-- =============================================================================
-- nutrition-usda.sql —— 用 USDA FoodData Central 真实数值替换演示营养数值
-- =============================================================================
-- 本文件由 scripts/gen_usda_nutrition.py 从 docs/db/usda-selection.json 渲染生成，
-- **请勿手工编辑**。重新生成：
--     python scripts/gen_usda_nutrition.py
-- 校验是否同步（CI）：
--     python scripts/gen_usda_nutrition.py --check
--
-- 数据来源：《USDA FoodData Central》
--   发布机构：U.S. Department of Agriculture, Agricultural Research Service
--   版本/日期：SR Legacy 2018-04 / Foundation Foods 2025-04-24 / 2018-04-01
--   地址：https://fdc.nal.usda.gov/
--   USDA FoodData Central 是美国农业部（USDA）的官方食品成分库，属美国政府作品，
--   在美国境内不受版权保护（17 U.S.C. §105），可自由再利用；本项目按
--   docs/data-source-policy.md 1.4 的 A 级判据登记，仅取「可食部/生」条目，
--   熟制条目已逐条核对烹饪方式（见下）。选样明细见 docs/db/usda-selection.json。
--
-- 覆盖范围：28 个形态 × 14 个营养素 = 392 行
--   其中 RAW 形态 23 个、USDA 烹饪方式一致的 COOKED 形态 5 个。
--   数值三态：PRESENT 323 / ZERO 68 / MISSING 1
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
--   - veg-tomato             COOKED  炒    USDA 只有 Tomatoes, red, ripe, cooked（炖煮），无炒制条目
--   - veg-carrot             COOKED  蒸    USDA 只有 Carrots, cooked, boiled, drained（水煮），无蒸制条目
--   - veg-broccoli           COOKED  蒸    USDA 只有 Broccoli, cooked, boiled, drained（水煮），无蒸制条目
--   - veg-onion              COOKED  炒    USDA 只有 Onions, cooked, boiled, drained（水煮），无炒制条目
--   - veg-eggplant           COOKED  炸    USDA 只有 Eggplant, cooked, boiled, drained（水煮），无炸制条目
--   - fruit-apple            COOKED  烤    USDA 只有脱水/干燥苹果，无烤制条目
--   - aquatic-bass           COOKED  蒸    USDA 只有 Fish, bass, striped, cooked, dry heat（干热），无蒸制条目
--   - nut-walnut             COOKED  烤    USDA 只有 Nuts, walnuts, english（生），无烤制条目
--   - meat-beef              COOKED  煮    USDA 只有 Beef, composite ... cooked（干热泛值），无炖煮条目
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
  ('USDA-FDC', '《USDA FoodData Central》', 'DATABASE', 'U.S. Department of Agriculture, Agricultural Research Service', 'SR Legacy 2018-04 / Foundation Foods 2025-04-24', '2018-04-01', 'https://fdc.nal.usda.gov/',
   '美国居民膳食（USDA 检测口径）；本项目仅用于为演示食品补入可核查的参考数值，不作中国居民膳食建议依据',
   'A 级判据满足情况：①国家级机构（USDA-ARS）发布 ②版本与发布日期明确（SR Legacy 2018-04、Foundation Foods 2025-04-24）③数据集附取样与检测方法学说明 ④以数据集官方发布文件核验（无 ISBN，为政府数据集）⑤适用范围为美国居民，故仅作参考数值来源，并在 applicable_scope 注明',
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
  ('veg-tomato', 'RAW', NULL),
  ('veg-cucumber', 'RAW', NULL),
  ('veg-spinach', 'RAW', NULL),
  ('veg-carrot', 'RAW', NULL),
  ('veg-cabbage', 'RAW', NULL),
  ('veg-broccoli', 'RAW', NULL),
  ('veg-potato', 'RAW', NULL),
  ('veg-onion', 'RAW', NULL),
  ('veg-garlic', 'RAW', NULL),
  ('veg-eggplant', 'RAW', NULL),
  ('fruit-blueberry', 'RAW', NULL),
  ('fruit-apple', 'RAW', NULL),
  ('fruit-banana', 'RAW', NULL),
  ('fruit-grape', 'RAW', NULL),
  ('fruit-orange', 'RAW', NULL),
  ('meat-chicken-breast', 'RAW', NULL),
  ('meat-beef', 'RAW', NULL),
  ('aquatic-salmon', 'RAW', NULL),
  ('aquatic-bass', 'RAW', NULL),
  ('egg-chicken-egg', 'RAW', NULL),
  ('grain-oat', 'RAW', NULL),
  ('dairy-milk', 'RAW', NULL),
  ('nut-walnut', 'RAW', NULL),
  ('veg-spinach', 'COOKED', '煮'),
  ('veg-potato', 'COOKED', '煮'),
  ('meat-chicken-breast', 'COOKED', '煮'),
  ('aquatic-salmon', 'COOKED', '烤'),
  ('egg-chicken-egg', 'COOKED', '煮');

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
 WHERE s.code = 'USDA-FDC';

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
  ('veg-tomato', 'RAW', NULL, 'ENERGY', 'PRESENT', 18, 'USDA FDC fdcId=170457 "Tomatoes, red, ripe, raw, year round average"'),
  ('veg-tomato', 'RAW', NULL, 'PROTEIN', 'PRESENT', 0.88, 'USDA FDC fdcId=170457 "Tomatoes, red, ripe, raw, year round average"'),
  ('veg-tomato', 'RAW', NULL, 'FAT', 'PRESENT', 0.2, 'USDA FDC fdcId=170457 "Tomatoes, red, ripe, raw, year round average"'),
  ('veg-tomato', 'RAW', NULL, 'CARBOHYDRATE', 'PRESENT', 3.89, 'USDA FDC fdcId=170457 "Tomatoes, red, ripe, raw, year round average"'),
  ('veg-tomato', 'RAW', NULL, 'DIETARY_FIBER', 'PRESENT', 1.2, 'USDA FDC fdcId=170457 "Tomatoes, red, ripe, raw, year round average"'),
  ('veg-tomato', 'RAW', NULL, 'SODIUM', 'PRESENT', 5, 'USDA FDC fdcId=170457 "Tomatoes, red, ripe, raw, year round average"'),
  ('veg-tomato', 'RAW', NULL, 'CALCIUM', 'PRESENT', 10, 'USDA FDC fdcId=170457 "Tomatoes, red, ripe, raw, year round average"'),
  ('veg-tomato', 'RAW', NULL, 'IRON', 'PRESENT', 0.27, 'USDA FDC fdcId=170457 "Tomatoes, red, ripe, raw, year round average"'),
  ('veg-tomato', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 237, 'USDA FDC fdcId=170457 "Tomatoes, red, ripe, raw, year round average"'),
  ('veg-tomato', 'RAW', NULL, 'VITAMIN_A', 'PRESENT', 42, 'USDA FDC fdcId=170457 "Tomatoes, red, ripe, raw, year round average"'),
  ('veg-tomato', 'RAW', NULL, 'VITAMIN_C', 'PRESENT', 13.7, 'USDA FDC fdcId=170457 "Tomatoes, red, ripe, raw, year round average"'),
  ('veg-tomato', 'RAW', NULL, 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=170457 "Tomatoes, red, ripe, raw, year round average"'),
  ('veg-tomato', 'RAW', NULL, 'CHOLESTEROL', 'ZERO', 0, 'USDA FDC fdcId=170457 "Tomatoes, red, ripe, raw, year round average"'),
  ('veg-tomato', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.003, 'USDA FDC fdcId=170457 "Tomatoes, red, ripe, raw, year round average"；Omega-3 为求和口径（ALA=0.003、EPA=0、DHA=0、DPA=0），USDA 无总 n-3 字段'),
  ('veg-cucumber', 'RAW', NULL, 'ENERGY', 'PRESENT', 15, 'USDA FDC fdcId=168409 "Cucumber, with peel, raw"'),
  ('veg-cucumber', 'RAW', NULL, 'PROTEIN', 'PRESENT', 0.65, 'USDA FDC fdcId=168409 "Cucumber, with peel, raw"'),
  ('veg-cucumber', 'RAW', NULL, 'FAT', 'PRESENT', 0.11, 'USDA FDC fdcId=168409 "Cucumber, with peel, raw"'),
  ('veg-cucumber', 'RAW', NULL, 'CARBOHYDRATE', 'PRESENT', 3.63, 'USDA FDC fdcId=168409 "Cucumber, with peel, raw"'),
  ('veg-cucumber', 'RAW', NULL, 'DIETARY_FIBER', 'PRESENT', 0.5, 'USDA FDC fdcId=168409 "Cucumber, with peel, raw"'),
  ('veg-cucumber', 'RAW', NULL, 'SODIUM', 'PRESENT', 2, 'USDA FDC fdcId=168409 "Cucumber, with peel, raw"'),
  ('veg-cucumber', 'RAW', NULL, 'CALCIUM', 'PRESENT', 16, 'USDA FDC fdcId=168409 "Cucumber, with peel, raw"'),
  ('veg-cucumber', 'RAW', NULL, 'IRON', 'PRESENT', 0.28, 'USDA FDC fdcId=168409 "Cucumber, with peel, raw"'),
  ('veg-cucumber', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 147, 'USDA FDC fdcId=168409 "Cucumber, with peel, raw"'),
  ('veg-cucumber', 'RAW', NULL, 'VITAMIN_A', 'PRESENT', 5, 'USDA FDC fdcId=168409 "Cucumber, with peel, raw"'),
  ('veg-cucumber', 'RAW', NULL, 'VITAMIN_C', 'PRESENT', 2.8, 'USDA FDC fdcId=168409 "Cucumber, with peel, raw"'),
  ('veg-cucumber', 'RAW', NULL, 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=168409 "Cucumber, with peel, raw"'),
  ('veg-cucumber', 'RAW', NULL, 'CHOLESTEROL', 'ZERO', 0, 'USDA FDC fdcId=168409 "Cucumber, with peel, raw"'),
  ('veg-cucumber', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.005, 'USDA FDC fdcId=168409 "Cucumber, with peel, raw"；Omega-3 为求和口径（ALA=0.005、EPA=0、DHA=0、DPA=0），USDA 无总 n-3 字段'),
  ('veg-spinach', 'RAW', NULL, 'ENERGY', 'PRESENT', 23, 'USDA FDC fdcId=168462 "Spinach, raw"'),
  ('veg-spinach', 'RAW', NULL, 'PROTEIN', 'PRESENT', 2.86, 'USDA FDC fdcId=168462 "Spinach, raw"'),
  ('veg-spinach', 'RAW', NULL, 'FAT', 'PRESENT', 0.39, 'USDA FDC fdcId=168462 "Spinach, raw"'),
  ('veg-spinach', 'RAW', NULL, 'CARBOHYDRATE', 'PRESENT', 3.63, 'USDA FDC fdcId=168462 "Spinach, raw"'),
  ('veg-spinach', 'RAW', NULL, 'DIETARY_FIBER', 'PRESENT', 2.2, 'USDA FDC fdcId=168462 "Spinach, raw"'),
  ('veg-spinach', 'RAW', NULL, 'SODIUM', 'PRESENT', 79, 'USDA FDC fdcId=168462 "Spinach, raw"'),
  ('veg-spinach', 'RAW', NULL, 'CALCIUM', 'PRESENT', 99, 'USDA FDC fdcId=168462 "Spinach, raw"'),
  ('veg-spinach', 'RAW', NULL, 'IRON', 'PRESENT', 2.71, 'USDA FDC fdcId=168462 "Spinach, raw"'),
  ('veg-spinach', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 558, 'USDA FDC fdcId=168462 "Spinach, raw"'),
  ('veg-spinach', 'RAW', NULL, 'VITAMIN_A', 'PRESENT', 469, 'USDA FDC fdcId=168462 "Spinach, raw"'),
  ('veg-spinach', 'RAW', NULL, 'VITAMIN_C', 'PRESENT', 28.1, 'USDA FDC fdcId=168462 "Spinach, raw"'),
  ('veg-spinach', 'RAW', NULL, 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=168462 "Spinach, raw"'),
  ('veg-spinach', 'RAW', NULL, 'CHOLESTEROL', 'ZERO', 0, 'USDA FDC fdcId=168462 "Spinach, raw"'),
  ('veg-spinach', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.138, 'USDA FDC fdcId=168462 "Spinach, raw"；Omega-3 为求和口径（ALA=0.138、EPA=0、DHA=0、DPA=0），USDA 无总 n-3 字段'),
  ('veg-carrot', 'RAW', NULL, 'ENERGY', 'PRESENT', 41, 'USDA FDC fdcId=170393 "Carrots, raw"'),
  ('veg-carrot', 'RAW', NULL, 'PROTEIN', 'PRESENT', 0.93, 'USDA FDC fdcId=170393 "Carrots, raw"'),
  ('veg-carrot', 'RAW', NULL, 'FAT', 'PRESENT', 0.24, 'USDA FDC fdcId=170393 "Carrots, raw"'),
  ('veg-carrot', 'RAW', NULL, 'CARBOHYDRATE', 'PRESENT', 9.58, 'USDA FDC fdcId=170393 "Carrots, raw"'),
  ('veg-carrot', 'RAW', NULL, 'DIETARY_FIBER', 'PRESENT', 2.8, 'USDA FDC fdcId=170393 "Carrots, raw"'),
  ('veg-carrot', 'RAW', NULL, 'SODIUM', 'PRESENT', 69, 'USDA FDC fdcId=170393 "Carrots, raw"'),
  ('veg-carrot', 'RAW', NULL, 'CALCIUM', 'PRESENT', 33, 'USDA FDC fdcId=170393 "Carrots, raw"'),
  ('veg-carrot', 'RAW', NULL, 'IRON', 'PRESENT', 0.3, 'USDA FDC fdcId=170393 "Carrots, raw"'),
  ('veg-carrot', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 320, 'USDA FDC fdcId=170393 "Carrots, raw"'),
  ('veg-carrot', 'RAW', NULL, 'VITAMIN_A', 'PRESENT', 835, 'USDA FDC fdcId=170393 "Carrots, raw"'),
  ('veg-carrot', 'RAW', NULL, 'VITAMIN_C', 'PRESENT', 5.9, 'USDA FDC fdcId=170393 "Carrots, raw"'),
  ('veg-carrot', 'RAW', NULL, 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=170393 "Carrots, raw"'),
  ('veg-carrot', 'RAW', NULL, 'CHOLESTEROL', 'ZERO', 0, 'USDA FDC fdcId=170393 "Carrots, raw"'),
  ('veg-carrot', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.002, 'USDA FDC fdcId=170393 "Carrots, raw"；Omega-3 为求和口径（ALA=0.002、EPA=0、DHA=0、DPA=0），USDA 无总 n-3 字段'),
  ('veg-cabbage', 'RAW', NULL, 'ENERGY', 'PRESENT', 13, 'USDA FDC fdcId=170390 "Cabbage, chinese (pak-choi), raw"'),
  ('veg-cabbage', 'RAW', NULL, 'PROTEIN', 'PRESENT', 1.5, 'USDA FDC fdcId=170390 "Cabbage, chinese (pak-choi), raw"'),
  ('veg-cabbage', 'RAW', NULL, 'FAT', 'PRESENT', 0.2, 'USDA FDC fdcId=170390 "Cabbage, chinese (pak-choi), raw"'),
  ('veg-cabbage', 'RAW', NULL, 'CARBOHYDRATE', 'PRESENT', 2.18, 'USDA FDC fdcId=170390 "Cabbage, chinese (pak-choi), raw"'),
  ('veg-cabbage', 'RAW', NULL, 'DIETARY_FIBER', 'PRESENT', 1, 'USDA FDC fdcId=170390 "Cabbage, chinese (pak-choi), raw"'),
  ('veg-cabbage', 'RAW', NULL, 'SODIUM', 'PRESENT', 65, 'USDA FDC fdcId=170390 "Cabbage, chinese (pak-choi), raw"'),
  ('veg-cabbage', 'RAW', NULL, 'CALCIUM', 'PRESENT', 105, 'USDA FDC fdcId=170390 "Cabbage, chinese (pak-choi), raw"'),
  ('veg-cabbage', 'RAW', NULL, 'IRON', 'PRESENT', 0.8, 'USDA FDC fdcId=170390 "Cabbage, chinese (pak-choi), raw"'),
  ('veg-cabbage', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 252, 'USDA FDC fdcId=170390 "Cabbage, chinese (pak-choi), raw"'),
  ('veg-cabbage', 'RAW', NULL, 'VITAMIN_A', 'PRESENT', 223, 'USDA FDC fdcId=170390 "Cabbage, chinese (pak-choi), raw"'),
  ('veg-cabbage', 'RAW', NULL, 'VITAMIN_C', 'PRESENT', 45, 'USDA FDC fdcId=170390 "Cabbage, chinese (pak-choi), raw"'),
  ('veg-cabbage', 'RAW', NULL, 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=170390 "Cabbage, chinese (pak-choi), raw"'),
  ('veg-cabbage', 'RAW', NULL, 'CHOLESTEROL', 'ZERO', 0, 'USDA FDC fdcId=170390 "Cabbage, chinese (pak-choi), raw"'),
  ('veg-cabbage', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.055, 'USDA FDC fdcId=170390 "Cabbage, chinese (pak-choi), raw"；Omega-3 为求和口径（ALA=0.055、EPA=0、DHA=0、DPA=0），USDA 无总 n-3 字段'),
  ('veg-broccoli', 'RAW', NULL, 'ENERGY', 'PRESENT', 34, 'USDA FDC fdcId=170379 "Broccoli, raw"'),
  ('veg-broccoli', 'RAW', NULL, 'PROTEIN', 'PRESENT', 2.82, 'USDA FDC fdcId=170379 "Broccoli, raw"'),
  ('veg-broccoli', 'RAW', NULL, 'FAT', 'PRESENT', 0.37, 'USDA FDC fdcId=170379 "Broccoli, raw"'),
  ('veg-broccoli', 'RAW', NULL, 'CARBOHYDRATE', 'PRESENT', 6.64, 'USDA FDC fdcId=170379 "Broccoli, raw"'),
  ('veg-broccoli', 'RAW', NULL, 'DIETARY_FIBER', 'PRESENT', 2.6, 'USDA FDC fdcId=170379 "Broccoli, raw"'),
  ('veg-broccoli', 'RAW', NULL, 'SODIUM', 'PRESENT', 33, 'USDA FDC fdcId=170379 "Broccoli, raw"'),
  ('veg-broccoli', 'RAW', NULL, 'CALCIUM', 'PRESENT', 47, 'USDA FDC fdcId=170379 "Broccoli, raw"'),
  ('veg-broccoli', 'RAW', NULL, 'IRON', 'PRESENT', 0.73, 'USDA FDC fdcId=170379 "Broccoli, raw"'),
  ('veg-broccoli', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 316, 'USDA FDC fdcId=170379 "Broccoli, raw"'),
  ('veg-broccoli', 'RAW', NULL, 'VITAMIN_A', 'PRESENT', 31, 'USDA FDC fdcId=170379 "Broccoli, raw"'),
  ('veg-broccoli', 'RAW', NULL, 'VITAMIN_C', 'PRESENT', 89.2, 'USDA FDC fdcId=170379 "Broccoli, raw"'),
  ('veg-broccoli', 'RAW', NULL, 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=170379 "Broccoli, raw"'),
  ('veg-broccoli', 'RAW', NULL, 'CHOLESTEROL', 'ZERO', 0, 'USDA FDC fdcId=170379 "Broccoli, raw"'),
  ('veg-broccoli', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.063, 'USDA FDC fdcId=170379 "Broccoli, raw"；Omega-3 为求和口径（ALA=0.063、EPA=0、DHA=0、DPA=0），USDA 无总 n-3 字段'),
  ('veg-potato', 'RAW', NULL, 'ENERGY', 'PRESENT', 77, 'USDA FDC fdcId=170026 "Potatoes, flesh and skin, raw"'),
  ('veg-potato', 'RAW', NULL, 'PROTEIN', 'PRESENT', 2.05, 'USDA FDC fdcId=170026 "Potatoes, flesh and skin, raw"'),
  ('veg-potato', 'RAW', NULL, 'FAT', 'PRESENT', 0.09, 'USDA FDC fdcId=170026 "Potatoes, flesh and skin, raw"'),
  ('veg-potato', 'RAW', NULL, 'CARBOHYDRATE', 'PRESENT', 17.5, 'USDA FDC fdcId=170026 "Potatoes, flesh and skin, raw"'),
  ('veg-potato', 'RAW', NULL, 'DIETARY_FIBER', 'PRESENT', 2.1, 'USDA FDC fdcId=170026 "Potatoes, flesh and skin, raw"'),
  ('veg-potato', 'RAW', NULL, 'SODIUM', 'PRESENT', 6, 'USDA FDC fdcId=170026 "Potatoes, flesh and skin, raw"'),
  ('veg-potato', 'RAW', NULL, 'CALCIUM', 'PRESENT', 12, 'USDA FDC fdcId=170026 "Potatoes, flesh and skin, raw"'),
  ('veg-potato', 'RAW', NULL, 'IRON', 'PRESENT', 0.81, 'USDA FDC fdcId=170026 "Potatoes, flesh and skin, raw"'),
  ('veg-potato', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 425, 'USDA FDC fdcId=170026 "Potatoes, flesh and skin, raw"'),
  ('veg-potato', 'RAW', NULL, 'VITAMIN_A', 'ZERO', 0, 'USDA FDC fdcId=170026 "Potatoes, flesh and skin, raw"'),
  ('veg-potato', 'RAW', NULL, 'VITAMIN_C', 'PRESENT', 19.7, 'USDA FDC fdcId=170026 "Potatoes, flesh and skin, raw"'),
  ('veg-potato', 'RAW', NULL, 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=170026 "Potatoes, flesh and skin, raw"'),
  ('veg-potato', 'RAW', NULL, 'CHOLESTEROL', 'ZERO', 0, 'USDA FDC fdcId=170026 "Potatoes, flesh and skin, raw"'),
  ('veg-potato', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.01, 'USDA FDC fdcId=170026 "Potatoes, flesh and skin, raw"；Omega-3 为求和口径（ALA=0.01、EPA=0、DHA=0、DPA=0），USDA 无总 n-3 字段'),
  ('veg-onion', 'RAW', NULL, 'ENERGY', 'PRESENT', 40, 'USDA FDC fdcId=170000 "Onions, raw"'),
  ('veg-onion', 'RAW', NULL, 'PROTEIN', 'PRESENT', 1.1, 'USDA FDC fdcId=170000 "Onions, raw"'),
  ('veg-onion', 'RAW', NULL, 'FAT', 'PRESENT', 0.1, 'USDA FDC fdcId=170000 "Onions, raw"'),
  ('veg-onion', 'RAW', NULL, 'CARBOHYDRATE', 'PRESENT', 9.34, 'USDA FDC fdcId=170000 "Onions, raw"'),
  ('veg-onion', 'RAW', NULL, 'DIETARY_FIBER', 'PRESENT', 1.7, 'USDA FDC fdcId=170000 "Onions, raw"'),
  ('veg-onion', 'RAW', NULL, 'SODIUM', 'PRESENT', 4, 'USDA FDC fdcId=170000 "Onions, raw"'),
  ('veg-onion', 'RAW', NULL, 'CALCIUM', 'PRESENT', 23, 'USDA FDC fdcId=170000 "Onions, raw"'),
  ('veg-onion', 'RAW', NULL, 'IRON', 'PRESENT', 0.21, 'USDA FDC fdcId=170000 "Onions, raw"'),
  ('veg-onion', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 146, 'USDA FDC fdcId=170000 "Onions, raw"'),
  ('veg-onion', 'RAW', NULL, 'VITAMIN_A', 'ZERO', 0, 'USDA FDC fdcId=170000 "Onions, raw"'),
  ('veg-onion', 'RAW', NULL, 'VITAMIN_C', 'PRESENT', 7.4, 'USDA FDC fdcId=170000 "Onions, raw"'),
  ('veg-onion', 'RAW', NULL, 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=170000 "Onions, raw"'),
  ('veg-onion', 'RAW', NULL, 'CHOLESTEROL', 'ZERO', 0, 'USDA FDC fdcId=170000 "Onions, raw"'),
  ('veg-onion', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.004, 'USDA FDC fdcId=170000 "Onions, raw"；Omega-3 为求和口径（ALA=0.004、EPA=0、DHA=0、DPA=0），USDA 无总 n-3 字段'),
  ('veg-garlic', 'RAW', NULL, 'ENERGY', 'PRESENT', 149, 'USDA FDC fdcId=169230 "Garlic, raw"'),
  ('veg-garlic', 'RAW', NULL, 'PROTEIN', 'PRESENT', 6.36, 'USDA FDC fdcId=169230 "Garlic, raw"'),
  ('veg-garlic', 'RAW', NULL, 'FAT', 'PRESENT', 0.5, 'USDA FDC fdcId=169230 "Garlic, raw"'),
  ('veg-garlic', 'RAW', NULL, 'CARBOHYDRATE', 'PRESENT', 33.1, 'USDA FDC fdcId=169230 "Garlic, raw"'),
  ('veg-garlic', 'RAW', NULL, 'DIETARY_FIBER', 'PRESENT', 2.1, 'USDA FDC fdcId=169230 "Garlic, raw"'),
  ('veg-garlic', 'RAW', NULL, 'SODIUM', 'PRESENT', 17, 'USDA FDC fdcId=169230 "Garlic, raw"'),
  ('veg-garlic', 'RAW', NULL, 'CALCIUM', 'PRESENT', 181, 'USDA FDC fdcId=169230 "Garlic, raw"'),
  ('veg-garlic', 'RAW', NULL, 'IRON', 'PRESENT', 1.7, 'USDA FDC fdcId=169230 "Garlic, raw"'),
  ('veg-garlic', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 401, 'USDA FDC fdcId=169230 "Garlic, raw"'),
  ('veg-garlic', 'RAW', NULL, 'VITAMIN_A', 'ZERO', 0, 'USDA FDC fdcId=169230 "Garlic, raw"'),
  ('veg-garlic', 'RAW', NULL, 'VITAMIN_C', 'PRESENT', 31.2, 'USDA FDC fdcId=169230 "Garlic, raw"'),
  ('veg-garlic', 'RAW', NULL, 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=169230 "Garlic, raw"'),
  ('veg-garlic', 'RAW', NULL, 'CHOLESTEROL', 'ZERO', 0, 'USDA FDC fdcId=169230 "Garlic, raw"'),
  ('veg-garlic', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.02, 'USDA FDC fdcId=169230 "Garlic, raw"；Omega-3 为求和口径（ALA=0.02、EPA=0、DHA=0、DPA=0），USDA 无总 n-3 字段'),
  ('veg-eggplant', 'RAW', NULL, 'ENERGY', 'PRESENT', 25, 'USDA FDC fdcId=169228 "Eggplant, raw"'),
  ('veg-eggplant', 'RAW', NULL, 'PROTEIN', 'PRESENT', 0.98, 'USDA FDC fdcId=169228 "Eggplant, raw"'),
  ('veg-eggplant', 'RAW', NULL, 'FAT', 'PRESENT', 0.18, 'USDA FDC fdcId=169228 "Eggplant, raw"'),
  ('veg-eggplant', 'RAW', NULL, 'CARBOHYDRATE', 'PRESENT', 5.88, 'USDA FDC fdcId=169228 "Eggplant, raw"'),
  ('veg-eggplant', 'RAW', NULL, 'DIETARY_FIBER', 'PRESENT', 3, 'USDA FDC fdcId=169228 "Eggplant, raw"'),
  ('veg-eggplant', 'RAW', NULL, 'SODIUM', 'PRESENT', 2, 'USDA FDC fdcId=169228 "Eggplant, raw"'),
  ('veg-eggplant', 'RAW', NULL, 'CALCIUM', 'PRESENT', 9, 'USDA FDC fdcId=169228 "Eggplant, raw"'),
  ('veg-eggplant', 'RAW', NULL, 'IRON', 'PRESENT', 0.23, 'USDA FDC fdcId=169228 "Eggplant, raw"'),
  ('veg-eggplant', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 229, 'USDA FDC fdcId=169228 "Eggplant, raw"'),
  ('veg-eggplant', 'RAW', NULL, 'VITAMIN_A', 'PRESENT', 1, 'USDA FDC fdcId=169228 "Eggplant, raw"'),
  ('veg-eggplant', 'RAW', NULL, 'VITAMIN_C', 'PRESENT', 2.2, 'USDA FDC fdcId=169228 "Eggplant, raw"'),
  ('veg-eggplant', 'RAW', NULL, 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=169228 "Eggplant, raw"'),
  ('veg-eggplant', 'RAW', NULL, 'CHOLESTEROL', 'ZERO', 0, 'USDA FDC fdcId=169228 "Eggplant, raw"'),
  ('veg-eggplant', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.013, 'USDA FDC fdcId=169228 "Eggplant, raw"；Omega-3 为求和口径（ALA=0.013、EPA=0、DHA=0、DPA=0），USDA 无总 n-3 字段'),
  ('fruit-blueberry', 'RAW', NULL, 'ENERGY', 'PRESENT', 57, 'USDA FDC fdcId=171711 "Blueberries, raw"'),
  ('fruit-blueberry', 'RAW', NULL, 'PROTEIN', 'PRESENT', 0.74, 'USDA FDC fdcId=171711 "Blueberries, raw"'),
  ('fruit-blueberry', 'RAW', NULL, 'FAT', 'PRESENT', 0.33, 'USDA FDC fdcId=171711 "Blueberries, raw"'),
  ('fruit-blueberry', 'RAW', NULL, 'CARBOHYDRATE', 'PRESENT', 14.5, 'USDA FDC fdcId=171711 "Blueberries, raw"'),
  ('fruit-blueberry', 'RAW', NULL, 'DIETARY_FIBER', 'PRESENT', 2.4, 'USDA FDC fdcId=171711 "Blueberries, raw"'),
  ('fruit-blueberry', 'RAW', NULL, 'SODIUM', 'PRESENT', 1, 'USDA FDC fdcId=171711 "Blueberries, raw"'),
  ('fruit-blueberry', 'RAW', NULL, 'CALCIUM', 'PRESENT', 6, 'USDA FDC fdcId=171711 "Blueberries, raw"'),
  ('fruit-blueberry', 'RAW', NULL, 'IRON', 'PRESENT', 0.28, 'USDA FDC fdcId=171711 "Blueberries, raw"'),
  ('fruit-blueberry', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 77, 'USDA FDC fdcId=171711 "Blueberries, raw"'),
  ('fruit-blueberry', 'RAW', NULL, 'VITAMIN_A', 'PRESENT', 3, 'USDA FDC fdcId=171711 "Blueberries, raw"'),
  ('fruit-blueberry', 'RAW', NULL, 'VITAMIN_C', 'PRESENT', 9.7, 'USDA FDC fdcId=171711 "Blueberries, raw"'),
  ('fruit-blueberry', 'RAW', NULL, 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=171711 "Blueberries, raw"'),
  ('fruit-blueberry', 'RAW', NULL, 'CHOLESTEROL', 'ZERO', 0, 'USDA FDC fdcId=171711 "Blueberries, raw"'),
  ('fruit-blueberry', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.058, 'USDA FDC fdcId=171711 "Blueberries, raw"；Omega-3 为求和口径（ALA=0.058、EPA=0、DHA=0、DPA=0），USDA 无总 n-3 字段'),
  ('fruit-apple', 'RAW', NULL, 'ENERGY', 'PRESENT', 52, 'USDA FDC fdcId=171688 "Apples, raw, with skin (Includes foods for USDA''s Food Distribution Program)"'),
  ('fruit-apple', 'RAW', NULL, 'PROTEIN', 'PRESENT', 0.26, 'USDA FDC fdcId=171688 "Apples, raw, with skin (Includes foods for USDA''s Food Distribution Program)"'),
  ('fruit-apple', 'RAW', NULL, 'FAT', 'PRESENT', 0.17, 'USDA FDC fdcId=171688 "Apples, raw, with skin (Includes foods for USDA''s Food Distribution Program)"'),
  ('fruit-apple', 'RAW', NULL, 'CARBOHYDRATE', 'PRESENT', 13.8, 'USDA FDC fdcId=171688 "Apples, raw, with skin (Includes foods for USDA''s Food Distribution Program)"'),
  ('fruit-apple', 'RAW', NULL, 'DIETARY_FIBER', 'PRESENT', 2.4, 'USDA FDC fdcId=171688 "Apples, raw, with skin (Includes foods for USDA''s Food Distribution Program)"'),
  ('fruit-apple', 'RAW', NULL, 'SODIUM', 'PRESENT', 1, 'USDA FDC fdcId=171688 "Apples, raw, with skin (Includes foods for USDA''s Food Distribution Program)"'),
  ('fruit-apple', 'RAW', NULL, 'CALCIUM', 'PRESENT', 6, 'USDA FDC fdcId=171688 "Apples, raw, with skin (Includes foods for USDA''s Food Distribution Program)"'),
  ('fruit-apple', 'RAW', NULL, 'IRON', 'PRESENT', 0.12, 'USDA FDC fdcId=171688 "Apples, raw, with skin (Includes foods for USDA''s Food Distribution Program)"'),
  ('fruit-apple', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 107, 'USDA FDC fdcId=171688 "Apples, raw, with skin (Includes foods for USDA''s Food Distribution Program)"'),
  ('fruit-apple', 'RAW', NULL, 'VITAMIN_A', 'PRESENT', 3, 'USDA FDC fdcId=171688 "Apples, raw, with skin (Includes foods for USDA''s Food Distribution Program)"'),
  ('fruit-apple', 'RAW', NULL, 'VITAMIN_C', 'PRESENT', 4.6, 'USDA FDC fdcId=171688 "Apples, raw, with skin (Includes foods for USDA''s Food Distribution Program)"'),
  ('fruit-apple', 'RAW', NULL, 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=171688 "Apples, raw, with skin (Includes foods for USDA''s Food Distribution Program)"'),
  ('fruit-apple', 'RAW', NULL, 'CHOLESTEROL', 'ZERO', 0, 'USDA FDC fdcId=171688 "Apples, raw, with skin (Includes foods for USDA''s Food Distribution Program)"'),
  ('fruit-apple', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.009, 'USDA FDC fdcId=171688 "Apples, raw, with skin (Includes foods for USDA''s Food Distribution Program)"；Omega-3 为求和口径（ALA=0.009、EPA=0、DHA=0、DPA=0），USDA 无总 n-3 字段'),
  ('fruit-banana', 'RAW', NULL, 'ENERGY', 'PRESENT', 89, 'USDA FDC fdcId=173944 "Bananas, raw"'),
  ('fruit-banana', 'RAW', NULL, 'PROTEIN', 'PRESENT', 1.09, 'USDA FDC fdcId=173944 "Bananas, raw"'),
  ('fruit-banana', 'RAW', NULL, 'FAT', 'PRESENT', 0.33, 'USDA FDC fdcId=173944 "Bananas, raw"'),
  ('fruit-banana', 'RAW', NULL, 'CARBOHYDRATE', 'PRESENT', 22.8, 'USDA FDC fdcId=173944 "Bananas, raw"'),
  ('fruit-banana', 'RAW', NULL, 'DIETARY_FIBER', 'PRESENT', 2.6, 'USDA FDC fdcId=173944 "Bananas, raw"'),
  ('fruit-banana', 'RAW', NULL, 'SODIUM', 'PRESENT', 1, 'USDA FDC fdcId=173944 "Bananas, raw"'),
  ('fruit-banana', 'RAW', NULL, 'CALCIUM', 'PRESENT', 5, 'USDA FDC fdcId=173944 "Bananas, raw"'),
  ('fruit-banana', 'RAW', NULL, 'IRON', 'PRESENT', 0.26, 'USDA FDC fdcId=173944 "Bananas, raw"'),
  ('fruit-banana', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 358, 'USDA FDC fdcId=173944 "Bananas, raw"'),
  ('fruit-banana', 'RAW', NULL, 'VITAMIN_A', 'PRESENT', 3, 'USDA FDC fdcId=173944 "Bananas, raw"'),
  ('fruit-banana', 'RAW', NULL, 'VITAMIN_C', 'PRESENT', 8.7, 'USDA FDC fdcId=173944 "Bananas, raw"'),
  ('fruit-banana', 'RAW', NULL, 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=173944 "Bananas, raw"'),
  ('fruit-banana', 'RAW', NULL, 'CHOLESTEROL', 'ZERO', 0, 'USDA FDC fdcId=173944 "Bananas, raw"'),
  ('fruit-banana', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.027, 'USDA FDC fdcId=173944 "Bananas, raw"；Omega-3 为求和口径（ALA=0.027、EPA=0、DHA=0、DPA=0），USDA 无总 n-3 字段'),
  ('fruit-grape', 'RAW', NULL, 'ENERGY', 'PRESENT', 69, 'USDA FDC fdcId=174683 "Grapes, red or green (European type, such as Thompson seedless), raw"'),
  ('fruit-grape', 'RAW', NULL, 'PROTEIN', 'PRESENT', 0.72, 'USDA FDC fdcId=174683 "Grapes, red or green (European type, such as Thompson seedless), raw"'),
  ('fruit-grape', 'RAW', NULL, 'FAT', 'PRESENT', 0.16, 'USDA FDC fdcId=174683 "Grapes, red or green (European type, such as Thompson seedless), raw"'),
  ('fruit-grape', 'RAW', NULL, 'CARBOHYDRATE', 'PRESENT', 18.1, 'USDA FDC fdcId=174683 "Grapes, red or green (European type, such as Thompson seedless), raw"'),
  ('fruit-grape', 'RAW', NULL, 'DIETARY_FIBER', 'PRESENT', 0.9, 'USDA FDC fdcId=174683 "Grapes, red or green (European type, such as Thompson seedless), raw"'),
  ('fruit-grape', 'RAW', NULL, 'SODIUM', 'PRESENT', 2, 'USDA FDC fdcId=174683 "Grapes, red or green (European type, such as Thompson seedless), raw"'),
  ('fruit-grape', 'RAW', NULL, 'CALCIUM', 'PRESENT', 10, 'USDA FDC fdcId=174683 "Grapes, red or green (European type, such as Thompson seedless), raw"'),
  ('fruit-grape', 'RAW', NULL, 'IRON', 'PRESENT', 0.36, 'USDA FDC fdcId=174683 "Grapes, red or green (European type, such as Thompson seedless), raw"'),
  ('fruit-grape', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 191, 'USDA FDC fdcId=174683 "Grapes, red or green (European type, such as Thompson seedless), raw"'),
  ('fruit-grape', 'RAW', NULL, 'VITAMIN_A', 'PRESENT', 3, 'USDA FDC fdcId=174683 "Grapes, red or green (European type, such as Thompson seedless), raw"'),
  ('fruit-grape', 'RAW', NULL, 'VITAMIN_C', 'PRESENT', 3.2, 'USDA FDC fdcId=174683 "Grapes, red or green (European type, such as Thompson seedless), raw"'),
  ('fruit-grape', 'RAW', NULL, 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=174683 "Grapes, red or green (European type, such as Thompson seedless), raw"'),
  ('fruit-grape', 'RAW', NULL, 'CHOLESTEROL', 'ZERO', 0, 'USDA FDC fdcId=174683 "Grapes, red or green (European type, such as Thompson seedless), raw"'),
  ('fruit-grape', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.011, 'USDA FDC fdcId=174683 "Grapes, red or green (European type, such as Thompson seedless), raw"；Omega-3 为求和口径（ALA=0.011、EPA=0、DHA=0、DPA=0），USDA 无总 n-3 字段'),
  ('fruit-orange', 'RAW', NULL, 'ENERGY', 'PRESENT', 47, 'USDA FDC fdcId=169097 "Oranges, raw, all commercial varieties"'),
  ('fruit-orange', 'RAW', NULL, 'PROTEIN', 'PRESENT', 0.94, 'USDA FDC fdcId=169097 "Oranges, raw, all commercial varieties"'),
  ('fruit-orange', 'RAW', NULL, 'FAT', 'PRESENT', 0.12, 'USDA FDC fdcId=169097 "Oranges, raw, all commercial varieties"'),
  ('fruit-orange', 'RAW', NULL, 'CARBOHYDRATE', 'PRESENT', 11.8, 'USDA FDC fdcId=169097 "Oranges, raw, all commercial varieties"'),
  ('fruit-orange', 'RAW', NULL, 'DIETARY_FIBER', 'PRESENT', 2.4, 'USDA FDC fdcId=169097 "Oranges, raw, all commercial varieties"'),
  ('fruit-orange', 'RAW', NULL, 'SODIUM', 'ZERO', 0, 'USDA FDC fdcId=169097 "Oranges, raw, all commercial varieties"'),
  ('fruit-orange', 'RAW', NULL, 'CALCIUM', 'PRESENT', 40, 'USDA FDC fdcId=169097 "Oranges, raw, all commercial varieties"'),
  ('fruit-orange', 'RAW', NULL, 'IRON', 'PRESENT', 0.1, 'USDA FDC fdcId=169097 "Oranges, raw, all commercial varieties"'),
  ('fruit-orange', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 181, 'USDA FDC fdcId=169097 "Oranges, raw, all commercial varieties"'),
  ('fruit-orange', 'RAW', NULL, 'VITAMIN_A', 'PRESENT', 11, 'USDA FDC fdcId=169097 "Oranges, raw, all commercial varieties"'),
  ('fruit-orange', 'RAW', NULL, 'VITAMIN_C', 'PRESENT', 53.2, 'USDA FDC fdcId=169097 "Oranges, raw, all commercial varieties"'),
  ('fruit-orange', 'RAW', NULL, 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=169097 "Oranges, raw, all commercial varieties"'),
  ('fruit-orange', 'RAW', NULL, 'CHOLESTEROL', 'ZERO', 0, 'USDA FDC fdcId=169097 "Oranges, raw, all commercial varieties"'),
  ('fruit-orange', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.007, 'USDA FDC fdcId=169097 "Oranges, raw, all commercial varieties"；Omega-3 为求和口径（ALA=0.007、EPA=0、DHA=0、DPA=0），USDA 无总 n-3 字段'),
  ('meat-chicken-breast', 'RAW', NULL, 'ENERGY', 'PRESENT', 120, 'USDA FDC fdcId=171077 "Chicken, broiler or fryers, breast, skinless, boneless, meat only, raw"'),
  ('meat-chicken-breast', 'RAW', NULL, 'PROTEIN', 'PRESENT', 22.5, 'USDA FDC fdcId=171077 "Chicken, broiler or fryers, breast, skinless, boneless, meat only, raw"'),
  ('meat-chicken-breast', 'RAW', NULL, 'FAT', 'PRESENT', 2.62, 'USDA FDC fdcId=171077 "Chicken, broiler or fryers, breast, skinless, boneless, meat only, raw"'),
  ('meat-chicken-breast', 'RAW', NULL, 'CARBOHYDRATE', 'ZERO', 0, 'USDA FDC fdcId=171077 "Chicken, broiler or fryers, breast, skinless, boneless, meat only, raw"'),
  ('meat-chicken-breast', 'RAW', NULL, 'DIETARY_FIBER', 'ZERO', 0, 'USDA FDC fdcId=171077 "Chicken, broiler or fryers, breast, skinless, boneless, meat only, raw"'),
  ('meat-chicken-breast', 'RAW', NULL, 'SODIUM', 'PRESENT', 45, 'USDA FDC fdcId=171077 "Chicken, broiler or fryers, breast, skinless, boneless, meat only, raw"'),
  ('meat-chicken-breast', 'RAW', NULL, 'CALCIUM', 'PRESENT', 5, 'USDA FDC fdcId=171077 "Chicken, broiler or fryers, breast, skinless, boneless, meat only, raw"'),
  ('meat-chicken-breast', 'RAW', NULL, 'IRON', 'PRESENT', 0.37, 'USDA FDC fdcId=171077 "Chicken, broiler or fryers, breast, skinless, boneless, meat only, raw"'),
  ('meat-chicken-breast', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 334, 'USDA FDC fdcId=171077 "Chicken, broiler or fryers, breast, skinless, boneless, meat only, raw"'),
  ('meat-chicken-breast', 'RAW', NULL, 'VITAMIN_A', 'PRESENT', 9, 'USDA FDC fdcId=171077 "Chicken, broiler or fryers, breast, skinless, boneless, meat only, raw"'),
  ('meat-chicken-breast', 'RAW', NULL, 'VITAMIN_C', 'ZERO', 0, 'USDA FDC fdcId=171077 "Chicken, broiler or fryers, breast, skinless, boneless, meat only, raw"'),
  ('meat-chicken-breast', 'RAW', NULL, 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=171077 "Chicken, broiler or fryers, breast, skinless, boneless, meat only, raw"'),
  ('meat-chicken-breast', 'RAW', NULL, 'CHOLESTEROL', 'PRESENT', 73, 'USDA FDC fdcId=171077 "Chicken, broiler or fryers, breast, skinless, boneless, meat only, raw"'),
  ('meat-chicken-breast', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.026, 'USDA FDC fdcId=171077 "Chicken, broiler or fryers, breast, skinless, boneless, meat only, raw"；Omega-3 为求和口径（ALA=0.013、EPA=0.003、DHA=0.004、DPA=0.006），USDA 无总 n-3 字段'),
  ('meat-beef', 'RAW', NULL, 'ENERGY', 'PRESENT', 215, 'USDA FDC fdcId=169483 "Beef, composite of trimmed retail cuts, separable lean and fat, trimmed to 1/8" fat, all grades, raw"'),
  ('meat-beef', 'RAW', NULL, 'PROTEIN', 'PRESENT', 20, 'USDA FDC fdcId=169483 "Beef, composite of trimmed retail cuts, separable lean and fat, trimmed to 1/8" fat, all grades, raw"'),
  ('meat-beef', 'RAW', NULL, 'FAT', 'PRESENT', 14.4, 'USDA FDC fdcId=169483 "Beef, composite of trimmed retail cuts, separable lean and fat, trimmed to 1/8" fat, all grades, raw"'),
  ('meat-beef', 'RAW', NULL, 'CARBOHYDRATE', 'ZERO', 0, 'USDA FDC fdcId=169483 "Beef, composite of trimmed retail cuts, separable lean and fat, trimmed to 1/8" fat, all grades, raw"'),
  ('meat-beef', 'RAW', NULL, 'DIETARY_FIBER', 'ZERO', 0, 'USDA FDC fdcId=169483 "Beef, composite of trimmed retail cuts, separable lean and fat, trimmed to 1/8" fat, all grades, raw"'),
  ('meat-beef', 'RAW', NULL, 'SODIUM', 'PRESENT', 52, 'USDA FDC fdcId=169483 "Beef, composite of trimmed retail cuts, separable lean and fat, trimmed to 1/8" fat, all grades, raw"'),
  ('meat-beef', 'RAW', NULL, 'CALCIUM', 'PRESENT', 17, 'USDA FDC fdcId=169483 "Beef, composite of trimmed retail cuts, separable lean and fat, trimmed to 1/8" fat, all grades, raw"'),
  ('meat-beef', 'RAW', NULL, 'IRON', 'PRESENT', 1.69, 'USDA FDC fdcId=169483 "Beef, composite of trimmed retail cuts, separable lean and fat, trimmed to 1/8" fat, all grades, raw"'),
  ('meat-beef', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 283, 'USDA FDC fdcId=169483 "Beef, composite of trimmed retail cuts, separable lean and fat, trimmed to 1/8" fat, all grades, raw"'),
  ('meat-beef', 'RAW', NULL, 'VITAMIN_A', 'PRESENT', 2, 'USDA FDC fdcId=169483 "Beef, composite of trimmed retail cuts, separable lean and fat, trimmed to 1/8" fat, all grades, raw"'),
  ('meat-beef', 'RAW', NULL, 'VITAMIN_C', 'ZERO', 0, 'USDA FDC fdcId=169483 "Beef, composite of trimmed retail cuts, separable lean and fat, trimmed to 1/8" fat, all grades, raw"'),
  ('meat-beef', 'RAW', NULL, 'VITAMIN_D', 'PRESENT', 0.1, 'USDA FDC fdcId=169483 "Beef, composite of trimmed retail cuts, separable lean and fat, trimmed to 1/8" fat, all grades, raw"'),
  ('meat-beef', 'RAW', NULL, 'CHOLESTEROL', 'PRESENT', 72, 'USDA FDC fdcId=169483 "Beef, composite of trimmed retail cuts, separable lean and fat, trimmed to 1/8" fat, all grades, raw"'),
  ('meat-beef', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.016, 'USDA FDC fdcId=169483 "Beef, composite of trimmed retail cuts, separable lean and fat, trimmed to 1/8" fat, all grades, raw"；Omega-3 为求和口径（ALA=0.011、EPA=0、DHA=0、DPA=0.005），USDA 无总 n-3 字段'),
  ('aquatic-salmon', 'RAW', NULL, 'ENERGY', 'PRESENT', 208, 'USDA FDC fdcId=175167 "Fish, salmon, Atlantic, farmed, raw"'),
  ('aquatic-salmon', 'RAW', NULL, 'PROTEIN', 'PRESENT', 20.4, 'USDA FDC fdcId=175167 "Fish, salmon, Atlantic, farmed, raw"'),
  ('aquatic-salmon', 'RAW', NULL, 'FAT', 'PRESENT', 13.4, 'USDA FDC fdcId=175167 "Fish, salmon, Atlantic, farmed, raw"'),
  ('aquatic-salmon', 'RAW', NULL, 'CARBOHYDRATE', 'ZERO', 0, 'USDA FDC fdcId=175167 "Fish, salmon, Atlantic, farmed, raw"'),
  ('aquatic-salmon', 'RAW', NULL, 'DIETARY_FIBER', 'ZERO', 0, 'USDA FDC fdcId=175167 "Fish, salmon, Atlantic, farmed, raw"'),
  ('aquatic-salmon', 'RAW', NULL, 'SODIUM', 'PRESENT', 59, 'USDA FDC fdcId=175167 "Fish, salmon, Atlantic, farmed, raw"'),
  ('aquatic-salmon', 'RAW', NULL, 'CALCIUM', 'PRESENT', 9, 'USDA FDC fdcId=175167 "Fish, salmon, Atlantic, farmed, raw"'),
  ('aquatic-salmon', 'RAW', NULL, 'IRON', 'PRESENT', 0.34, 'USDA FDC fdcId=175167 "Fish, salmon, Atlantic, farmed, raw"'),
  ('aquatic-salmon', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 363, 'USDA FDC fdcId=175167 "Fish, salmon, Atlantic, farmed, raw"'),
  ('aquatic-salmon', 'RAW', NULL, 'VITAMIN_A', 'PRESENT', 58, 'USDA FDC fdcId=175167 "Fish, salmon, Atlantic, farmed, raw"'),
  ('aquatic-salmon', 'RAW', NULL, 'VITAMIN_C', 'PRESENT', 3.9, 'USDA FDC fdcId=175167 "Fish, salmon, Atlantic, farmed, raw"'),
  ('aquatic-salmon', 'RAW', NULL, 'VITAMIN_D', 'PRESENT', 11, 'USDA FDC fdcId=175167 "Fish, salmon, Atlantic, farmed, raw"'),
  ('aquatic-salmon', 'RAW', NULL, 'CHOLESTEROL', 'PRESENT', 55, 'USDA FDC fdcId=175167 "Fish, salmon, Atlantic, farmed, raw"'),
  ('aquatic-salmon', 'RAW', NULL, 'OMEGA3', 'PRESENT', 2.503, 'USDA FDC fdcId=175167 "Fish, salmon, Atlantic, farmed, raw"；Omega-3 为求和口径（ALA=0.148、EPA=0.862、DHA=1.1、DPA=0.393），USDA 无总 n-3 字段'),
  ('aquatic-bass', 'RAW', NULL, 'ENERGY', 'PRESENT', 97, 'USDA FDC fdcId=171948 "Fish, bass, striped, raw"'),
  ('aquatic-bass', 'RAW', NULL, 'PROTEIN', 'PRESENT', 17.7, 'USDA FDC fdcId=171948 "Fish, bass, striped, raw"'),
  ('aquatic-bass', 'RAW', NULL, 'FAT', 'PRESENT', 2.33, 'USDA FDC fdcId=171948 "Fish, bass, striped, raw"'),
  ('aquatic-bass', 'RAW', NULL, 'CARBOHYDRATE', 'ZERO', 0, 'USDA FDC fdcId=171948 "Fish, bass, striped, raw"'),
  ('aquatic-bass', 'RAW', NULL, 'DIETARY_FIBER', 'ZERO', 0, 'USDA FDC fdcId=171948 "Fish, bass, striped, raw"'),
  ('aquatic-bass', 'RAW', NULL, 'SODIUM', 'PRESENT', 69, 'USDA FDC fdcId=171948 "Fish, bass, striped, raw"'),
  ('aquatic-bass', 'RAW', NULL, 'CALCIUM', 'PRESENT', 15, 'USDA FDC fdcId=171948 "Fish, bass, striped, raw"'),
  ('aquatic-bass', 'RAW', NULL, 'IRON', 'PRESENT', 0.84, 'USDA FDC fdcId=171948 "Fish, bass, striped, raw"'),
  ('aquatic-bass', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 256, 'USDA FDC fdcId=171948 "Fish, bass, striped, raw"'),
  ('aquatic-bass', 'RAW', NULL, 'VITAMIN_A', 'PRESENT', 27, 'USDA FDC fdcId=171948 "Fish, bass, striped, raw"'),
  ('aquatic-bass', 'RAW', NULL, 'VITAMIN_C', 'ZERO', 0, 'USDA FDC fdcId=171948 "Fish, bass, striped, raw"'),
  ('aquatic-bass', 'RAW', NULL, 'VITAMIN_D', 'MISSING', NULL, 'USDA FDC fdcId=171948 "Fish, bass, striped, raw"'),
  ('aquatic-bass', 'RAW', NULL, 'CHOLESTEROL', 'PRESENT', 80, 'USDA FDC fdcId=171948 "Fish, bass, striped, raw"'),
  ('aquatic-bass', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.769, 'USDA FDC fdcId=171948 "Fish, bass, striped, raw"；Omega-3 为求和口径（ALA=0.015、EPA=0.169、DHA=0.585、DPA=0），USDA 无总 n-3 字段'),
  ('egg-chicken-egg', 'RAW', NULL, 'ENERGY', 'PRESENT', 143, 'USDA FDC fdcId=171287 "Egg, whole, raw, fresh"'),
  ('egg-chicken-egg', 'RAW', NULL, 'PROTEIN', 'PRESENT', 12.6, 'USDA FDC fdcId=171287 "Egg, whole, raw, fresh"'),
  ('egg-chicken-egg', 'RAW', NULL, 'FAT', 'PRESENT', 9.51, 'USDA FDC fdcId=171287 "Egg, whole, raw, fresh"'),
  ('egg-chicken-egg', 'RAW', NULL, 'CARBOHYDRATE', 'PRESENT', 0.72, 'USDA FDC fdcId=171287 "Egg, whole, raw, fresh"'),
  ('egg-chicken-egg', 'RAW', NULL, 'DIETARY_FIBER', 'ZERO', 0, 'USDA FDC fdcId=171287 "Egg, whole, raw, fresh"'),
  ('egg-chicken-egg', 'RAW', NULL, 'SODIUM', 'PRESENT', 142, 'USDA FDC fdcId=171287 "Egg, whole, raw, fresh"'),
  ('egg-chicken-egg', 'RAW', NULL, 'CALCIUM', 'PRESENT', 56, 'USDA FDC fdcId=171287 "Egg, whole, raw, fresh"'),
  ('egg-chicken-egg', 'RAW', NULL, 'IRON', 'PRESENT', 1.75, 'USDA FDC fdcId=171287 "Egg, whole, raw, fresh"'),
  ('egg-chicken-egg', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 138, 'USDA FDC fdcId=171287 "Egg, whole, raw, fresh"'),
  ('egg-chicken-egg', 'RAW', NULL, 'VITAMIN_A', 'PRESENT', 160, 'USDA FDC fdcId=171287 "Egg, whole, raw, fresh"'),
  ('egg-chicken-egg', 'RAW', NULL, 'VITAMIN_C', 'ZERO', 0, 'USDA FDC fdcId=171287 "Egg, whole, raw, fresh"'),
  ('egg-chicken-egg', 'RAW', NULL, 'VITAMIN_D', 'PRESENT', 2, 'USDA FDC fdcId=171287 "Egg, whole, raw, fresh"'),
  ('egg-chicken-egg', 'RAW', NULL, 'CHOLESTEROL', 'PRESENT', 372, 'USDA FDC fdcId=171287 "Egg, whole, raw, fresh"'),
  ('egg-chicken-egg', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.101, 'USDA FDC fdcId=171287 "Egg, whole, raw, fresh"；Omega-3 为求和口径（ALA=0.036、EPA=0、DHA=0.058、DPA=0.007），USDA 无总 n-3 字段'),
  ('grain-oat', 'RAW', NULL, 'ENERGY', 'PRESENT', 389, 'USDA FDC fdcId=169705 "Oats (Includes foods for USDA''s Food Distribution Program)"'),
  ('grain-oat', 'RAW', NULL, 'PROTEIN', 'PRESENT', 16.9, 'USDA FDC fdcId=169705 "Oats (Includes foods for USDA''s Food Distribution Program)"'),
  ('grain-oat', 'RAW', NULL, 'FAT', 'PRESENT', 6.9, 'USDA FDC fdcId=169705 "Oats (Includes foods for USDA''s Food Distribution Program)"'),
  ('grain-oat', 'RAW', NULL, 'CARBOHYDRATE', 'PRESENT', 66.3, 'USDA FDC fdcId=169705 "Oats (Includes foods for USDA''s Food Distribution Program)"'),
  ('grain-oat', 'RAW', NULL, 'DIETARY_FIBER', 'PRESENT', 10.6, 'USDA FDC fdcId=169705 "Oats (Includes foods for USDA''s Food Distribution Program)"'),
  ('grain-oat', 'RAW', NULL, 'SODIUM', 'PRESENT', 2, 'USDA FDC fdcId=169705 "Oats (Includes foods for USDA''s Food Distribution Program)"'),
  ('grain-oat', 'RAW', NULL, 'CALCIUM', 'PRESENT', 54, 'USDA FDC fdcId=169705 "Oats (Includes foods for USDA''s Food Distribution Program)"'),
  ('grain-oat', 'RAW', NULL, 'IRON', 'PRESENT', 4.72, 'USDA FDC fdcId=169705 "Oats (Includes foods for USDA''s Food Distribution Program)"'),
  ('grain-oat', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 429, 'USDA FDC fdcId=169705 "Oats (Includes foods for USDA''s Food Distribution Program)"'),
  ('grain-oat', 'RAW', NULL, 'VITAMIN_A', 'ZERO', 0, 'USDA FDC fdcId=169705 "Oats (Includes foods for USDA''s Food Distribution Program)"'),
  ('grain-oat', 'RAW', NULL, 'VITAMIN_C', 'ZERO', 0, 'USDA FDC fdcId=169705 "Oats (Includes foods for USDA''s Food Distribution Program)"'),
  ('grain-oat', 'RAW', NULL, 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=169705 "Oats (Includes foods for USDA''s Food Distribution Program)"'),
  ('grain-oat', 'RAW', NULL, 'CHOLESTEROL', 'ZERO', 0, 'USDA FDC fdcId=169705 "Oats (Includes foods for USDA''s Food Distribution Program)"'),
  ('grain-oat', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.111, 'USDA FDC fdcId=169705 "Oats (Includes foods for USDA''s Food Distribution Program)"；Omega-3 为求和口径（ALA=0.111、EPA=NULL、DHA=NULL、DPA=NULL），USDA 无总 n-3 字段'),
  ('dairy-milk', 'RAW', NULL, 'ENERGY', 'PRESENT', 61, 'USDA FDC fdcId=171265 "Milk, whole, 3.25% milkfat, with added vitamin D"'),
  ('dairy-milk', 'RAW', NULL, 'PROTEIN', 'PRESENT', 3.15, 'USDA FDC fdcId=171265 "Milk, whole, 3.25% milkfat, with added vitamin D"'),
  ('dairy-milk', 'RAW', NULL, 'FAT', 'PRESENT', 3.25, 'USDA FDC fdcId=171265 "Milk, whole, 3.25% milkfat, with added vitamin D"'),
  ('dairy-milk', 'RAW', NULL, 'CARBOHYDRATE', 'PRESENT', 4.8, 'USDA FDC fdcId=171265 "Milk, whole, 3.25% milkfat, with added vitamin D"'),
  ('dairy-milk', 'RAW', NULL, 'DIETARY_FIBER', 'ZERO', 0, 'USDA FDC fdcId=171265 "Milk, whole, 3.25% milkfat, with added vitamin D"'),
  ('dairy-milk', 'RAW', NULL, 'SODIUM', 'PRESENT', 43, 'USDA FDC fdcId=171265 "Milk, whole, 3.25% milkfat, with added vitamin D"'),
  ('dairy-milk', 'RAW', NULL, 'CALCIUM', 'PRESENT', 113, 'USDA FDC fdcId=171265 "Milk, whole, 3.25% milkfat, with added vitamin D"'),
  ('dairy-milk', 'RAW', NULL, 'IRON', 'PRESENT', 0.03, 'USDA FDC fdcId=171265 "Milk, whole, 3.25% milkfat, with added vitamin D"'),
  ('dairy-milk', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 132, 'USDA FDC fdcId=171265 "Milk, whole, 3.25% milkfat, with added vitamin D"'),
  ('dairy-milk', 'RAW', NULL, 'VITAMIN_A', 'PRESENT', 46, 'USDA FDC fdcId=171265 "Milk, whole, 3.25% milkfat, with added vitamin D"'),
  ('dairy-milk', 'RAW', NULL, 'VITAMIN_C', 'ZERO', 0, 'USDA FDC fdcId=171265 "Milk, whole, 3.25% milkfat, with added vitamin D"'),
  ('dairy-milk', 'RAW', NULL, 'VITAMIN_D', 'PRESENT', 1.3, 'USDA FDC fdcId=171265 "Milk, whole, 3.25% milkfat, with added vitamin D"'),
  ('dairy-milk', 'RAW', NULL, 'CHOLESTEROL', 'PRESENT', 10, 'USDA FDC fdcId=171265 "Milk, whole, 3.25% milkfat, with added vitamin D"'),
  ('dairy-milk', 'RAW', NULL, 'OMEGA3', 'PRESENT', 0.075, 'USDA FDC fdcId=171265 "Milk, whole, 3.25% milkfat, with added vitamin D"；Omega-3 为求和口径（ALA=0.075、EPA=0、DHA=0、DPA=0），USDA 无总 n-3 字段'),
  ('nut-walnut', 'RAW', NULL, 'ENERGY', 'PRESENT', 654, 'USDA FDC fdcId=170187 "Nuts, walnuts, english"'),
  ('nut-walnut', 'RAW', NULL, 'PROTEIN', 'PRESENT', 15.2, 'USDA FDC fdcId=170187 "Nuts, walnuts, english"'),
  ('nut-walnut', 'RAW', NULL, 'FAT', 'PRESENT', 65.2, 'USDA FDC fdcId=170187 "Nuts, walnuts, english"'),
  ('nut-walnut', 'RAW', NULL, 'CARBOHYDRATE', 'PRESENT', 13.7, 'USDA FDC fdcId=170187 "Nuts, walnuts, english"'),
  ('nut-walnut', 'RAW', NULL, 'DIETARY_FIBER', 'PRESENT', 6.7, 'USDA FDC fdcId=170187 "Nuts, walnuts, english"'),
  ('nut-walnut', 'RAW', NULL, 'SODIUM', 'PRESENT', 2, 'USDA FDC fdcId=170187 "Nuts, walnuts, english"'),
  ('nut-walnut', 'RAW', NULL, 'CALCIUM', 'PRESENT', 98, 'USDA FDC fdcId=170187 "Nuts, walnuts, english"'),
  ('nut-walnut', 'RAW', NULL, 'IRON', 'PRESENT', 2.91, 'USDA FDC fdcId=170187 "Nuts, walnuts, english"'),
  ('nut-walnut', 'RAW', NULL, 'POTASSIUM', 'PRESENT', 441, 'USDA FDC fdcId=170187 "Nuts, walnuts, english"'),
  ('nut-walnut', 'RAW', NULL, 'VITAMIN_A', 'PRESENT', 1, 'USDA FDC fdcId=170187 "Nuts, walnuts, english"'),
  ('nut-walnut', 'RAW', NULL, 'VITAMIN_C', 'PRESENT', 1.3, 'USDA FDC fdcId=170187 "Nuts, walnuts, english"'),
  ('nut-walnut', 'RAW', NULL, 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=170187 "Nuts, walnuts, english"'),
  ('nut-walnut', 'RAW', NULL, 'CHOLESTEROL', 'ZERO', 0, 'USDA FDC fdcId=170187 "Nuts, walnuts, english"'),
  ('nut-walnut', 'RAW', NULL, 'OMEGA3', 'PRESENT', 9.08, 'USDA FDC fdcId=170187 "Nuts, walnuts, english"；Omega-3 为求和口径（ALA=9.08、EPA=0、DHA=0、DPA=0），USDA 无总 n-3 字段'),
  ('veg-spinach', 'COOKED', '煮', 'ENERGY', 'PRESENT', 23, 'USDA FDC fdcId=168463 "Spinach, cooked, boiled, drained, without salt"'),
  ('veg-spinach', 'COOKED', '煮', 'PROTEIN', 'PRESENT', 2.97, 'USDA FDC fdcId=168463 "Spinach, cooked, boiled, drained, without salt"'),
  ('veg-spinach', 'COOKED', '煮', 'FAT', 'PRESENT', 0.26, 'USDA FDC fdcId=168463 "Spinach, cooked, boiled, drained, without salt"'),
  ('veg-spinach', 'COOKED', '煮', 'CARBOHYDRATE', 'PRESENT', 3.75, 'USDA FDC fdcId=168463 "Spinach, cooked, boiled, drained, without salt"'),
  ('veg-spinach', 'COOKED', '煮', 'DIETARY_FIBER', 'PRESENT', 2.4, 'USDA FDC fdcId=168463 "Spinach, cooked, boiled, drained, without salt"'),
  ('veg-spinach', 'COOKED', '煮', 'SODIUM', 'PRESENT', 70, 'USDA FDC fdcId=168463 "Spinach, cooked, boiled, drained, without salt"'),
  ('veg-spinach', 'COOKED', '煮', 'CALCIUM', 'PRESENT', 136, 'USDA FDC fdcId=168463 "Spinach, cooked, boiled, drained, without salt"'),
  ('veg-spinach', 'COOKED', '煮', 'IRON', 'PRESENT', 3.57, 'USDA FDC fdcId=168463 "Spinach, cooked, boiled, drained, without salt"'),
  ('veg-spinach', 'COOKED', '煮', 'POTASSIUM', 'PRESENT', 466, 'USDA FDC fdcId=168463 "Spinach, cooked, boiled, drained, without salt"'),
  ('veg-spinach', 'COOKED', '煮', 'VITAMIN_A', 'PRESENT', 524, 'USDA FDC fdcId=168463 "Spinach, cooked, boiled, drained, without salt"'),
  ('veg-spinach', 'COOKED', '煮', 'VITAMIN_C', 'PRESENT', 9.8, 'USDA FDC fdcId=168463 "Spinach, cooked, boiled, drained, without salt"'),
  ('veg-spinach', 'COOKED', '煮', 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=168463 "Spinach, cooked, boiled, drained, without salt"'),
  ('veg-spinach', 'COOKED', '煮', 'CHOLESTEROL', 'ZERO', 0, 'USDA FDC fdcId=168463 "Spinach, cooked, boiled, drained, without salt"'),
  ('veg-spinach', 'COOKED', '煮', 'OMEGA3', 'PRESENT', 0.092, 'USDA FDC fdcId=168463 "Spinach, cooked, boiled, drained, without salt"；Omega-3 为求和口径（ALA=0.092、EPA=0、DHA=0、DPA=0），USDA 无总 n-3 字段'),
  ('veg-potato', 'COOKED', '煮', 'ENERGY', 'PRESENT', 86, 'USDA FDC fdcId=170440 "Potatoes, boiled, cooked without skin, flesh, without salt"'),
  ('veg-potato', 'COOKED', '煮', 'PROTEIN', 'PRESENT', 1.71, 'USDA FDC fdcId=170440 "Potatoes, boiled, cooked without skin, flesh, without salt"'),
  ('veg-potato', 'COOKED', '煮', 'FAT', 'PRESENT', 0.1, 'USDA FDC fdcId=170440 "Potatoes, boiled, cooked without skin, flesh, without salt"'),
  ('veg-potato', 'COOKED', '煮', 'CARBOHYDRATE', 'PRESENT', 20, 'USDA FDC fdcId=170440 "Potatoes, boiled, cooked without skin, flesh, without salt"'),
  ('veg-potato', 'COOKED', '煮', 'DIETARY_FIBER', 'PRESENT', 1.8, 'USDA FDC fdcId=170440 "Potatoes, boiled, cooked without skin, flesh, without salt"'),
  ('veg-potato', 'COOKED', '煮', 'SODIUM', 'PRESENT', 5, 'USDA FDC fdcId=170440 "Potatoes, boiled, cooked without skin, flesh, without salt"'),
  ('veg-potato', 'COOKED', '煮', 'CALCIUM', 'PRESENT', 8, 'USDA FDC fdcId=170440 "Potatoes, boiled, cooked without skin, flesh, without salt"'),
  ('veg-potato', 'COOKED', '煮', 'IRON', 'PRESENT', 0.31, 'USDA FDC fdcId=170440 "Potatoes, boiled, cooked without skin, flesh, without salt"'),
  ('veg-potato', 'COOKED', '煮', 'POTASSIUM', 'PRESENT', 328, 'USDA FDC fdcId=170440 "Potatoes, boiled, cooked without skin, flesh, without salt"'),
  ('veg-potato', 'COOKED', '煮', 'VITAMIN_A', 'ZERO', 0, 'USDA FDC fdcId=170440 "Potatoes, boiled, cooked without skin, flesh, without salt"'),
  ('veg-potato', 'COOKED', '煮', 'VITAMIN_C', 'PRESENT', 7.4, 'USDA FDC fdcId=170440 "Potatoes, boiled, cooked without skin, flesh, without salt"'),
  ('veg-potato', 'COOKED', '煮', 'VITAMIN_D', 'ZERO', 0, 'USDA FDC fdcId=170440 "Potatoes, boiled, cooked without skin, flesh, without salt"'),
  ('veg-potato', 'COOKED', '煮', 'CHOLESTEROL', 'ZERO', 0, 'USDA FDC fdcId=170440 "Potatoes, boiled, cooked without skin, flesh, without salt"'),
  ('veg-potato', 'COOKED', '煮', 'OMEGA3', 'PRESENT', 0.01, 'USDA FDC fdcId=170440 "Potatoes, boiled, cooked without skin, flesh, without salt"；Omega-3 为求和口径（ALA=0.01、EPA=0、DHA=0、DPA=0），USDA 无总 n-3 字段'),
  ('meat-chicken-breast', 'COOKED', '煮', 'ENERGY', 'PRESENT', 151, 'USDA FDC fdcId=171478 "Chicken, broilers or fryers, breast, meat only, cooked, stewed"'),
  ('meat-chicken-breast', 'COOKED', '煮', 'PROTEIN', 'PRESENT', 29, 'USDA FDC fdcId=171478 "Chicken, broilers or fryers, breast, meat only, cooked, stewed"'),
  ('meat-chicken-breast', 'COOKED', '煮', 'FAT', 'PRESENT', 3.03, 'USDA FDC fdcId=171478 "Chicken, broilers or fryers, breast, meat only, cooked, stewed"'),
  ('meat-chicken-breast', 'COOKED', '煮', 'CARBOHYDRATE', 'ZERO', 0, 'USDA FDC fdcId=171478 "Chicken, broilers or fryers, breast, meat only, cooked, stewed"'),
  ('meat-chicken-breast', 'COOKED', '煮', 'DIETARY_FIBER', 'ZERO', 0, 'USDA FDC fdcId=171478 "Chicken, broilers or fryers, breast, meat only, cooked, stewed"'),
  ('meat-chicken-breast', 'COOKED', '煮', 'SODIUM', 'PRESENT', 63, 'USDA FDC fdcId=171478 "Chicken, broilers or fryers, breast, meat only, cooked, stewed"'),
  ('meat-chicken-breast', 'COOKED', '煮', 'CALCIUM', 'PRESENT', 13, 'USDA FDC fdcId=171478 "Chicken, broilers or fryers, breast, meat only, cooked, stewed"'),
  ('meat-chicken-breast', 'COOKED', '煮', 'IRON', 'PRESENT', 0.88, 'USDA FDC fdcId=171478 "Chicken, broilers or fryers, breast, meat only, cooked, stewed"'),
  ('meat-chicken-breast', 'COOKED', '煮', 'POTASSIUM', 'PRESENT', 187, 'USDA FDC fdcId=171478 "Chicken, broilers or fryers, breast, meat only, cooked, stewed"'),
  ('meat-chicken-breast', 'COOKED', '煮', 'VITAMIN_A', 'PRESENT', 6, 'USDA FDC fdcId=171478 "Chicken, broilers or fryers, breast, meat only, cooked, stewed"'),
  ('meat-chicken-breast', 'COOKED', '煮', 'VITAMIN_C', 'ZERO', 0, 'USDA FDC fdcId=171478 "Chicken, broilers or fryers, breast, meat only, cooked, stewed"'),
  ('meat-chicken-breast', 'COOKED', '煮', 'VITAMIN_D', 'PRESENT', 0.1, 'USDA FDC fdcId=171478 "Chicken, broilers or fryers, breast, meat only, cooked, stewed"'),
  ('meat-chicken-breast', 'COOKED', '煮', 'CHOLESTEROL', 'PRESENT', 77, 'USDA FDC fdcId=171478 "Chicken, broilers or fryers, breast, meat only, cooked, stewed"'),
  ('meat-chicken-breast', 'COOKED', '煮', 'OMEGA3', 'PRESENT', 0.06, 'USDA FDC fdcId=171478 "Chicken, broilers or fryers, breast, meat only, cooked, stewed"；Omega-3 为求和口径（ALA=0.02、EPA=0.01、DHA=0.02、DPA=0.01），USDA 无总 n-3 字段'),
  ('aquatic-salmon', 'COOKED', '烤', 'ENERGY', 'PRESENT', 206, 'USDA FDC fdcId=175168 "Fish, salmon, Atlantic, farmed, cooked, dry heat"'),
  ('aquatic-salmon', 'COOKED', '烤', 'PROTEIN', 'PRESENT', 22.1, 'USDA FDC fdcId=175168 "Fish, salmon, Atlantic, farmed, cooked, dry heat"'),
  ('aquatic-salmon', 'COOKED', '烤', 'FAT', 'PRESENT', 12.4, 'USDA FDC fdcId=175168 "Fish, salmon, Atlantic, farmed, cooked, dry heat"'),
  ('aquatic-salmon', 'COOKED', '烤', 'CARBOHYDRATE', 'ZERO', 0, 'USDA FDC fdcId=175168 "Fish, salmon, Atlantic, farmed, cooked, dry heat"'),
  ('aquatic-salmon', 'COOKED', '烤', 'DIETARY_FIBER', 'ZERO', 0, 'USDA FDC fdcId=175168 "Fish, salmon, Atlantic, farmed, cooked, dry heat"'),
  ('aquatic-salmon', 'COOKED', '烤', 'SODIUM', 'PRESENT', 61, 'USDA FDC fdcId=175168 "Fish, salmon, Atlantic, farmed, cooked, dry heat"'),
  ('aquatic-salmon', 'COOKED', '烤', 'CALCIUM', 'PRESENT', 15, 'USDA FDC fdcId=175168 "Fish, salmon, Atlantic, farmed, cooked, dry heat"'),
  ('aquatic-salmon', 'COOKED', '烤', 'IRON', 'PRESENT', 0.34, 'USDA FDC fdcId=175168 "Fish, salmon, Atlantic, farmed, cooked, dry heat"'),
  ('aquatic-salmon', 'COOKED', '烤', 'POTASSIUM', 'PRESENT', 384, 'USDA FDC fdcId=175168 "Fish, salmon, Atlantic, farmed, cooked, dry heat"'),
  ('aquatic-salmon', 'COOKED', '烤', 'VITAMIN_A', 'PRESENT', 69, 'USDA FDC fdcId=175168 "Fish, salmon, Atlantic, farmed, cooked, dry heat"'),
  ('aquatic-salmon', 'COOKED', '烤', 'VITAMIN_C', 'PRESENT', 3.7, 'USDA FDC fdcId=175168 "Fish, salmon, Atlantic, farmed, cooked, dry heat"'),
  ('aquatic-salmon', 'COOKED', '烤', 'VITAMIN_D', 'PRESENT', 13.1, 'USDA FDC fdcId=175168 "Fish, salmon, Atlantic, farmed, cooked, dry heat"'),
  ('aquatic-salmon', 'COOKED', '烤', 'CHOLESTEROL', 'PRESENT', 63, 'USDA FDC fdcId=175168 "Fish, salmon, Atlantic, farmed, cooked, dry heat"'),
  ('aquatic-salmon', 'COOKED', '烤', 'OMEGA3', 'PRESENT', 2.433, 'USDA FDC fdcId=175168 "Fish, salmon, Atlantic, farmed, cooked, dry heat"；Omega-3 为求和口径（ALA=0.113、EPA=0.69、DHA=1.46、DPA=0.17），USDA 无总 n-3 字段'),
  ('egg-chicken-egg', 'COOKED', '煮', 'ENERGY', 'PRESENT', 155, 'USDA FDC fdcId=173424 "Egg, whole, cooked, hard-boiled"'),
  ('egg-chicken-egg', 'COOKED', '煮', 'PROTEIN', 'PRESENT', 12.6, 'USDA FDC fdcId=173424 "Egg, whole, cooked, hard-boiled"'),
  ('egg-chicken-egg', 'COOKED', '煮', 'FAT', 'PRESENT', 10.6, 'USDA FDC fdcId=173424 "Egg, whole, cooked, hard-boiled"'),
  ('egg-chicken-egg', 'COOKED', '煮', 'CARBOHYDRATE', 'PRESENT', 1.12, 'USDA FDC fdcId=173424 "Egg, whole, cooked, hard-boiled"'),
  ('egg-chicken-egg', 'COOKED', '煮', 'DIETARY_FIBER', 'ZERO', 0, 'USDA FDC fdcId=173424 "Egg, whole, cooked, hard-boiled"'),
  ('egg-chicken-egg', 'COOKED', '煮', 'SODIUM', 'PRESENT', 124, 'USDA FDC fdcId=173424 "Egg, whole, cooked, hard-boiled"'),
  ('egg-chicken-egg', 'COOKED', '煮', 'CALCIUM', 'PRESENT', 50, 'USDA FDC fdcId=173424 "Egg, whole, cooked, hard-boiled"'),
  ('egg-chicken-egg', 'COOKED', '煮', 'IRON', 'PRESENT', 1.19, 'USDA FDC fdcId=173424 "Egg, whole, cooked, hard-boiled"'),
  ('egg-chicken-egg', 'COOKED', '煮', 'POTASSIUM', 'PRESENT', 126, 'USDA FDC fdcId=173424 "Egg, whole, cooked, hard-boiled"'),
  ('egg-chicken-egg', 'COOKED', '煮', 'VITAMIN_A', 'PRESENT', 149, 'USDA FDC fdcId=173424 "Egg, whole, cooked, hard-boiled"'),
  ('egg-chicken-egg', 'COOKED', '煮', 'VITAMIN_C', 'ZERO', 0, 'USDA FDC fdcId=173424 "Egg, whole, cooked, hard-boiled"'),
  ('egg-chicken-egg', 'COOKED', '煮', 'VITAMIN_D', 'PRESENT', 2.2, 'USDA FDC fdcId=173424 "Egg, whole, cooked, hard-boiled"'),
  ('egg-chicken-egg', 'COOKED', '煮', 'CHOLESTEROL', 'PRESENT', 373, 'USDA FDC fdcId=173424 "Egg, whole, cooked, hard-boiled"'),
  ('egg-chicken-egg', 'COOKED', '煮', 'OMEGA3', 'PRESENT', 0.078, 'USDA FDC fdcId=173424 "Egg, whole, cooked, hard-boiled"；Omega-3 为求和口径（ALA=0.035、EPA=0.005、DHA=0.038、DPA=0），USDA 无总 n-3 字段');

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
  JOIN data_source s        ON s.code = 'USDA-FDC'
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
