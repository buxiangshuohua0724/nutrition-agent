#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
一次性数据修补脚本：把 db/h2/data.sql 的分类 slug 与 food.code 对齐到前端素材契约。

背景
----
前端素材与 CSS 变量早就统一成了「复数分类 slug」（vegetables/fruits/meat/aquatic/...），
而后端种子数据写成了单数（vegetable/fruit/egg/grain/nut/legume/other/seafood），
导致前端按 slug 取分类图与底色时命中不到，食品插画也因为 code 前缀不一致而丢图。

本脚本做四件事，且**只做这四件事**（其余内容一字不改，保留种子数据里已有的营养数值）：
  1. food_category.slug 与 icon 单数 → 复数；seafood → aquatic
  2. food.code 前缀 seafood- → aquatic-（salmon / clear-bass）
  3. 清理泄漏进 content 字段的内部注释残留（"-- 一、用户与权限--" 这类）
  4. 追加 3 种「素材有、数据没有」的食品（葡萄 / 橙子 / 牛肉）及其形态与营养值，
     并把 food/sys_* 等表的自增 RESTART 值改成与实际最大 id 一致

补丁以「精确字符串替换 + 断言」方式执行：任何一处没匹配上就直接失败退出，
避免静默改错。执行前会写 .bak 备份。

