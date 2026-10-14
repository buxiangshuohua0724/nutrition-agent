#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""从 USDA FoodData Central 生成 docs/db/nutrition-usda.sql。

两段式，为的是让产物**在没有 200MB 数据集**的机器上也能被校验：

  1) 建档（需要整库数据）：--cache <usda_cache.json>  →  写 docs/db/usda-selection.json
     把本项目实际选用的 USDA 条目及其数值固化成一份小文件（约 40KB），入库留痕。
  2) 渲染（只需要上面那份小文件，默认行为）：读取 usda-selection.json → 写 nutrition-usda.sql

  python scripts/gen_usda_nutrition.py                 # 用已入库的选样重新渲染 SQL
  python scripts/gen_usda_nutrition.py --check         # 只校验 SQL 与选样是否同步（CI 用）
  python scripts/gen_usda_nutrition.py --cache <路径>   # 重新从 USDA 整库数据建档并渲染

USDA 整库下载（美国政府作品，公有领域，可自由再利用）：
  https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_json_2018-04.zip
  https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_foundation_food_json_2025-04-24.zip
解压后用 scan_candidates.py 生成精简缓存 usda_cache.json。

覆盖原则（依据 docs/data-source-policy.md:114 与 docs/agent-design.md FV-05）：
  只使用「可食部/生」条目；熟制条目必须与本平台 variant.cooking_method 一致。
  烹饪方式对不上的形态一律不写数值，缺口清单写进 SQL 头部注释。
