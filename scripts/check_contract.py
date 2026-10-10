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
