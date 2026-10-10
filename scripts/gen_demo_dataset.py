#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""从后端种子数据生成前端演示数据集。

## 这个脚本解决什么问题

前端在 `import.meta.env.DEV` 且接口不可用时，会回退到 `src/api/` 里的内联演示数据。
如果这份演示数据是**手写的**，它就会和数据库里的种子数据各自漂移 —— 实际发生过：

  * 分类浏览页写"蔬菜 12 种"，点进 `/category/vegetables` 却是"该分类下还没有食品"；
  * 演示食品只有 10 条，而种子库有 23 条，好几个分类在演示态下永远是空的。

因此演示数据一律**从种子 SQL 生成**，唯一事实来源是
`backend/src/main/resources/db/h2/data.sql`，不再手写。

## 用法

    python scripts/gen_demo_dataset.py

输出：`frontend/src/api/demo-dataset.js`

## 重要约定

  * **不编造任何数值**：脚本只做搬运，`nutrition_value` 里是什么就写什么；
  * **三态原样保留**：`MISSING` 的 `amount` 必须是 `null`，绝不在生成期写成 0；
  * 生成结果只覆盖"事实数据"（分类、食品、形态、营养表）；
    由人撰写的演示文案（AI 回答、知识文档、用户资料等）仍放在 `demo-data.js` 里手工维护。