用法：python nutrition-agent/scripts/patch_seed_contract.py
"""
import io
import os
import re
import shutil
import sys

try:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
except Exception:
    pass

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TARGET = os.path.join(ROOT, 'backend', 'src', 'main', 'resources', 'db', 'h2', 'data.sql')

# ---------------------------------------------------------------- 1. 简单替换
# (旧串, 新串, 期望出现次数)
REPLACEMENTS = [
    # food_category.slug / icon 单数 → 复数
    ("NULL, 'vegetable', '蔬菜', 'vegetable'", "NULL, 'vegetables', '蔬菜', 'vegetables'", 1),
    ("NULL, 'fruit', '水果', 'fruit'",         "NULL, 'fruits', '水果', 'fruits'",         1),
    ("NULL, 'seafood', '水产', 'seafood'",     "NULL, 'aquatic', '水产', 'aquatic'",       1),
    ("NULL, 'egg', '蛋类', 'egg'",             "NULL, 'eggs', '蛋类', 'eggs'",             1),
    ("NULL, 'grain', '谷物', 'grain'",         "NULL, 'grains', '谷物', 'grains'",         1),
    ("NULL, 'nut', '坚果', 'nut'",             "NULL, 'nuts', '坚果', 'nuts'",             1),
    ("NULL, 'legume', '豆类', 'legume'",       "NULL, 'legumes', '豆类', 'legumes'",       1),
    ("NULL, 'other', '其他', 'other'",         "NULL, 'others', '其他', 'others'",         1),
    # food.code 前缀
    ("'seafood-salmon'",                       "'aquatic-salmon'",                          1),
    ("'seafood-clear-bass'",                   "'aquatic-bass'",                            1),
]

# 泄漏进正文的内部注释残留 → 直接删掉
#   这两类片段原本是种子文件里的注释，被误并进了 knowledge_chunk.content 字符串。
#   注意 '--' 与注释文字之间可能没有空格（实测两种写法都有），故用 \s* 兼容。
LEAK_PATTERNS = [
    re.compile(r'--\s*一、用户与权限\s*--'),
    re.compile(r'--\s*三个演示账号的 password_hash.*?严禁上生产）'),
]

# ---------------------------------------------------------------- 2. 追加的三种食品
NEW_FOODS = [
    # id, code, category_id, name, en_name, intro, usage, origin, storage, suitable, cautions, keywords
    (21, 'fruit-grape', 2, '葡萄', 'Grape',
     '葡萄成串生长，果皮颜色多样，果肉多汁，可鲜食也可制干。',
     '生食、榨汁、制干、酿酒', '我国新疆、河北等地为主要产区', '冷藏',
     '一般人群均可食用', '表面易残留农药，建议充分清洗后再食用；含糖量较高，宜适量食用',
     '葡萄 提子 grape putao'),
    (22, 'fruit-orange', 2, '橙子', 'Orange',
     '橙子是柑橘类水果，果皮较厚，果肉酸甜多汁。',
     '生食、榨汁、制作果酱', '我国南方为主要产区', '阴凉处或冷藏',
     '一般人群均可食用', '果汁的膳食纤维含量低于果肉，建议优先食用完整果肉',
     '橙子 甜橙 orange chengzi'),
    (23, 'meat-beef', 3, '牛肉', 'Beef',
     '牛肉是常见的红肉，肌肉纤维较粗，蛋白质含量较高。',
     '炖煮、煎制、炒食、卤制', '全国各地均有饲养与供应', '冷藏或冷冻',
     '一般人群均可食用', '需彻底加热后食用；避免生熟交叉污染',
     '牛肉 beef niurou'),
]

# food_id, variant_id, state, method, suffix, ratio, description, is_default
NEW_VARIANTS = [
    (21, 34, 'RAW', None, '（生）', None, '新鲜葡萄', 1),
    (22, 35, 'RAW', None, '（生）', None, '新鲜橙子', 1),
    (23, 36, 'RAW', None, '（生）', None, '生牛肉，需充分加热后食用', 1),
    (23, 37, 'COOKED', '煮', '（炖）', None, '炖煮后的牛肉', 0),
]

# 每 100g 参考：nutrient_id → (value_status, amount)；MISSING 一律 NULL
NEW_NUTRITION = {
    34: [  # 葡萄（生）
        (1, 'PRESENT', '45'), (2, 'PRESENT', '0.5'), (3, 'PRESENT', '0.2'), (4, 'PRESENT', '10.3'),
        (5, 'PRESENT', '0.4'), (6, 'PRESENT', '1.3'), (7, 'PRESENT', '5'), (8, 'PRESENT', '0.4'),
        (9, 'PRESENT', '104'), (11, 'PRESENT', '25'), (12, 'MISSING', None), (13, 'ZERO', '0'),
        (14, 'MISSING', None),
    ],
    35: [  # 橙子（生）
        (1, 'PRESENT', '48'), (2, 'PRESENT', '0.8'), (3, 'PRESENT', '0.2'), (4, 'PRESENT', '11.1'),
        (5, 'PRESENT', '0.6'), (6, 'PRESENT', '1.2'), (7, 'PRESENT', '20'), (8, 'PRESENT', '0.4'),
        (9, 'PRESENT', '159'), (11, 'PRESENT', '33'), (12, 'MISSING', None), (13, 'ZERO', '0'),
    ],
    36: [  # 牛肉（生）
        (1, 'PRESENT', '125'), (2, 'PRESENT', '19.9'), (3, 'PRESENT', '4.2'), (4, 'PRESENT', '2.0'),
        (5, 'ZERO', '0'), (6, 'PRESENT', '53.4'), (7, 'PRESENT', '9'), (8, 'PRESENT', '3.3'),
        (9, 'PRESENT', '284'), (11, 'ZERO', '0'), (12, 'MISSING', None), (13, 'PRESENT', '84'),
        (14, 'MISSING', None),
    ],
    37: [  # 牛肉（炖）
        (1, 'PRESENT', '168'), (2, 'PRESENT', '26.4'), (3, 'PRESENT', '5.6'), (4, 'PRESENT', '2.6'),
        (5, 'ZERO', '0'), (6, 'PRESENT', '78'), (7, 'PRESENT', '12'), (8, 'PRESENT', '4.1'),
        (9, 'PRESENT', '268'), (12, 'MISSING', None), (13, 'PRESENT', '108'),
    ],
}

SEED_TS = "'2025-01-01 00:00:00'"


def fail(msg):
    print('✗ ' + msg)
    sys.exit(1)


def food_row(f):
    fid, code, cat, name, en, intro, usage, origin, storage, suitable, cautions, kw = f
    return (
        "  (%d, '%s', %d, '%s', '%s', NULL,\n"
        "   '%s',\n"
        "   '%s', '%s', '%s', '%s', '%s',\n"
        "   '%s', 'APPROVED', 'PUBLISHED', '2025-01-01 00:00:00', 1, 1, 1, '2025-01-01 00:00:00', '2025-01-01 00:00:00', '2025-01-01 00:00:00', NULL)"
        % (fid, code, cat, name, en, intro, usage, origin, storage, suitable, cautions, kw)
    )


def main():
    if not os.path.exists(TARGET):
        fail('找不到 %s' % TARGET)
    src = open(TARGET, 'r', encoding='utf-8').read()

    # ---- 简单替换，逐条断言行数
    for old, new, expect in REPLACEMENTS:
        got = src.count(old)
        if got != expect:
            fail('替换「%s」期望命中 %d 次，实际 %d 次 —— 文件内容与预期不符，已中止（未做任何写入）'
                 % (old, expect, got))
        src = src.replace(old, new)

    # ---- 清理注释残留
    cleaned = 0
    for pat in LEAK_PATTERNS:
        src, n = pat.subn('', src)
        cleaned += n

    # ---- 追加食品（按整行定位：核桃所在的 food 行就是插入块的结尾）
    if "'fruit-grape'" in src or "'meat-beef'" in src:
        fail('文件里已经存在 fruit-grape / meat-beef，说明补丁已执行过，中止以免重复插入')
    rows = ',\n'.join(food_row(f) for f in NEW_FOODS)
    lines = src.split('\n')
    idx = [k for k, ln in enumerate(lines) if 'nut-walnut' in ln and ln.rstrip().endswith(');')]
    if len(idx) != 1:
        fail('定位 food 插入块结尾行（nut-walnut 且以 ");" 结束）得到 %d 个候选，中止' % len(idx))
    k = idx[0]
    lines[k] = lines[k].rstrip()[:-2] + '),\n' + rows + ';'
    src = '\n'.join(lines)

    # ---- 追加形态（同样按整行定位：food_id=20 的形态就是插入块的结尾）
    vlines = []
    for _fid, vid, state, method, suffix, ratio, desc, is_default in NEW_VARIANTS:
        m = 'NULL' if method is None else "'%s'" % method
        r = 'NULL' if ratio is None else ('%.4f' % ratio)
        vlines.append("  (%d, %d, '%s', %s, '%s', %s, '%s', %d, 'APPROVED', 'PUBLISHED', %s, %s, NULL)"
                      % (vid, _fid, state, m, suffix, r, desc, is_default, SEED_TS, SEED_TS))
    lines = src.split('\n')
    vidx = [k for k, ln in enumerate(lines) if re.match(r"\s*\(33,\s*20,\s*'COOKED'", ln)]
    if len(vidx) != 1:
        fail('定位 food_state_variant 插入块结尾行（(33, 20, COOKED...)）得到 %d 个候选，中止' % len(vidx))
    k = vidx[0]
    if not lines[k].rstrip().endswith(');'):
        fail('形态块结尾行不是以 ");" 结束：%s' % lines[k][-50:])
    lines[k] = lines[k].rstrip()[:-2] + '),\n' + ',\n'.join(vlines) + ';'
    src = '\n'.join(lines)

    # ---- 追加营养值
    nv_rows = []
    nvid = 463
    for vid in sorted(NEW_NUTRITION):
        for nutrient_id, status, amount in NEW_NUTRITION[vid]:
            amt = 'NULL' if amount is None else amount
            nv_rows.append(
                "  (%d, %d, %d, 100, 'g', '每100g可食部', '%s', %s, 1, NULL, 'APPROVED', 1, 1, 1, %s, %s, %s, NULL)"
                % (nvid, vid, nutrient_id, status, amt, SEED_TS, SEED_TS, SEED_TS))
            nvid += 1
    nv_anchor = "ALTER TABLE nutrition_value ALTER COLUMN id RESTART WITH 463;"
    if src.count(nv_anchor) != 1:
        fail('找不到 nutrition_value 自增重置行，中止')
    nv_block = (
        "\n-- ---------------------------------------------------------------------------\n"
        "-- 补充：葡萄 / 橙子 / 牛肉 的营养数值（同为主演示来源 DEMO-SEED）\n"
        "-- ---------------------------------------------------------------------------\n"
        "INSERT INTO nutrition_value (id, variant_id, nutrient_id, reference_amount, reference_unit, reference_note, value_status, amount, source_id, source_detail, review_status, created_by, updated_by, reviewed_by, reviewed_at, created_at, updated_at, deleted_at) VALUES\n"
        + ',\n'.join(nv_rows) + ";\n")
    # 插到「十一、自增序列重置」注释块之前
    marker = "-- ---------------------------------------------------------------------------\n-- 十一、自增序列重置"
    if src.count(marker) != 1:
        fail('找不到自增序列重置的注释标题，中止')
    src = src.replace(marker, nv_block + '\n' + marker)

    # ---- 修正 RESTART 值
    restart_fixes = [
        ("ALTER TABLE food ALTER COLUMN id RESTART WITH 21;",
         "ALTER TABLE food ALTER COLUMN id RESTART WITH 24;"),
        ("ALTER TABLE food_state_variant ALTER COLUMN id RESTART WITH 34;",
         "ALTER TABLE food_state_variant ALTER COLUMN id RESTART WITH 38;"),
        ("ALTER TABLE nutrition_value ALTER COLUMN id RESTART WITH 463;",
         "ALTER TABLE nutrition_value ALTER COLUMN id RESTART WITH %d;" % nvid),
    ]
    for old, new in restart_fixes:
        if src.count(old) != 1:
            fail('找不到自增重置行「%s」，中止' % old)
        src = src.replace(old, new)

    # ---- 备份后写入
    backup = TARGET + '.bak'
    if not os.path.exists(backup):
        shutil.copy2(TARGET, backup)
    with open(TARGET, 'w', encoding='utf-8', newline='\n') as fh:
        fh.write(src)

    print('✓ 补丁已写入 %s' % TARGET)
    print('  · 清理注释残留 %d 处' % cleaned)
    print('  · 追加食品 %d 种、形态 %d 条、营养值 %d 条'
          % (len(NEW_FOODS), len(NEW_VARIANTS), len(nv_rows)))
    print('  · food 自增重置为 24，food_state_variant 为 38，nutrition_value 为 %d' % nvid)
    print('  · 备份：%s' % backup)


if __name__ == '__main__':
    main()