"""
import argparse
import io
import json
import os
import sys

def _force_utf8_stdout():
    """Windows 控制台默认 GBK，直接 print 非 GBK 字符（如 ✓）会抛 UnicodeEncodeError。

    仅在作为脚本运行时调用：被 check_contract.py 导入时不能替换它的 stdout。
    """
    try:
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):  # 无 buffer（被重定向到 StringIO）时保持原样
        pass

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DST = os.path.join(ROOT, "docs", "db", "nutrition-usda.sql")
SEL = os.path.join(ROOT, "docs", "db", "usda-selection.json")

SOURCE = {
    "code": "USDA-FDC",
    "name": "《USDA FoodData Central》",
    "publisher": "U.S. Department of Agriculture, Agricultural Research Service",
    "version": "SR Legacy 2018-04 / Foundation Foods 2025-04-24",
    "publish_date": "2018-04-01",
    "url": "https://fdc.nal.usda.gov/",
    "applicable_scope": "美国居民膳食（USDA 检测口径）；本项目仅用于为演示食品补入可核查的参考数值，不作中国居民膳食建议依据",
    "note": (
        "A 级判据满足情况：①国家级机构（USDA-ARS）发布 ②版本与发布日期明确"
        "（SR Legacy 2018-04、Foundation Foods 2025-04-24）③数据集附取样与检测方法学说明 "
        "④以数据集官方发布文件核验（无 ISBN，为政府数据集）⑤适用范围为美国居民，"
        "故仅作参考数值来源，并在 applicable_scope 注明"
    ),
}

# 本地 nutrient.code -> (USDA nutrient id, USDA 字段名)
NUTRIENT_MAP = [
    ("ENERGY", 1008, "Energy (kcal)"),
    ("PROTEIN", 1003, "Protein"),
    ("FAT", 1004, "Total lipid (fat)"),
    ("CARBOHYDRATE", 1005, "Carbohydrate, by difference"),
    ("DIETARY_FIBER", 1079, "Fiber, total dietary"),
    ("SODIUM", 1093, "Sodium, Na"),
    ("CALCIUM", 1087, "Calcium, Ca"),
    ("IRON", 1089, "Iron, Fe"),
    ("POTASSIUM", 1092, "Potassium, K"),
    ("VITAMIN_A", 1106, "Vitamin A, RAE"),
    ("VITAMIN_C", 1162, "Vitamin C, total ascorbic acid"),
    ("VITAMIN_D", 1114, "Vitamin D (D2 + D3)"),
    ("CHOLESTEROL", 1253, "Cholesterol"),
]
NUTRIENT_CODES = [c for c, _, _ in NUTRIENT_MAP] + ["OMEGA3"]

# Omega-3 没有现成总量字段，按下述口径求和（口径写进 source_detail 供核查）
OMEGA3_ALA = 1404         # PUFA 18:3 n-3 c,c,c (ALA)
OMEGA3_ALA_LEGACY = 1270  # 旧版 SR 记录里的 PUFA 18:3，即 ALA
OMEGA3_EPA = 1278
OMEGA3_DHA = 1272
OMEGA3_DPA = 1280

# (食品 code, state, cooking_method, fdcId)；cooking_method 为 None 表示 RAW（库中 IS NULL）
PICKS = [
    # ---- RAW：一律使用 USDA「生/可食部」条目 ----
    ("veg-tomato", "RAW", None, 170457),
    ("veg-cucumber", "RAW", None, 168409),
    ("veg-spinach", "RAW", None, 168462),
    ("veg-carrot", "RAW", None, 170393),
    ("veg-cabbage", "RAW", None, 170390),
    ("veg-broccoli", "RAW", None, 170379),
    ("veg-potato", "RAW", None, 170026),
    ("veg-onion", "RAW", None, 170000),
    ("veg-garlic", "RAW", None, 169230),
    ("veg-eggplant", "RAW", None, 169228),
    ("fruit-blueberry", "RAW", None, 171711),
    ("fruit-apple", "RAW", None, 171688),
    ("fruit-banana", "RAW", None, 173944),
    ("fruit-grape", "RAW", None, 174683),
    ("fruit-orange", "RAW", None, 169097),
    ("meat-chicken-breast", "RAW", None, 171077),
    ("meat-beef", "RAW", None, 169483),
    ("aquatic-salmon", "RAW", None, 175167),
    ("aquatic-bass", "RAW", None, 171948),
    ("egg-chicken-egg", "RAW", None, 171287),
    ("grain-oat", "RAW", None, 169705),
    ("dairy-milk", "RAW", None, 171265),
    ("nut-walnut", "RAW", None, 170187),
    # ---- COOKED：仅收录 USDA 烹饪方式与本地一致的形态 ----
    ("veg-spinach", "COOKED", "煮", 168463),   # Spinach, cooked, boiled, drained
    ("veg-potato", "COOKED", "煮", 170440),    # Potatoes, boiled, cooked without skin
    ("meat-chicken-breast", "COOKED", "煮", 171478),  # Chicken, breast, meat only, cooked, stewed
    ("aquatic-salmon", "COOKED", "烤", 175168),       # Salmon, cooked, dry heat
    ("egg-chicken-egg", "COOKED", "煮", 173424),      # Egg, whole, cooked, hard-boiled
]

# 烹饪方式对不上、因此**不**写入数值的形态（缺口，写进 SQL 头部）
GAPS = [
    ("veg-tomato", "COOKED", "炒", "USDA 只有 Tomatoes, red, ripe, cooked（炖煮），无炒制条目"),
    ("veg-carrot", "COOKED", "蒸", "USDA 只有 Carrots, cooked, boiled, drained（水煮），无蒸制条目"),
    ("veg-broccoli", "COOKED", "蒸", "USDA 只有 Broccoli, cooked, boiled, drained（水煮），无蒸制条目"),
    ("veg-onion", "COOKED", "炒", "USDA 只有 Onions, cooked, boiled, drained（水煮），无炒制条目"),
    ("veg-eggplant", "COOKED", "炸", "USDA 只有 Eggplant, cooked, boiled, drained（水煮），无炸制条目"),
    ("fruit-apple", "COOKED", "烤", "USDA 只有脱水/干燥苹果，无烤制条目"),
    ("aquatic-bass", "COOKED", "蒸", "USDA 只有 Fish, bass, striped, cooked, dry heat（干热），无蒸制条目"),
    ("nut-walnut", "COOKED", "烤", "USDA 只有 Nuts, walnuts, english（生），无烤制条目"),
    ("meat-beef", "COOKED", "煮", "USDA 只有 Beef, composite ... cooked（干热泛值），无炖煮条目"),
]


# --------------------------------------------------------------------------- 数值处理
def fmt(v):
    """DECIMAL(18,6) 可读化：去尾零，整数不带小数点。"""
    if v is None:
        return "NULL"
    s = ("%.6f" % round(float(v), 6)).rstrip("0").rstrip(".")
    return s if s not in ("", "-0") else "0"


def esc(s):
    return (s or "").replace("\\", "\\\\").replace("'", "''")


def _amounts(food):
    return {n["id"]: n["amount"] for n in food["nutes"] if n["id"] is not None}


def _pick(m, nid):
    """返回 'PRESENT'/'ZERO'/'MISSING'；USDA 未提供 -> MISSING。"""
    if nid not in m or m[nid] is None:
        return "MISSING", None
    try:
        v = float(m[nid])
    except (TypeError, ValueError):
        return "MISSING", None
    if v < 0:
        return "MISSING", None
    return ("ZERO", 0.0) if v == 0 else ("PRESENT", v)


def _omega3(m):
    """Omega-3 总量 = ALA + EPA + DHA + DPA。任一分量存在即认为来源提供了 n-3 信息。"""
    parts = {}
    for key, nid in (("ALA", OMEGA3_ALA), ("EPA", OMEGA3_EPA),
                     ("DHA", OMEGA3_DHA), ("DPA", OMEGA3_DPA)):
        v = m.get(nid)
        if v is None and nid == OMEGA3_ALA:
            v = m.get(OMEGA3_ALA_LEGACY)
        parts[key] = float(v) if isinstance(v, (int, float)) else None
    known = [v for v in parts.values() if v is not None]
    if not known:
        return "MISSING", None, ""
    total = round(sum(known), 6)
    return ("ZERO" if total == 0 else "PRESENT"), total, "、".join(
        "%s=%s" % (k, fmt(parts[k])) for k in ("ALA", "EPA", "DHA", "DPA"))


# --------------------------------------------------------------------------- 建档
def build_selection(cache_path):
    if not os.path.exists(cache_path):
        raise SystemExit("找不到 USDA 缓存：%s" % cache_path)
    with open(cache_path, encoding="utf-8") as fh:
        by_id = {f["fdcId"]: f for f in json.load(fh)}

    picks = []
    for food_code, state, cooking, fdc in PICKS:
        food = by_id.get(fdc)
        if food is None:
            raise SystemExit("fdcId %s 不在缓存中（%s/%s）" % (fdc, food_code, state))
        m = _amounts(food)
        values = {}
        for code, nid, _label in NUTRIENT_MAP:
            status, amt = _pick(m, nid)
            values[code] = {"status": status, "amount": amt}
        o_status, o_amt, o_detail = _omega3(m)
        values["OMEGA3"] = {"status": o_status, "amount": o_amt, "omega3_parts": o_detail}
        picks.append({
            "food_code": food_code, "state": state, "cooking_method": cooking,
            "fdc_id": fdc, "description": food["description"], "data_type": food.get("dataType"),
            "values": values,
        })
    return {
        "_comment": "由 scripts/gen_usda_nutrition.py --cache 生成，记录本项目实际选用的 USDA 条目与数值；"
                    "nutrition-usda.sql 由本文件渲染而来，请勿手工编辑。",
        "source": SOURCE,
        "usda_nutrient_ids": {code: nid for code, nid, _ in NUTRIENT_MAP},
        "omega3_rule": "总量 = ALA(1404，缺失时回退 1270) + EPA(1278) + DHA(1272) + DPA(1280)，"
                       "单位与各分量一致（g/100g）",
        "gaps": [{"food_code": g[0], "state": g[1], "cooking_method": g[2], "reason": g[3]}
                 for g in GAPS],
        "picks": picks,
    }


# --------------------------------------------------------------------------- 渲染
HEADER = """-- =============================================================================
-- nutrition-usda.sql —— 用 USDA FoodData Central 真实数值替换演示营养数值
-- =============================================================================
-- 本文件由 scripts/gen_usda_nutrition.py 从 docs/db/usda-selection.json 渲染生成，
-- **请勿手工编辑**。重新生成：
--     python scripts/gen_usda_nutrition.py
-- 校验是否同步（CI）：
--     python scripts/gen_usda_nutrition.py --check
--
-- 数据来源：%(name)s
--   发布机构：%(publisher)s
--   版本/日期：%(version)s / %(publish_date)s
--   地址：%(url)s
--   USDA FoodData Central 是美国农业部（USDA）的官方食品成分库，属美国政府作品，
--   在美国境内不受版权保护（17 U.S.C. §105），可自由再利用；本项目按
--   docs/data-source-policy.md 1.4 的 A 级判据登记，仅取「可食部/生」条目，
--   熟制条目已逐条核对烹饪方式（见下）。选样明细见 docs/db/usda-selection.json。
--
-- 覆盖范围：%(n_variant)d 个形态 × %(n_nutrient)d 个营养素 = %(n_row)d 行
--   其中 RAW 形态 %(n_raw)d 个、USDA 烹饪方式一致的 COOKED 形态 %(n_cooked_match)d 个。
--   数值三态：PRESENT %(n_present)d / ZERO %(n_zero)d / MISSING %(n_missing)d
--   （MISSING 表示 USDA 未提供该食品的该营养素，amount 必须为 NULL）
--   Omega-3 无现成总量字段，按 ALA+EPA+DHA+DPA 求和，口径写进每行 source_detail。
--
-- 执行顺序：必须在 docs/db/data.sql 之后执行。
--   mysql -uroot -p --default-character-set=utf8mb4 nutrition_agent < docs/db/nutrition-usda.sql
-- 本脚本可重复执行：先物理删除本脚本此前写入的 USDA-FDC 行，再整批重插；
-- 演示数据只做逻辑删除（保留审计痕迹）。
--   ⚠ 不能依赖 uk_nv_variant_nutrient 做幂等：该唯一键含可空的 deleted_at，
--   而 MySQL 在唯一索引中把 NULL 视为互不相等，因此**同键的未删除行可以重复插入**
--   （实测：两条完全相同的 INSERT 均成功）。
--
-- 未覆盖的形态（烹饪方式与 USDA 条目对不上，**不写数值、不套用**，仍保留演示数据）：
%(gap_lines)s
--   依据：docs/data-source-policy.md 的 A 级判据「仅可使用其中"可食部/生"相关条目；
--   熟制条目须核对烹饪方式是否与本平台 variant.cooking_method 一致」，
--   以及 docs/agent-design.md FV-05「不同食品 / 不同烹饪方式 / 不同份量的数值不得混用」。
--   这些形态的熟制数值应改用《中国食物成分表》第6版（data_source id=2，尚未录入）。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 一、登记数据来源
-- ---------------------------------------------------------------------------
INSERT INTO data_source
  (code, name, source_type, publisher, version, publish_date, url, applicable_scope, note, created_at, updated_at)