"""

import io
import json
import os
import re
import sys

if __name__ == "__main__":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SEED = os.path.join(ROOT, "backend", "src", "main", "resources", "db", "h2", "data.sql")
OUT = os.path.join(ROOT, "frontend", "src", "api", "demo-dataset.js")

# ---------------------------------------------------------------------------
# 一个够用的 INSERT 解析器
# ---------------------------------------------------------------------------

INSERT_RE = re.compile(r"INSERT INTO\s+(\w+)\s*\(([^)]*)\)\s*VALUES", re.IGNORECASE)


def split_tuples(body):
    """把 VALUES 之后的内容切成一个个元组（不含外层括号）。

    必须按"引号感知"切分：中文字段值里会出现逗号、括号甚至换行。
    """
    tuples = []
    depth = 0
    buf = []
    in_str = False
    i = 0
    n = len(body)
    while i < n:
        ch = body[i]
        if in_str:
            if ch == "'":
                if i + 1 < n and body[i + 1] == "'":  # '' 转义
                    buf.append("''")
                    i += 2
                    continue
                in_str = False
            buf.append(ch)
        else:
            if ch == "'":
                in_str = True
                buf.append(ch)
            elif ch == "(":
                depth += 1
                if depth == 1:
                    buf = []
                else:
                    buf.append(ch)
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    tuples.append("".join(buf))
                    buf = []
                else:
                    buf.append(ch)
            elif depth >= 1:
                buf.append(ch)
        i += 1
    return tuples


def split_fields(tup):
    """把一条元组按顶层逗号切成字段。"""
    fields = []
    buf = []
    depth = 0
    in_str = False
    i = 0
    n = len(tup)
    while i < n:
        ch = tup[i]
        if in_str:
            if ch == "'":
                if i + 1 < n and tup[i + 1] == "'":
                    buf.append("''")
                    i += 2
                    continue
                in_str = False
            buf.append(ch)
        else:
            if ch == "'":
                in_str = True
                buf.append(ch)
            elif ch == "(":
                depth += 1
                buf.append(ch)
            elif ch == ")":
                depth -= 1
                buf.append(ch)
            elif ch == "," and depth == 0:
                fields.append("".join(buf).strip())
                buf = []
            else:
                buf.append(ch)
        i += 1
    fields.append("".join(buf).strip())
    return fields


def unquote(tok):
    """SQL 字面量 → Python 值。"""
    if tok.upper() == "NULL":
        return None
    if tok.startswith("'") and tok.endswith("'") and len(tok) >= 2:
        return tok[1:-1].replace("''", "'")
    try:
        if "." in tok:
            return float(tok)
        return int(tok)
    except ValueError:
        return tok


def parse_seed(path):
    """返回 {表名: [ {列名: 值}, ... ]}。"""
    sql = open(path, encoding="utf-8").read()
    tables = {}
    for m in INSERT_RE.finditer(sql):
        table = m.group(1)
        cols = [c.strip() for c in m.group(2).split(",")]
        # VALUES 之后的正文：直到下一个 INSERT / 文件末
        start = m.end()
        nxt = INSERT_RE.search(sql, start)
        end = nxt.start() if nxt else len(sql)
        body = sql[start:end]
        # 去掉紧随其后的注释行与 ALTER 语句
        body = re.split(r"\n\s*(?:--|ALTER TABLE)", body)[0]
        rows = tables.setdefault(table, [])
        for tup in split_tuples(body):
            fields = split_fields(tup)
            if len(fields) != len(cols):
                raise SystemExit(
                    "列数不匹配：表 %s 期望 %d 列，实际 %d 列\n  %s"
                    % (table, len(cols), len(fields), tup[:200])
                )
            rows.append({c: unquote(v) for c, v in zip(cols, fields)})
    return tables


# ---------------------------------------------------------------------------
# 组装
# ---------------------------------------------------------------------------


def num_str(value):
    """JS 数值字面量：None → null，浮点去掉多余的 .0 尾巴。"""
    if value is None:
        return "null"
    if isinstance(value, float):
        if value == int(value):
            return str(int(value))
        return repr(value)
    return str(value)


def js_str(value):
    """JS 字符串字面量（用 JSON 转义，安全处理引号与反斜杠）。"""
    if value is None:
        return "null"
    return json.dumps(str(value), ensure_ascii=False)


def display_text(status, amount, unit):
    """契约 3.3：displayText 由后端给出。三态在此处如实表达。"""
    if status == "MISSING":
        return "暂无数据"
    if amount is None:
        return "暂无数据"
    # 与种子数据一致：整数不显示小数点，微克用 ug
    text = num_str(amount)
    return "%s %s" % (text, unit)


def main():
    if not os.path.exists(SEED):
        raise SystemExit("找不到种子数据：%s" % SEED)

    db = parse_seed(SEED)
    for required in ("food_category", "nutrient", "food", "food_state_variant", "nutrition_value"):
        if required not in db:
            raise SystemExit("种子数据缺少表 %s" % required)
        print("%-22s %4d 行" % (required, len(db[required])))

    # --- 营养素元数据（按 nutrient_id 索引） ---
    nutrients = {}
    for n in db["nutrient"]:
        nutrients[n["id"]] = n
    order = {nid: n["display_order"] for nid, n in nutrients.items()}

    # --- 分类 ---
    categories = sorted(db["food_category"], key=lambda c: c["sort_order"])
    cat_by_id = {c["id"]: c for c in categories}

    # --- 形态（按 food_id 分组） ---
    variants_by_food = {}
    for v in db["food_state_variant"]:
        variants_by_food.setdefault(v["food_id"], []).append(v)
    for rows in variants_by_food.values():
        rows.sort(key=lambda v: (0 if v["is_default"] else 1, v["id"]))

    # --- 营养值（按 variant_id 分组） ---
    values_by_variant = {}
    for nv in db["nutrition_value"]:
        values_by_variant.setdefault(nv["variant_id"], []).append(nv)

    STATE_LABEL = {"RAW": "生", "COOKED": "熟", "PROCESSED": "加工"}

    # --- 食品 ---
    foods = sorted(db["food"], key=lambda f: f["id"])
    counts = {}
    for f in foods:
        counts[f["category_id"]] = counts.get(f["category_id"], 0) + 1

    out = []
    w = out.append

    w("/* eslint-disable */")
    w("/**")
    w(" * 演示数据集 —— **由脚本生成，请勿手工编辑**。")
    w(" *")
    w(" * 生成器：`scripts/gen_demo_dataset.py`")
    w(" * 事实来源：`backend/src/main/resources/db/h2/data.sql`")
    w(" *")
    w(" * 为什么要生成而不是手写：手写的演示数据会与数据库种子各自漂移。曾经出现过")
    w(" * 「分类页写“蔬菜 12 种”、点进去却是空列表」这类自相矛盾的界面。本文件一律从")
    w(" * 种子 SQL 搬运，保证两者永远一致。")
    w(" *")
    w(" * 不变量：")
    w(" *   - `MISSING` 的 `amount` 一律为 `null`，`displayText` 一律为「暂无数据」，")
    w(" *     生成期**绝不**把缺失写成 0；")
    w(" *   - 每条营养值都带 `source`（来源可追溯）；")
    w(" *   - 数值不做任何换算、四舍五入或「补齐」。")
    w(" */")
    w("")
    w("/* 种子数据里登记的唯一来源：data_source.id = 1 */")
    w("const SOURCE = {")
    ds = db["data_source"][0]
    w("  id: %s," % num_str(ds["id"]))
    w("  name: %s," % js_str(ds["name"]))
    w("  code: %s," % js_str(ds["code"]))
    w("  sourceType: %s," % js_str(ds["source_type"]))
    w("  applicableScope: %s," % js_str(ds["applicable_scope"]))
    w("}")
    w("")
    w("/** 营养素元数据：[code, 中文名, 分组, 单位] */")
    w("const NUTRIENTS = {}")
    for nid in sorted(nutrients, key=lambda i: order[i]):
        n = nutrients[nid]
        w(
            "NUTRIENTS[%d] = { code: %s, name: %s, groupCode: %s, unit: %s, displayOrder: %d }"
            % (
                nid,
                js_str(n["code"]),
                js_str(n["name"]),
                js_str(n["group_code"]),
                js_str(n["base_unit"]),
                n["display_order"],
            )
        )
    w("")
    w("/**")
    w(" * 按营养素编码索引的元数据表。")
    w(" *")
    w(" * 用途：接口尚未就绪时，界面需要把后端可能只给编码的字段（例如折算结果里的")
    w(" * `missingNutrients`）显示成中文名。**中文名一律取自数据库，前端不另行维护一份。**")
    w(" */")
    w("export const DEMO_NUTRIENT_BY_CODE = {")
    for nid in sorted(nutrients, key=lambda i: order[i]):
        n = nutrients[nid]
        w(
            "  %s: { id: %d, name: %s, unit: %s, groupCode: %s, displayOrder: %d },"
            % (
                js_str(n["code"]),
                nid,
                js_str(n["name"]),
                js_str(n["base_unit"]),
                js_str(n["group_code"]),
                n["display_order"],
            )
        )
    w("}")
    w("")
    w("/**")
    w(" * 按营养素 id 索引的元数据表（营养行里只带 nutrientId，展示前要翻成中文名）。")
    w(" */")
    w("export const DEMO_NUTRIENT_BY_ID = {")
    for nid in sorted(nutrients, key=lambda i: order[i]):
        n = nutrients[nid]
        w(
            "  %d: { code: %s, name: %s, unit: %s, groupCode: %s, displayOrder: %d },"
            % (
                nid,
                js_str(n["code"]),
                js_str(n["name"]),
                js_str(n["base_unit"]),
                js_str(n["group_code"]),
                n["display_order"],
            )
        )
    w("}")
    w("")
    w("/** 把一条 nutrition_value 行渲染成契约 3.3 的 NutritionRow */")
    w("function toRow(nv) {")
    w("  const meta = NUTRIENTS[nv.nutrientId]")
    w("  const missing = nv.valueStatus === 'MISSING'")
    w("  return {")
    w("    nutrientId: nv.nutrientId,")
    w("    nutrientCode: meta.code,")
    w("    nutrientName: meta.name,")
    w("    groupCode: meta.groupCode,")
    w("    displayOrder: meta.displayOrder,")
    w("    valueStatus: nv.valueStatus,")
    w("    unit: meta.unit,")
    w("    amount: missing ? null : nv.amount,")
    w("    displayText: missing ? '暂无数据' : `${nv.amount} ${meta.unit}`,")
    w("    source: SOURCE,")
    w("  }")
    w("}")
    w("")

    # --- 分类 ---
    w("/** 分类列表（foodCount 由食品清单直接统计，不手写） */")
    w("export const DEMO_CATEGORY_LIST = [")
    for c in categories:
        w(
            "  { id: %s, name: %s, slug: %s, foodCount: %d, iconUrl: null },"
            % (num_str(c["id"]), js_str(c["name"]), js_str(c["slug"]), counts.get(c["id"], 0))
        )
    w("]")
    w("")

    # --- 食品摘要 ---
    w("/** 食品摘要列表（FoodSummary） */")
    w("export const DEMO_FOOD_LIST = [")
    for f in foods:
        cat = cat_by_id[f["category_id"]]
        variants = variants_by_food.get(f["id"], [])
        default_variant = variants[0] if variants else None
        energy = None
        variant_label = None
        if default_variant:
            variant_label = state_label(default_variant)
            for nv in values_by_variant.get(default_variant["id"], []):
                if nutrients[nv["nutrient_id"]]["code"] == "ENERGY" and nv["value_status"] == "PRESENT":
                    energy = nv["amount"]
                    break
        w("  {")
        w("    id: %s," % num_str(f["id"]))
        w("    code: %s," % js_str(f["code"]))
        w("    name: %s," % js_str(f["name"]))
        w("    enName: %s," % js_str(f["en_name"]))
        w("    categoryId: %s," % num_str(cat["id"]))
        w("    categoryName: %s," % js_str(cat["name"]))
        w("    categorySlug: %s," % js_str(cat["slug"]))
        w("    coverImageUrl: null,")
        w("    intro: %s," % js_str(f["intro"]))
        w("    energyKcal: %s," % num_str(energy))
        w("    defaultVariantId: %s," % num_str(default_variant["id"] if default_variant else None))
        w("    variantLabel: %s," % js_str(variant_label))
        w("  },")
    w("]")
    w("")

    # --- 详情 ---
    aliases_by_food = {}
    if "food_alias" in db:
        for a in db["food_alias"]:
            aliases_by_food.setdefault(a["food_id"], []).append(a)

    w("/** 食品详情（FoodDetail），按 id 索引 */")
    w("export const DEMO_FOOD_DETAILS = {")
    for f in foods:
        cat = cat_by_id[f["category_id"]]
        variants = variants_by_food.get(f["id"], [])
        aliases = aliases_by_food.get(f["id"], [])
        w("  %s: {" % num_str(f["id"]))
        w("    id: %s," % num_str(f["id"]))
        w("    code: %s," % js_str(f["code"]))
        w("    name: %s," % js_str(f["name"]))
        w("    enName: %s," % js_str(f["en_name"]))
        w("    scientificName: %s," % js_str(f["scientific_name"]))
        w(
            "    category: { id: %s, name: %s, slug: %s },"
            % (num_str(cat["id"]), js_str(cat["name"]), js_str(cat["slug"]))
        )
        w("    aliases: [")
        for a in aliases:
            w(
                "      { alias: %s, aliasType: %s },"
                % (js_str(a["alias"]), js_str(a["alias_type"]))
            )
        w("    ],")
        w("    intro: %s," % js_str(f["intro"]))
        w("    commonUsage: %s," % js_str(f["common_usage"]))
        w("    origin: %s," % js_str(f["origin"]))
        w("    storageMethod: %s," % js_str(f["storage_method"]))
        w("    suitableFor: %s," % js_str(f["suitable_for"]))
        w("    cautions: %s," % js_str(f["cautions"]))
        # 种子库没有授权图片，故为真实的"无图片"状态，界面须显示占位而不是破图
        w("    images: [],")
        w("    variants: [")
        for v in variants:
            w("      {")
            w("        id: %s," % num_str(v["id"]))
            w("        state: %s," % js_str(v["state"]))
            w("        stateLabel: %s," % js_str(STATE_LABEL.get(v["state"], v["state"])))
            w("        cookingMethod: %s," % js_str(v["cooking_method"]))
            w("        nameSuffix: %s," % js_str(v["name_suffix"]))
            w("        edibleRatio: %s," % num_str(v["edible_ratio"]))
            w("        description: %s," % js_str(v["description"]))
            w("        isDefault: %s," % ("true" if v["is_default"] else "false"))
            w("      },")
        w("    ],")
        updated = f.get("published_at") or f.get("updated_at")
        w("    dataUpdatedAt: %s," % js_str(updated))
        w("    publishStatus: %s," % js_str(f["publish_status"]))
        w("  },")
    w("}")
    w("")

    # --- 营养表 ---
    w("/** 营养表（NutritionTable），按 variantId 索引；三态原样保留 */")
    w("export const DEMO_NUTRITION_TABLES = {")
    missing_total = 0
    zero_total = 0
    present_total = 0
    for f in foods:
        for v in variants_by_food.get(f["id"], []):
            rows = sorted(
                values_by_variant.get(v["id"], []),
                key=lambda nv: order[nv["nutrient_id"]],
            )
            if not rows:
                continue
            miss = sum(1 for r in rows if r["value_status"] == "MISSING")
            zero = sum(1 for r in rows if r["value_status"] == "ZERO")
            present = sum(1 for r in rows if r["value_status"] == "PRESENT")
            missing_total += miss
            zero_total += zero
            present_total += present
            w("  %s: {" % num_str(v["id"]))
            w("    variantId: %s," % num_str(v["id"]))
            w("    foodId: %s," % num_str(f["id"]))
            w("    foodName: %s," % js_str(f["name"]))
            w("    variantLabel: %s," % js_str(state_label(v)))
            w("    referenceAmount: 100,")
            w("    referenceUnit: 'g',")
            w("    referenceNote: '每 100g 可食部',")
            w("    rows: [")
            for r in rows:
                meta = nutrients[r["nutrient_id"]]
                status = r["value_status"]
                amount = None if status == "MISSING" else r["amount"]
                w(
                    "      { nutrientId: %d, valueStatus: %s, amount: %s },"
                    % (r["nutrient_id"], js_str(status), num_str(amount))
                )
            w("    ].map(toRow),")
            w("    incomplete: %s," % ("true" if miss else "false"))
            w("    missingCount: %d," % miss)
            # 数据更新时间：种子数据里 food.published_at / updated_at 均为
            # '2025-01-01 00:00:00'，可直接取用（FR-003 要求展示更新时间）。
            w(
                "    dataUpdatedAt: %s,"
                % js_str(f.get("published_at") or f.get("updated_at") or "2025-01-01 00:00:00")
            )
            # 整表来源：FR-003 要求详情页展示「数据来源」，且来源必须真实存在。
            # 这里引用的是种子数据里登记的那一条 data_source，不新增虚构来源。
            w("    sources: [SOURCE],")
            w("  },")
    w("}")
    w("")
    w("/** 每种食品的默认形态营养表 id，便于「未指定 variantId」时取默认形态 */")
    w("export const DEMO_DEFAULT_VARIANT = {")
    for f in foods:
        variants = variants_by_food.get(f["id"], [])
        if variants:
            w("  %s: %s," % (num_str(f["id"]), num_str(variants[0]["id"])))
    w("}")
    w("")
    w("/** 供页面直接取用的形态→食品反查（演示数据一致性自检用） */")
    w("export const DEMO_VARIANT_FOOD = {")
    for f in foods:
        for v in variants_by_food.get(f["id"], []):
            w("  %s: %s," % (num_str(v["id"]), num_str(f["id"])))
    w("}")
    w("")
    w('/* 三态自检：生成期即断言，避免「缺失被写成 0」这类退化 */')
    w("export const DEMO_DATASET_STATS = {")
    w("  foodCount: %d," % len(foods))
    w("  variantCount: %d," % sum(len(v) for v in variants_by_food.values()))
    w("  presentCount: %d," % present_total)
    w("  zeroCount: %d," % zero_total)
    w("  missingCount: %d," % missing_total)
    w("}")
    w("")

    text = "\n".join(out) + "\n"
    with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)

    print()
    print("已写出 %s（%d 字节）" % (os.path.relpath(OUT, ROOT), len(text.encode("utf-8"))))
    print(
        "食品 %d / 形态 %d / 三态 PRESENT=%d ZERO=%d MISSING=%d"
        % (
            len(foods),
            sum(len(v) for v in variants_by_food.values()),
            present_total,
            zero_total,
            missing_total,
        )
    )


def state_label(variant):
    """形态显示名，与后端 FoodStateVariant.stateLabel 口径一致。"""
    state = variant["state"]
    label = {"RAW": "生", "COOKED": "熟", "PROCESSED": "加工"}.get(state, state)
    suffix = variant.get("name_suffix")
    if suffix:
        return "%s%s" % (label, suffix)
    ratio = variant.get("edible_ratio")
    if state == "RAW" and ratio is not None:
        return "%s（可食部）" % label
    return label


if __name__ == "__main__":
    main()
