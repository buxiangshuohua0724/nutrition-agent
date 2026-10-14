#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
契约一致性检查（CI 可复用的独立脚本）

检查三份「同一组命名约定」的落盘文件是否一致：
  A. frontend/src/assets/categories/<slug>.svg        分类素材文件名
  B. frontend/src/styles/fallback-tokens.css          --na-color-cat-<slug>-bg 变量名
  C. backend/src/main/resources/db/h2/data.sql        food_category.slug
  D. frontend/src/assets/foods/<code>.svg             食品素材文件名
  E. backend/src/main/resources/db/h2/data.sql        food.code

外加：
  F. docs/db/schema.sql 与 db/h2/schema.sql 的表/列结构比对（H2 侧允许
     JSON→VARCHAR、去 ENGINE/CHARSET 等已知差异，但**表名与列名必须完全一致**）
  G. 三态数据自检（MISSING 必须 amount IS NULL，ZERO 必须 amount = 0）
  H. docs/db/data.sql（MySQL 部署种子）是否与 H2 种子逐字同步
  I. docs/db/nutrition-usda.sql 是否与 docs/db/usda-selection.json 同步

用法： python nutrition-agent/scripts/check_contract.py
退出码： 0 = 全部通过；1 = 有不一致
"""
import io
import os
import re
import sys

# Windows 控制台默认 GBK，直接 print 非 GBK 字符（如 ✓ ✗）会抛 UnicodeEncodeError。
# 这里强制以 UTF-8 输出，并对无法编码的字符退化为 '?'，保证脚本永远能跑完并给出退出码。
try:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
except Exception:
    pass

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

CATEGORY_DIR = os.path.join(ROOT, 'frontend', 'src', 'assets', 'categories')
FOOD_DIR = os.path.join(ROOT, 'frontend', 'src', 'assets', 'foods')
FALLBACK_CSS = os.path.join(ROOT, 'frontend', 'src', 'styles', 'fallback-tokens.css')
TOKENS_CSS = os.path.join(ROOT, 'frontend', 'src', 'design', 'tokens.css')
H2_DATA = os.path.join(ROOT, 'backend', 'src', 'main', 'resources', 'db', 'h2', 'data.sql')
MYSQL_SCHEMA = os.path.join(ROOT, 'docs', 'db', 'schema.sql')
H2_SCHEMA = os.path.join(ROOT, 'backend', 'src', 'main', 'resources', 'db', 'h2', 'schema.sql')
DEMO_DATASET = os.path.join(ROOT, 'frontend', 'src', 'api', 'demo-dataset.js')

problems = []
notes = []


def read(path):
    with open(path, 'r', encoding='utf-8', errors='replace') as fh:
        return fh.read()


def svg_stems(directory):
    if not os.path.isdir(directory):
        return set()
    return {n[:-4] for n in os.listdir(directory) if n.lower().endswith('.svg')}


def insert_blocks(sql, table):
    """取全部 INSERT INTO <table> (...) VALUES ...; 块（MATCH-ALL）"""
    return re.findall(
        r'INSERT\s+INTO\s+`?' + table + r'`?\s*\((.*?)\)\s*VALUES(.*?);',
        sql, re.S | re.I)


def insert_rows(sql, table):
    """把全部 INSERT ... VALUES 块合并成 [(列名列表, [值字符串列表]), ...]

    同一张表可能分多个 INSERT 块写入（例如基础种子 + 补充数据），
    这里必须全部收集，否则校验会漏掉后面的行。
    """
    blocks = insert_blocks(sql, table)
    if not blocks:
        return None, []
    rows = []
    cols = None
    for head, body in blocks:
        c = [x.strip().strip('`') for x in head.split(',')]
        if cols is None:
            cols = c
        elif c != cols:
            problems.append('表 %s 的多个 INSERT 块列清单不一致：%s vs %s' % (table, cols, c))
        for raw in split_tuples(body):
            rows.append([v.strip() for v in split_values(raw)])
    return cols, rows


def split_tuples(body):
    """按顶层括号切出每个 (...) 行，返回去掉外层括号的内容"""
    out, depth, cur, in_str = [], 0, '', False
    i = 0
    while i < len(body):
        ch = body[i]
        if in_str:
            if ch == "'":
                if i + 1 < len(body) and body[i + 1] == "'":
                    cur += "''"
                    i += 2
                    continue
                in_str = False
            cur += ch
        else:
            if ch == "'":
                in_str = True
                cur += ch
            elif ch == '(':
                depth += 1
                if depth == 1:
                    cur = ''
                else:
                    cur += ch
            elif ch == ')':
                depth -= 1
                if depth == 0:
                    out.append(cur)
                else:
                    cur += ch
            elif depth > 0:
                cur += ch
        i += 1
    return out


def split_values(row):
    """按顶层逗号切一行的各列值"""
    out, depth, cur, in_str = [], 0, '', False
    i = 0
    while i < len(row):
        ch = row[i]
        if in_str:
            if ch == "'":
                if i + 1 < len(row) and row[i + 1] == "'":
                    cur += "''"
                    i += 2
                    continue
                in_str = False
            cur += ch
        else:
            if ch == "'":
                in_str = True
                cur += ch
            elif ch == '(':
                depth += 1
                cur += ch
            elif ch == ')':
                depth -= 1
                cur += ch
            elif ch == ',' and depth == 0:
                out.append(cur)
                cur = ''
            else:
                cur += ch
        i += 1
    if cur.strip():
        out.append(cur)
    return out


def unquote(v):
    v = v.strip()
    if v.upper() == 'NULL':
        return None
    if len(v) >= 2 and v[0] == "'" and v[-1] == "'":
        return v[1:-1].replace("''", "'")
    return v


# ---------------------------------------------------------------- 1. 分类 slug
cat_assets = svg_stems(CATEGORY_DIR)
css_slugs = set(re.findall(r'--na-color-cat-([a-z0-9-]+)-(?:bg|fg|border)', read(FALLBACK_CSS)))
css_slugs |= set(re.findall(r'--na-color-cat-([a-z0-9-]+)-(?:bg|fg|border)', read(TOKENS_CSS)))
data_sql = read(H2_DATA)
_, cat_rows = insert_rows(data_sql, 'food_category')
if cat_rows is None:
    problems.append('data.sql 中找不到 food_category 的 INSERT')
    db_slugs = set()
else:
    db_slugs = {unquote(r[2]) for r in cat_rows}

notes.append('分类素材文件: %s' % ', '.join(sorted(cat_assets)))
notes.append('CSS 变量 slug: %s' % ', '.join(sorted(css_slugs)))
notes.append('DB 分类 slug : %s' % ', '.join(sorted(db_slugs)))

if cat_assets != css_slugs:
    problems.append('分类素材文件名与 CSS 变量名不一致：只在素材 %s ｜ 只在 CSS %s'
                    % (sorted(cat_assets - css_slugs), sorted(css_slugs - cat_assets)))
if cat_assets != db_slugs:
    problems.append('分类素材文件名与 data.sql 的 food_category.slug 不一致：只在素材 %s ｜ 只在 DB %s'
                    % (sorted(cat_assets - db_slugs), sorted(db_slugs - cat_assets)))

# ---------------------------------------------------------------- 2. 食品 code
food_assets = svg_stems(FOOD_DIR)
_, food_rows = insert_rows(data_sql, 'food')
if food_rows is None:
    problems.append('data.sql 中找不到 food 的 INSERT')
    db_codes = set()
else:
    db_codes = {unquote(r[1]) for r in food_rows}

missing_asset = sorted(db_codes - food_assets)
extra_asset = sorted(food_assets - db_codes)
notes.append('食品素材数: %d ｜ DB food.code 数: %d' % (len(food_assets), len(db_codes)))
if missing_asset:
    problems.append('以下 food.code 在 assets/foods 中没有对应 SVG（前端会丢图）：%s' % missing_asset)
if extra_asset:
    notes.append('以下素材没有被任何 food.code 引用（可接受，属预留）：%s' % extra_asset)

# ------------------------------------------------- 3. food.category_id 指向合法 slug
if cat_rows is not None and food_rows is not None:
    id2slug = {unquote(r[0]): unquote(r[2]) for r in cat_rows}
    bad_cat = []
    for r in food_rows:
        cid = unquote(r[2])
        if cid not in id2slug:
            bad_cat.append((unquote(r[1]), cid))
    if bad_cat:
        problems.append('food.category_id 指向不存在的分类：%s' % bad_cat)

# ---------------------------------------------------------------- 4. 三态自检
if not insert_blocks(data_sql, 'nutrition_value'):
    problems.append('data.sql 中找不到 nutrition_value 的 INSERT')
else:
    cols, rows = insert_rows(data_sql, 'nutrition_value')
    idx_status = cols.index('value_status')
    idx_amount = cols.index('amount')
    statuses = {}
    bad = []
    for r in rows:
        st = unquote(r[idx_status])
        am = unquote(r[idx_amount])
        statuses[st] = statuses.get(st, 0) + 1
        if st == 'MISSING' and am is not None:
            bad.append(('MISSING 但有值', r[0]))
        if st == 'ZERO' and (am is None or float(am) != 0):
            bad.append(('ZERO 但值非 0', r[0]))
        if st == 'PRESENT' and am is None:
            bad.append(('PRESENT 但值为 NULL', r[0]))
    notes.append('nutrition_value 行数 %d，三态分布 %s' % (len(rows), statuses))
    if bad:
        problems.append('三态与 amount 不一致的行：%s' % bad[:10])
    for want, desc in (('PRESENT', '有值'), ('ZERO', '真实零'), ('MISSING', '缺失')):
        if want not in statuses:
            problems.append('种子数据缺少 %s（%s）样本，无法验证三态渲染' % (want, desc))

# ---------------------------------------------------------------- 5. schema 对比
def mysql_tables(sql):
    out = {}
    for m in re.finditer(r'CREATE TABLE `(\w+)` \((.*?)\n\) ENGINE', sql, re.S):
        out[m.group(1)] = m.group(2)
    return out


def mysql_columns(body):
    cols = {}
    for line in body.split('\n'):
        m = re.match(r'\s*`(\w+)`\s+([A-Za-z][A-Za-z0-9()]*)', line)
        if m and m.group(1).upper() not in ('PRIMARY', 'UNIQUE', 'KEY', 'CONSTRAINT', 'INDEX'):
            cols[m.group(1)] = m.group(2).upper()
    return cols


def h2_tables(sql):
    out = {}
    for m in re.finditer(r'CREATE TABLE (?:IF NOT EXISTS )?`?(\w+)`? \((.*?)\n\);', sql, re.S):
        out[m.group(1)] = m.group(2)
    if not out:
        for m in re.finditer(r'CREATE TABLE (?:IF NOT EXISTS )?`?(\w+)`? \((.*?)\);', sql, re.S):
            out[m.group(1)] = m.group(2)
    return out


def h2_columns(body):
    cols = {}
    for line in body.split('\n'):
        m = re.match(r'\s*`?(\w+)`?\s+([A-Za-z][A-Za-z0-9()]*)', line)
        if m and m.group(1).upper() not in ('PRIMARY', 'UNIQUE', 'KEY', 'CONSTRAINT', 'INDEX', 'FOREIGN'):
            cols[m.group(1)] = m.group(2).upper()
    return cols


my = mysql_tables(read(MYSQL_SCHEMA))
h2 = h2_tables(read(H2_SCHEMA))
if set(my) != set(h2):
    problems.append('两份 schema 的表集合不一致：只在 MySQL %s ｜ 只在 H2 %s'
                    % (sorted(set(my) - set(h2)), sorted(set(h2) - set(my))))
else:
    notes.append('两份 schema 表数一致：%d 张' % len(my))
    for t in sorted(my):
        mc, hc = mysql_columns(my[t]), h2_columns(h2[t])
        only_my = sorted(set(mc) - set(hc))
        only_h2 = sorted(set(hc) - set(mc))
        if only_my or only_h2:
            problems.append('表 %s 列不一致：只在 MySQL %s ｜ 只在 H2 %s' % (t, only_my, only_h2))

# ------------------------------------------------- 6. 演示数据集与种子数据一致
# 演示数据由 scripts/gen_demo_dataset.py 从 data.sql 生成。它一旦过期，
# 界面就会开始展示与数据库不符的食品/营养数据（曾经真的发生过），
# 因此这里把「生成物的自检统计」与「data.sql 的实测统计」逐项对齐。
if not os.path.isfile(DEMO_DATASET):
    problems.append('frontend/src/api/demo-dataset.js 不存在，请运行 scripts/gen_demo_dataset.py')
else:
    demo_js = read(DEMO_DATASET)
    stats_match = re.search(r'DEMO_DATASET_STATS\s*=\s*\{(.*?)\}', demo_js, re.S)
    if not stats_match:
        problems.append('demo-dataset.js 里找不到 DEMO_DATASET_STATS')
    else:
        demo_stats = {
            k: int(v)
            for k, v in re.findall(r'(\w+)\s*:\s*(\d+)', stats_match.group(1))
        }
        nv_cols, nv_rows = insert_rows(data_sql, 'nutrition_value')
        _, variant_rows = insert_rows(data_sql, 'food_state_variant')
        _, nutrient_rows = insert_rows(data_sql, 'nutrient')
        real_status = {}
        if nv_cols and 'value_status' in nv_cols:
            idx_st = nv_cols.index('value_status')
            for r in nv_rows or []:
                st = unquote(r[idx_st])
                real_status[st] = real_status.get(st, 0) + 1
        # 口径说明（不要改回"逐字段必须相等"）：
        #   data.sql 的 nutrition_value 是**不完整**的：种子实测 511 行，
        #   而 37 个形态 × 14 个启用营养素应为 518 行 —— 有 7 行整行不存在。
        #   生成器 scripts/gen_demo_dataset.py 会把这些整行缺失补成**显式的** MISSING
        #   （amount=null、displayText=暂无数据，来源沿用该形态已有行的 source_id），
        #   因为"行不存在"和"项暂无数据"在界面上必须表达同一种事实。
        #   于是：
        #     * present / zero 是**事实计数**，必须逐位相等（多一个少一个都是脱节）；
        #     * demo 的 missing 允许更大，但必须恰好等于
        #       「data.sql 的 missing + (形态数 × 启用营养素数 − data.sql 实际行数)」，
        #       即多出来的缺口只能来自"整行不存在的行被补成 MISSING"这一种原因。
        #   这样它依然能抓住生成物过期、凭空多出/少掉缺失项、或悄悄补齐了数值。
        variant_count = len(variant_rows or [])
        nutrient_count = len(nutrient_rows or [])
        real_row_count = len(nv_rows or [])
        absent_rows = variant_count * nutrient_count - real_row_count  # 整行缺失的行数
        real_missing = real_status.get('MISSING', 0)
        min_missing = real_missing + absent_rows
        expect = {
            'foodCount': len(food_rows or []),
            'variantCount': variant_count,
            'presentCount': real_status.get('PRESENT', 0),
            'zeroCount': real_status.get('ZERO', 0),
            # missingCount 不要求相等，真正的判定在下面（>= 且差值恰好为 absent_rows）
            'missingCount': demo_stats.get('missingCount'),
        }
        notes.append(
            '演示数据集统计 %s（对照 data.sql %s；data.sql 实测 %d 行，'
            '形态×启用营养素 = %d×%d，整行缺失 %d 行 → 补齐后 missingCount 应为 %d）'
            % (demo_stats, expect, real_row_count, variant_count, nutrient_count,
               absent_rows, min_missing))
        drift = {k: (demo_stats.get(k), v) for k, v in expect.items() if k != 'missingCount' and demo_stats.get(k) != v}
        if drift:
            problems.append(
                'demo-dataset.js 已与 data.sql 脱节（字段: 生成值 vs 实际值 = %s），'
                '请重新运行 scripts/gen_demo_dataset.py' % drift)
        demo_missing = demo_stats.get('missingCount')
        if demo_missing is None:
            problems.append('demo-dataset.js 的 DEMO_DATASET_STATS 缺少 missingCount')
        elif demo_missing < min_missing:
            problems.append(
                'demo-dataset.js 的 missingCount=%s 小于应有缺口 %d（data.sql 实测 MISSING %d + '
                '整行缺失 %d）—— 缺失项被吞掉了，请重新运行 scripts/gen_demo_dataset.py'
                % (demo_missing, min_missing, real_missing, absent_rows))
        elif demo_missing > min_missing:
            problems.append(
                'demo-dataset.js 的 missingCount=%s 大于应有缺口 %d（data.sql 实测 MISSING %d + '
                '整行缺失 %d）—— 多出来的缺失无法用"整行补 MISSING"解释，请检查生成器'
                % (demo_missing, min_missing, real_missing, absent_rows))

# ------------------------------------------------- 7. 知识引用 refId 守护
# 规则（见 docs/api-contract.md 与 docs/agent-design.md）：
#     KDOC_<docId 左补零至 4 位>#chunk<chunkId>
# 其中 chunkId 是 knowledge_chunk.id —— **全局自增主键**，不是文档内的
# chunk_index。这个格式极易写错，而写错了在界面上看不出来（它只是一个查不到
# 的字符串），真实事故已经发生过两次：
#   · KDOC_0007#chunk2  —— 种子库只有 6 篇文档、18 个分块，该引用查无此条；
#   · KDOC_0005#chunk1  —— 误把 chunk_index 当成 chunk id：chunk.id=1 其实属于
#                          doc_id=1，而文档 5 内 chunk_index=1 的分块是 id=14。
# 所以这里把每个 refId 拿回 data.sql 核对两件事：①分块真实存在；②该分块的
# doc_id 与 refId 里的 docId 一致 —— 后者是「取错 chunk_index」唯一能抓住的判据。
DEMO_DATA = os.path.join(ROOT, 'frontend', 'src', 'api', 'demo-data.js')
REFID_RE = re.compile(r"""refId\s*:\s*['"](KDOC_[^'"]*)['"]""")
KDOC_RE = re.compile(r'^KDOC_(\d{4})#chunk(\d+)$')
# 同一条规则的另一处入口：`DEMO_KNOWLEDGE` 用分开的 docId / chunkId 两个字段描述引用
DOCID_RE = re.compile(r'docId\s*:\s*(\d+)')
CHUNKID_NEAR_RE = re.compile(r'chunkId\s*:\s*(\d+)')


def as_int(v):
    """把 SQL 里的数字字面量归一成不带前导零的字符串，便于跨写法比较"""
    try:
        return str(int(str(v).strip()))
    except (TypeError, ValueError):
        return None


def kdoc_span(raw):
    """把一个 KDOC_ 引用解析成 (docId, chunkId) 的归一字符串；不合法返回 (None, None)"""
    m = KDOC_RE.match(raw)
    if not m:
        return None, None
    return as_int(m.group(1)), as_int(m.group(2))


def check_kdoc(where, raw, want_doc, chunk_id, chunk_by_id, chunk_id_by_doc_index):
    """核对一个「引用 → 知识分块」的指向关系，返回 (True, note) 或 (False, problem)。

    只负责两条规则，且**假定调用方已确认 chunk_id 存在于 chunk_by_id**：
      ①分块真实存在（由调用方先查，因为报错文本要用到分块总数）；
      ②该分块的 doc_id 必须等于引用里的 docId —— 这是「把文档内 chunk_index 当成
        全局 chunk id」唯一能抓住的判据。
    问题文本集中在这里，让 refId 字面量与 docId/chunkId 字段对两条入口共用一套措辞；
    调用方已判不存在的分支不再重复判一次。
    """
    real_doc, _real_idx = chunk_by_id[chunk_id]
    if real_doc != want_doc:
        right_cid = chunk_id_by_doc_index.get((want_doc, chunk_id))
        if right_cid is not None:
            return False, (
                "%s 的引用 '%s' 取的是 chunk_index：knowledge_chunk.id=%s 属于 doc_id=%s，与引用里的 "
                "docId=%s 不一致（文档 %s 内 chunk_index=%s 的分块是 id=%s，应为 'KDOC_%04d#chunk%s'）"
                % (where, raw, chunk_id, real_doc, want_doc, want_doc, chunk_id,
                   right_cid, int(want_doc), right_cid))
        return False, (
            "%s 的引用 '%s' 的 docId 与分块对不上：knowledge_chunk.id=%s 属于 doc_id=%s，"
            "而引用里写的是 docId=%s"
            % (where, raw, chunk_id, real_doc, want_doc))
    return True, ("%s 的引用 '%s' 一致" % (where, raw))


def kdoc_missing_problem(where, raw, chunk_id, chunk_by_id, chunk_row_count):
    """分块不存在时的报错文本（两条入口共用）"""
    return (
        "%s 的引用 '%s' 查无此分块：knowledge_chunk 中没有 id=%s 的行"
        "（data.sql 实测 %d 个分块，id 范围 %d~%d）"
        % (where, raw, chunk_id, chunk_row_count,
           min(int(k) for k in chunk_by_id), max(int(k) for k in chunk_by_id)))


# 记录两条入口各自实际核对到几条引用，用于输出「覆盖了哪些规则」的提示
kdoc_covered = set()


if not os.path.isfile(DEMO_DATA):
    problems.append('frontend/src/api/demo-data.js 不存在，无法校验知识引用 refId')
else:
    kc_cols, kc_rows = insert_rows(data_sql, 'knowledge_chunk')
    kd_cols, kd_rows = insert_rows(data_sql, 'knowledge_doc')
    if kc_cols is None:
        problems.append('data.sql 中找不到 knowledge_chunk 的 INSERT，无法校验知识引用 refId')
    else:
        ci_id = kc_cols.index('id')
        ci_doc = kc_cols.index('doc_id')
        ci_cidx = kc_cols.index('chunk_index')
        chunk_by_id = {}             # chunk.id -> (doc_id, chunk_index)
        chunk_id_by_doc_index = {}   # (doc_id, chunk_index) -> chunk.id
        for r in kc_rows:
            c_id = as_int(unquote(r[ci_id]))
            c_doc = as_int(unquote(r[ci_doc]))
            c_idx = as_int(unquote(r[ci_cidx]))
            chunk_by_id[c_id] = (c_doc, c_idx)
            chunk_id_by_doc_index[(c_doc, c_idx)] = c_id

        doc_meta = {}
        if kd_cols is not None:
            want = ('id', 'title', 'review_status', 'publish_status', 'valid_until', 'deleted_at')
            di = {n: kd_cols.index(n) for n in want if n in kd_cols}
            if 'id' in di:
                for r in kd_rows:
                    doc_meta[as_int(unquote(r[di['id']]))] = {
                        'title': unquote(r[di['title']]) if 'title' in di else '—',
                        'review': unquote(r[di['review_status']]) if 'review_status' in di else '—',
                        'publish': unquote(r[di['publish_status']]) if 'publish_status' in di else '—',
                        'valid_until': unquote(r[di['valid_until']]) if 'valid_until' in di else '—',
                        'deleted_at': unquote(r[di['deleted_at']]) if 'deleted_at' in di else '—',
                    }

        def line_of(text, offset):
            """把字符偏移折算成行号（报错里必须给出可点的行号）"""
            return text.count('\n', 0, offset) + 1

        demo_src = read(DEMO_DATA)
        refids = list(REFID_RE.finditer(demo_src))
        ok_refs = []
        for m in refids:
            raw = m.group(1)
            line_no = line_of(demo_src, m.start())
            want_doc, num = kdoc_span(raw)
            if want_doc is None:
                problems.append(
                    "demo-data.js:%d 的 refId '%s' 格式不合法：应为 "
                    "KDOC_<docId 左补零至 4 位>#chunk<chunkId>，例如 'KDOC_0005#chunk14'"
                    % (line_no, raw))
                continue
            if num not in chunk_by_id:
                problems.append(kdoc_missing_problem(
                    "demo-data.js:%d" % line_no, raw, num, chunk_by_id, len(kc_rows)))
                continue
            kdoc_covered.add('refId')
            good, msg = check_kdoc("demo-data.js:%d" % line_no, raw, want_doc, num,
                                   chunk_by_id, chunk_id_by_doc_index)
            if not good:
                problems.append(msg)
                continue
            ok_refs.append((raw, want_doc))

        # ---- ②对象字段对 `docId: N` / `chunkId: M`（DEMO_KNOWLEDGE）----
        # 与 refId 字面量是**同一个错误面**：这两个字段最终也会被拼成给用户看的引用，
        # 但错误的后果更重 —— 它们是"检索结果"，写错会展示一条声称检索到、实际不存在的记录。
        # 上一版守护只扫 refId 字面量，这一处正好从底下漏了过去。
        n_obj_checked = 0
        for m in DOCID_RE.finditer(demo_src):
            f_line = line_of(demo_src, m.start())
            seg = demo_src[m.end():m.end() + 240]
            cm = CHUNKID_NEAR_RE.search(seg)
            if not cm:
                problems.append(
                    "demo-data.js:%d 写了 docId=%s 但邻近 240 字符内没有 chunkId："
                    "两者必须成对出现，否则无法判断这条引用指向哪个分块"
                    % (f_line, m.group(1)))
                continue
            c_line = f_line + seg.count('\n', 0, cm.start())
            want_doc, num = as_int(m.group(1)), as_int(cm.group(1))
            where = "demo-data.js:%d/%d 的 docId=%s + chunkId=%s" % (f_line, c_line, want_doc, num)
            if num not in chunk_by_id:
                problems.append(kdoc_missing_problem(where, 'docId=%s chunkId=%s' % (want_doc, num),
                                                     num, chunk_by_id, len(kc_rows)))
                continue
            n_obj_checked += 1
            kdoc_covered.add('field')
            good, msg = check_kdoc(where, 'docId=%s chunkId=%s' % (want_doc, num), want_doc, num,
                                   chunk_by_id, chunk_id_by_doc_index)
            if not good:
                problems.append(msg)
                continue
            # 这一条是演示态下的"检索结果"，会直接展示给用户，所以顺带核对目标文档
            # 处于「已审核 + 已发布 + 未删除」——不通过时给出提示（不判失败：演示数据
            # 允许提前写好，真实可检索性由后端检索接口决定）。
            d = doc_meta.get(want_doc)
            if d is not None and (d['publish'] != 'PUBLISHED' or d['review'] != 'APPROVED'
                                  or d['deleted_at'] is not None):
                notes.append(
                    '演示知识记录状态提示: docId=%s「%s」review_status=%s publish_status=%s '
                    'deleted_at=%s —— 不处于「已审核且已发布」，这条演示"检索结果"在真实环境里'
                    '检索不到' % (want_doc, d['title'], d['review'], d['publish'], d['deleted_at']))

        # ④ 可检索性只作提示，不用 wall-clock 比较有效期：种子数据的 valid_until
        # 一旦到期，检查会毫无征兆地开始失败，那不是契约问题。
        if not kdoc_covered:
            notes.append('知识引用 refId: demo-data.js 中未出现 KDOC_ 形式的引用字面量，'
                         '也未出现 docId/chunkId 字段对')
        else:
            kinds = []
            if 'refId' in kdoc_covered:
                kinds.append('%d 条 refId 字面量' % len(refids))
            if 'field' in kdoc_covered:
                kinds.append('%d 组 docId/chunkId 字段对' % n_obj_checked)
            notes.append('知识引用 refId: %s 全部指向真实分块且 docId 一致（%s）'
                         % (' + '.join(kinds), ', '.join(r for r, _ in ok_refs) or '字段对形式'))
            for d_id in sorted({d for _, d in ok_refs}, key=lambda x: int(x)):
                d = doc_meta.get(d_id)
                if d is None:
                    continue
                if d['publish'] != 'PUBLISHED' or d['review'] != 'APPROVED' or d['deleted_at'] is not None:
                    notes.append(
                        '引用文档状态提示: refId 引用的 doc %s「%s」review_status=%s publish_status=%s '
                        'deleted_at=%s —— 不处于「已审核且已发布」，该分块可能不会被检索返回'
                        % (d_id, d['title'], d['review'], d['publish'], d['deleted_at']))

# ---- H. docs/db/data.sql（MySQL 部署种子）是否与 H2 种子同步 ----
# docs/db/data.sql 由 scripts/gen_mysql_seed.py 从 H2 那份机械生成，不是第二份手写数据。
# 两者漂移是真出过事的：MySQL 库里「有表无数据」时，注册会因
#     INSERT INTO sys_user_role (user_id, role_id)  →  外键 fk_ur_role 失败
# 而让 POST /auth/register 直接 500 —— 编译、单测、前端构建全都照常通过，
# 只有真的去调注册接口才暴露。所以这里把它也变成一条能被自动发现的契约。
MYSQL_DATA = os.path.join(ROOT, 'docs', 'db', 'data.sql')
try:
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import gen_mysql_seed

    expected_seed = gen_mysql_seed.build()
    if not os.path.isfile(MYSQL_DATA):
        problems.append('docs/db/data.sql 不存在：请运行 python scripts/gen_mysql_seed.py 生成')
    else:
        with open(MYSQL_DATA, 'r', encoding='utf-8') as fh:
            if fh.read() != expected_seed:
                problems.append(
                    'docs/db/data.sql 与 H2 种子（backend/src/main/resources/db/h2/data.sql）不同步：'
                    '请运行 python scripts/gen_mysql_seed.py 重新生成')
            else:
                notes.append('MySQL 部署种子（docs/db/data.sql）与 H2 种子逐字同步')
except SystemExit as exc:
    problems.append('无法从 H2 种子生成 MySQL 种子：%s' % exc)
except Exception as exc:  # noqa: BLE001 —— 检查脚本自身出错也必须给出可读信息而不是堆栈
    problems.append('校验 docs/db/data.sql 时出错：%r' % (exc,))

# ---- I. docs/db/nutrition-usda.sql 是否与选样档案同步 ----
# nutrition-usda.sql 由 scripts/gen_usda_nutrition.py 从 docs/db/usda-selection.json 渲染而来，
# 不是第二份手写数据。选样档案记录「本项目到底抄了 USDA 的哪一条记录」及其数值（约 46KB，可入库），
# 因此这条检查在**没有 200MB 原始数据集**的机器上也能跑。
# 手工改 SQL 而不改档案（或反之）会让"数值出自哪条来源记录"说不清，而 nutrition_value 的
# source_detail 正是靠它填的 —— 这正是 docs/data-source-policy.md 要求的可追溯率 100%。
USDA_SQL = os.path.join(ROOT, 'docs', 'db', 'nutrition-usda.sql')
USDA_SEL = os.path.join(ROOT, 'docs', 'db', 'usda-selection.json')
try:
    import json as _json  # noqa: PLC0415 —— 与生成器同目录，就地导入即可

    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import gen_usda_nutrition

    if not os.path.isfile(USDA_SEL):
        problems.append('docs/db/usda-selection.json 不存在：'
                        '请运行 python scripts/gen_usda_nutrition.py --cache <usda_cache.json>')
    elif not os.path.isfile(USDA_SQL):
        problems.append('docs/db/nutrition-usda.sql 不存在：'
                        '请运行 python scripts/gen_usda_nutrition.py')
    else:
        with open(USDA_SEL, 'r', encoding='utf-8') as fh:
            _sel = _json.load(fh)
        with open(USDA_SQL, 'r', encoding='utf-8') as fh:
            _actual = fh.read()
        if _actual != gen_usda_nutrition.render(_sel):
            problems.append('docs/db/nutrition-usda.sql 与 docs/db/usda-selection.json 不同步：'
                            '请运行 python scripts/gen_usda_nutrition.py')
        else:
            notes.append('USDA 营养补丁（docs/db/nutrition-usda.sql）与选样档案逐字同步：'
                         '%d 个形态 / %d 行'
                         % (len(_sel['picks']),
                            len(_sel['picks']) * len(gen_usda_nutrition.NUTRIENT_CODES)))
except Exception as exc:  # noqa: BLE001
    problems.append('校验 docs/db/nutrition-usda.sql 时出错：%r' % (exc,))

# ---------------------------------------------------------------- 输出
print('=' * 72)
print('契约一致性检查')
print('=' * 72)
for n in notes:
    print('  · ' + n)
print('-' * 72)
if problems:
    print('✗ 发现 %d 个问题：' % len(problems))
    for p in problems:
        print('  - ' + p)
    sys.exit(1)
print('✓ 全部通过')
sys.exit(0)