VALUES
  ('%(code)s', '%(name)s', 'DATABASE', '%(publisher)s', '%(version)s', '%(publish_date)s', '%(url)s',
   '%(applicable_scope)s',
   '%(note)s',
   NOW(), NOW())
ON DUPLICATE KEY UPDATE
  name = VALUES(name), source_type = VALUES(source_type), publisher = VALUES(publisher),
  version = VALUES(version), publish_date = VALUES(publish_date), url = VALUES(url),
  applicable_scope = VALUES(applicable_scope), note = VALUES(note), updated_at = NOW();

-- ---------------------------------------------------------------------------
-- 二、待替换的形态清单（临时表，脚本结束即销毁）
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_usda_scope;
CREATE TEMPORARY TABLE tmp_usda_scope (
  food_code      VARCHAR(64)  NOT NULL,
  state          VARCHAR(16)  NOT NULL,
  cooking_method VARCHAR(32)      NULL,
  UNIQUE KEY uk_scope (food_code, state, cooking_method)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_usda_scope (food_code, state, cooking_method) VALUES
%(scope_rows)s;

-- ---------------------------------------------------------------------------
-- 三、让这些形态下的演示数值（DEMO-SEED）下线，保留审计痕迹
-- ---------------------------------------------------------------------------
UPDATE nutrition_value nv
  JOIN food_state_variant v ON v.id = nv.variant_id
  JOIN food f               ON f.id = v.food_id
  JOIN data_source d        ON d.id = nv.source_id
  JOIN tmp_usda_scope t     ON t.food_code = f.code
                           AND t.state = v.state
                           AND IFNULL(t.cooking_method, '') = IFNULL(v.cooking_method, '')
   SET nv.deleted_at = NOW()
 WHERE d.code = 'DEMO-SEED'
   AND nv.deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- 四、写入 USDA 真实数值
-- ---------------------------------------------------------------------------
-- 4.1 先清掉本脚本此前写入的行（本脚本自有数据，物理删除即可幂等重跑）
DELETE nv
  FROM nutrition_value nv
  JOIN food_state_variant v ON v.id = nv.variant_id
  JOIN food f               ON f.id = v.food_id
  JOIN tmp_usda_scope t     ON t.food_code = f.code
                           AND t.state = v.state
                           AND IFNULL(t.cooking_method, '') = IFNULL(v.cooking_method, '')
  JOIN data_source s        ON s.id = nv.source_id
 WHERE s.code = '%(code)s';

-- 4.2 再整批插入
DROP TEMPORARY TABLE IF EXISTS tmp_usda_value;
CREATE TEMPORARY TABLE tmp_usda_value (
  food_code      VARCHAR(64)    NOT NULL,
  state          VARCHAR(16)    NOT NULL,
  cooking_method VARCHAR(32)        NULL,
  nutrient_code  VARCHAR(32)    NOT NULL,
  value_status   VARCHAR(16)    NOT NULL,
  amount         DECIMAL(18,6)      NULL,
  source_detail  VARCHAR(255)       NULL,
  UNIQUE KEY uk_value (food_code, state, cooking_method, nutrient_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_usda_value
  (food_code, state, cooking_method, nutrient_code, value_status, amount, source_detail) VALUES
%(value_rows)s;

INSERT INTO nutrition_value
  (variant_id, nutrient_id, reference_amount, reference_unit, reference_note,
   value_status, amount, source_id, source_detail, review_status,
   created_by, updated_by, reviewed_by, reviewed_at, created_at, updated_at, deleted_at)
SELECT v.id, n.id, 100, 'g',
       IF(v.state = 'COOKED', '每100g熟制可食部', '每100g可食部'),
       t.value_status, t.amount, s.id, t.source_detail, 'APPROVED',
       1, 1, 1, NOW(), NOW(), NOW(), NULL
  FROM tmp_usda_value t
  JOIN food f               ON f.code = t.food_code
  JOIN food_state_variant v ON v.food_id = f.id
                           AND v.state = t.state
                           AND IFNULL(v.cooking_method, '') = IFNULL(t.cooking_method, '')
                           AND v.deleted_at IS NULL
  JOIN nutrient n           ON n.code = t.nutrient_code
  JOIN data_source s        ON s.code = '%(code)s'
ON DUPLICATE KEY UPDATE
  value_status   = VALUES(value_status),
  amount         = VALUES(amount),
  source_id      = VALUES(source_id),
  source_detail  = VALUES(source_detail),
  reference_note = VALUES(reference_note),
  review_status  = VALUES(review_status),
  updated_at     = NOW();

DROP TEMPORARY TABLE IF EXISTS tmp_usda_value;
DROP TEMPORARY TABLE IF EXISTS tmp_usda_scope;

-- ---------------------------------------------------------------------------
-- 五、自检
-- ---------------------------------------------------------------------------
SELECT d.code AS source_code,
       COUNT(*)                                              AS rows_total,
       SUM(nv.value_status = 'PRESENT')                      AS present_cnt,
       SUM(nv.value_status = 'ZERO')                         AS zero_cnt,
       SUM(nv.value_status = 'MISSING')                      AS missing_cnt,
       COUNT(DISTINCT nv.variant_id)                         AS variants_covered
  FROM nutrition_value nv
  JOIN data_source d ON d.id = nv.source_id
 WHERE nv.deleted_at IS NULL
 GROUP BY d.code
 ORDER BY d.code;

SELECT COUNT(*) AS demo_rows_still_live
  FROM nutrition_value nv
  JOIN data_source d ON d.id = nv.source_id
 WHERE d.code = 'DEMO-SEED' AND nv.deleted_at IS NULL;

-- 期望为 0：未删除行中不应存在同 (形态, 营养素, 口径) 的重复
SELECT COUNT(*) AS duplicate_live_keys
  FROM (SELECT variant_id, nutrient_id, reference_amount, reference_unit
          FROM nutrition_value
         WHERE deleted_at IS NULL
         GROUP BY variant_id, nutrient_id, reference_amount, reference_unit
        HAVING COUNT(*) > 1) x;
"""


def render(sel):
    rows = []
    for p in sel["picks"]:
        label = 'USDA FDC fdcId=%d "%s"' % (p["fdc_id"], p["description"])
        for code in NUTRIENT_CODES:
            v = p["values"][code]
            detail = label
            if code == "OMEGA3" and v.get("omega3_parts"):
                detail += "；Omega-3 为求和口径（%s），USDA 无总 n-3 字段" % v["omega3_parts"]
            rows.append((p["food_code"], p["state"], p["cooking_method"],
                         code, v["status"], v["amount"], detail))

    n_present = sum(1 for r in rows if r[4] == "PRESENT")
    n_zero = sum(1 for r in rows if r[4] == "ZERO")
    n_missing = sum(1 for r in rows if r[4] == "MISSING")
    n_raw = sum(1 for p in sel["picks"] if p["state"] == "RAW")

    scope_rows = ",\n".join(
        "  ('%s', '%s', %s)" % (p["food_code"], p["state"],
                               ("'%s'" % p["cooking_method"]) if p["cooking_method"] else "NULL")
        for p in sel["picks"])
    value_rows = ",\n".join(
        "  ('%s', '%s', %s, '%s', '%s', %s, '%s')" % (
            r[0], r[1], ("'%s'" % r[2]) if r[2] else "NULL", r[3], r[4],
            "NULL" if r[5] is None else fmt(r[5]), esc(r[6]))
        for r in rows)
    gap_lines = "\n".join(
        "--   - %-22s %-7s %-3s  %s" % (g["food_code"], g["state"], g["cooking_method"], g["reason"])
        for g in sel["gaps"])

    fields = dict(sel["source"])
    fields.update({
        "n_variant": len(sel["picks"]), "n_nutrient": len(NUTRIENT_CODES), "n_row": len(rows),
        "n_raw": n_raw, "n_cooked_match": len(sel["picks"]) - n_raw,
        "n_present": n_present, "n_zero": n_zero, "n_missing": n_missing,
        "gap_lines": gap_lines, "scope_rows": scope_rows, "value_rows": value_rows,
    })
    return HEADER % fields


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", help="usda_cache.json 路径；给了就先重新建档")
    ap.add_argument("--check", action="store_true", help="只校验 SQL 与选样是否同步")
    ap.add_argument("--selection", default=SEL)
    args = ap.parse_args()

    if args.cache:
        sel = build_selection(os.path.abspath(args.cache))
        with open(SEL, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(sel, fh, ensure_ascii=False, indent=2)
            fh.write("\n")
        print("已更新选样档案 %s" % SEL)
    else:
        if not os.path.exists(args.selection):
            raise SystemExit("找不到选样档案：%s\n请先用 --cache 从 USDA 整库数据建档。" % args.selection)
        with open(args.selection, encoding="utf-8") as fh:
            sel = json.load(fh)

    sql = render(sel)

    if args.check:
        if not os.path.exists(DST):
            print("✗ %s 不存在" % DST)
            return 1
        with open(DST, encoding="utf-8") as fh:
            if fh.read() != sql:
                print("✗ %s 与选样档案不同步：请运行 python scripts/gen_usda_nutrition.py" % DST)
                return 1
        print("✓ %s 与选样档案逐字同步" % DST)
        return 0

    with open(DST, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(sql)
    print("已生成 %s" % DST)
    print("  形态 %d（RAW %d / COOKED %d）× 营养素 %d = %d 行"
          % (len(sel["picks"]), sum(1 for p in sel["picks"] if p["state"] == "RAW"),
             sum(1 for p in sel["picks"] if p["state"] != "RAW"),
             len(NUTRIENT_CODES), len(sel["picks"]) * len(NUTRIENT_CODES)))
    print("  未覆盖形态 %d 个" % len(sel["gaps"]))
    return 0


if __name__ == "__main__":
    _force_utf8_stdout()
    sys.exit(main())
