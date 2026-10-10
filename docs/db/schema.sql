-- =============================================================================
-- AI 食品营养师 — 数据库结构 V1.0
-- =============================================================================
-- 目标数据库 : MySQL 8.0+（charset utf8mb4 / collate utf8mb4_0900_ai_ci）
-- 兼容性     : 亦可在 H2 2.x (MODE=MySQL) 下执行，供 dev profile 使用
-- 设计依据   : nutrition_agent/docs/PRD.md
--              - 5.3 食品详情  : 营养数据必须带食品状态与参考重量
--              - 5.4 营养成分  : 区分缺失值与真实零值
--              - 8.1 营养数据  : 来源、单位、食品状态、适用条件必须可追溯
--              - 11.x 初始化   : 食品 / 图片 / 营养 / 知识四类种子数据
-- =============================================================================
--
-- 关键设计决策（对应 PRD 的强制约束，不可随意简化）
--
-- [D1] 「食品形态」独立成表 food_state_variant，而不是在 food 上加几个字段。
--      原因：PRD 5.3 要求「生食、熟食、去皮食品及加工食品应在数据允许的情况下
--      分别建档」。西红柿(生) 与 西红柿(炒) 的热量差异极大，若共用一个
--      nutrition_value 集合，必然出现数据混用，这是最严重的事实性错误来源。
--      → 营养数据永远挂在 variant 上，绝不挂在 food 上。
--
-- [D2] nutrition_value 用三列表达「缺失 / 零 / 有值」：
--        value_status ENUM('PRESENT','ZERO','MISSING') + amount DECIMAL(18,6) NULL
--      原因：PRD 5.4 第 8 条与验收标准明确要求「不会把未知营养成分自动当作零」。
--      仅用 amount IS NULL 表达缺失无法区分「已知为 0」与「尚未录入」。
--      PRESENT → amount 非空；ZERO → amount = 0；MISSING → amount 为 NULL。
--      应用层禁止用 0 兜底缺失。
--
-- [D3] 营养数据与计量单位分离：nutrition_value 关联 nutrient 字典，单位来自字典。
--      原因：PRD 5.4 第 6 条要求「对单位换算进行统一处理」。单位写死在数据行里
--      会导致同一营养素出现 g / mg / μg 混用而无法汇总。
--
-- [D4] 来源独立成表且 nutrition_value 外键指向它（NOT NULL）。
--      原因：PRD 2.2 要求营养数据来源可追溯率 100%。可追溯率若允许 NULL
--      就等于放弃该指标。演示数据也必须挂一个标记为 demo 的 source。
--
-- [D5] food_alias 带 alias_type，且加唯一约束防止别名劫持其他食品标准名。
--      原因：PRD 5.2 业务规则「别名不能随意覆盖其他食品的标准名称」。
--
-- [D6] 审核状态与发布状态分离（review_status / publish_status）。
--      原因：PRD 7.4 与 5.9「草稿数据默认不对普通用户开放」，且「不允许未审核的
--      外部内容直接进入正式营养知识库」。审核通过 ≠ 自动发布，两件事必须可分别控制。
--
-- [D7] 所有内容表带审计字段（created_by/updated_by/reviewed_by + 时间戳）。
--      原因：PRD 9.3 要求「后台内容修改需要有审计记录」。
--
-- [D8] 逻辑删除统一用 deleted_at (NULL = 未删除)，不用 is_deleted 布尔。
--      原因：能同时回答「谁在何时删的」，并可支持误删恢复与审计。
--      注意：唯一约束需带 deleted_at 参与，避免软删后无法重建同名记录。
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- 一、用户与权限
-- =============================================================================

DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `username`      VARCHAR(64)  NOT NULL                COMMENT '登录名',
  `email`         VARCHAR(128)     NULL                COMMENT '邮箱，可空',
  `password_hash` VARCHAR(255) NOT NULL                COMMENT 'BCrypt 散列，禁止明文',
  `nickname`      VARCHAR(64)      NULL                COMMENT '昵称',
  `avatar_url`    VARCHAR(512)     NULL                COMMENT '头像地址',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED / LOCKED',
  `last_login_at` DATETIME         NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`    DATETIME         NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_user_username` UNIQUE (`username`, `deleted_at`),
  CONSTRAINT `uk_user_email`    UNIQUE (`email`,    `deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账户（FR-007）';

DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `code`        VARCHAR(32) NOT NULL COMMENT 'GUEST / USER / CONTENT_ADMIN / SYS_ADMIN',
  `name`        VARCHAR(64) NOT NULL COMMENT '角色显示名',
  `description` VARCHAR(255)    NULL,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色（PRD 3.1，四类角色权限必须在设计上可区分）';

DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `user_id`    BIGINT   NOT NULL,
  `role_id`    BIGINT   NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`, `role_id`),
  KEY `idx_ur_role` (`role_id`),
  CONSTRAINT `fk_ur_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_ur_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';

DROP TABLE IF EXISTS `sys_auth_token`;
CREATE TABLE `sys_auth_token` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT       NOT NULL,
  `token_hash`   VARCHAR(128) NOT NULL COMMENT '令牌散列，禁止存储原始令牌',
  `issued_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at`   DATETIME     NOT NULL,
  `revoked_at`   DATETIME         NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token_hash` (`token_hash`),
  KEY `idx_token_user` (`user_id`),
  CONSTRAINT `fk_token_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录令牌（可撤销、可过期）';

DROP TABLE IF EXISTS `user_profile`;
CREATE TABLE `user_profile` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT      NOT NULL,
  `age_range`     VARCHAR(16)     NULL COMMENT '年龄区间，不存精确生日（最小化收集）',
  `gender`        VARCHAR(16)     NULL COMMENT 'MALE / FEMALE / UNDISCLOSED',
  `height_cm`     DECIMAL(5,1)    NULL COMMENT '用户主动提供',
  `weight_kg`     DECIMAL(5,1)    NULL COMMENT '用户主动提供',
  `activity_level` VARCHAR(24)    NULL COMMENT 'SEDENTARY / LIGHT / MODERATE / ACTIVE / VERY_ACTIVE',
  `diet_goal`     VARCHAR(32)     NULL COMMENT 'WEIGHT_LOSS / MAINTAIN / MUSCLE_GAIN / ...',
  `allergens`     JSON            NULL COMMENT '过敏原列表，用户主动提供',
  `diet_preferences` JSON         NULL COMMENT '饮食偏好（素食等）',
  `consent_at`    DATETIME        NULL COMMENT '用户明确授权时间（PRD 5.7 要求主动提供或明确授权）',
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_profile_user` (`user_id`),
  CONSTRAINT `fk_profile_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户资料与饮食偏好（FR-007，全部字段可空=用户可不填）';

-- =============================================================================
-- 二、食品域
-- =============================================================================

DROP TABLE IF EXISTS `food_category`;
CREATE TABLE `food_category` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `parent_id`   BIGINT           NULL COMMENT '支持多级分类，顶级为 NULL',
  `slug`        VARCHAR(64)  NOT NULL COMMENT 'URL 友好标识',
  `name`        VARCHAR(64)  NOT NULL COMMENT '分类名，如 蔬菜',
  `icon`        VARCHAR(64)      NULL COMMENT '前端图标标识',
  `intro`       VARCHAR(512)     NULL COMMENT '分类介绍',
  `sort_order`  INT          NOT NULL DEFAULT 0,
  `enabled`     TINYINT(1)   NOT NULL DEFAULT 1,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`  DATETIME         NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_slug` (`slug`, `deleted_at`),
  KEY `idx_category_parent` (`parent_id`),
  CONSTRAINT `fk_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `food_category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食品分类（FR-001/FR-002）';

DROP TABLE IF EXISTS `food`;
CREATE TABLE `food` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `code`             VARCHAR(64)  NOT NULL COMMENT '业务编码，种子数据与导入用',
  `category_id`      BIGINT           NULL,
  `name`             VARCHAR(128) NOT NULL COMMENT '中文标准名称',
  `en_name`          VARCHAR(128)     NULL COMMENT '英文名称',
  `scientific_name`  VARCHAR(128)     NULL COMMENT '学名，用于相似品种区分',
  `intro`            TEXT             NULL COMMENT '食品介绍',
  `common_usage`     TEXT             NULL COMMENT '常见食用方式',
  `origin`           VARCHAR(255)     NULL COMMENT '产地/来源描述',
  `storage_method`   VARCHAR(255)     NULL COMMENT '储存方式',
  `suitable_for`     TEXT             NULL COMMENT '适宜人群（有依据时填写）',
  `cautions`         TEXT             NULL COMMENT '注意事项',
  `search_keywords`  VARCHAR(512)     NULL COMMENT '搜索加权词（去空格分隔）',
  `review_status`    VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'   COMMENT 'DRAFT / PENDING / APPROVED / REJECTED',
  `publish_status`   VARCHAR(16)  NOT NULL DEFAULT 'UNPUBLISHED' COMMENT 'UNPUBLISHED / PUBLISHED / OFFLINE',
  `published_at`     DATETIME         NULL,
  `created_by`       BIGINT           NULL,
  `updated_by`       BIGINT           NULL,
  `reviewed_by`      BIGINT           NULL,
  `reviewed_at`      DATETIME         NULL,
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`       DATETIME         NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_food_code` (`code`, `deleted_at`),
  KEY `idx_food_name`      (`name`),
  KEY `idx_food_category`  (`category_id`),
  -- 前台查询几乎恒带此条件，独立索引避免回表后再过滤
  KEY `idx_food_publish`   (`publish_status`, `review_status`, `deleted_at`),
  CONSTRAINT `fk_food_category` FOREIGN KEY (`category_id`) REFERENCES `food_category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食品主表（FR-003）— 只存"食品是什么"，不存任何营养数值';

DROP TABLE IF EXISTS `food_alias`;
CREATE TABLE `food_alias` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `food_id`    BIGINT       NOT NULL,
  `alias`      VARCHAR(128) NOT NULL COMMENT '别名/俗称/英文别名/拼音',
  `alias_type` VARCHAR(24)  NOT NULL DEFAULT 'COLLOQUIAL'
               COMMENT 'COLLOQUIAL 俗称 / ENGLISH 英文 / PINYIN 拼音 / SCIENTIFIC 学名 / BRAND 品牌',
  -- 别名匹配优先级低于标准名精确匹配，用于搜索排序（PRD 5.2 业务规则）
  `weight`     INT          NOT NULL DEFAULT 0 COMMENT '别名匹配权重，越大越优先',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` DATETIME         NULL,
  PRIMARY KEY (`id`),
  -- 同一食品下同一别名不重复；跨食品同名别名在服务层做冲突校验
  UNIQUE KEY `uk_alias_food` (`food_id`, `alias`, `deleted_at`),
  KEY `idx_alias_alias` (`alias`),
  CONSTRAINT `fk_alias_food` FOREIGN KEY (`food_id`) REFERENCES `food` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食品别名（FR-002 搜索范围：中文别名、英文名称）';

DROP TABLE IF EXISTS `food_image`;
CREATE TABLE `food_image` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `food_id`       BIGINT       NOT NULL,
  -- 可挂到具体形态；为 NULL 表示该图片仅展示食品本身，不代表某一形态的营养数据
  `variant_id`    BIGINT           NULL,
  `url`           VARCHAR(512) NOT NULL COMMENT '图片地址（本地磁盘或对象存储）',
  `thumb_url`     VARCHAR(512)     NULL COMMENT '缩略图地址',
  `image_type`    VARCHAR(24)  NOT NULL DEFAULT 'RAW_MATERIAL'
                  COMMENT 'RAW_MATERIAL 新鲜原材料 / CUT 切开处理 / COOKED 烹饪成品 / VARIETY 不同品种 / RECIPE 食谱',
  `caption`       VARCHAR(255)     NULL COMMENT '图片描述',
  `source_note`   VARCHAR(255)     NULL COMMENT '图片来源说明（PRD 11.2 必须保存来源）',
  `license_note`  VARCHAR(255)     NULL COMMENT '授权/使用条件说明（PRD 风险表：图片版权）',
  `is_cover`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否封面图',
  `sort_order`    INT          NOT NULL DEFAULT 0,
  `review_status` VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '图片同样需要审核',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`    DATETIME         NULL,
  PRIMARY KEY (`id`),
  KEY `idx_image_food`    (`food_id`, `sort_order`),
  KEY `idx_image_variant` (`variant_id`),
  CONSTRAINT `fk_image_food`    FOREIGN KEY (`food_id`)    REFERENCES `food` (`id`),
  CONSTRAINT `fk_image_variant` FOREIGN KEY (`variant_id`) REFERENCES `food_state_variant` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食品图片（FR-003 图片类型；菜品图与原材料图必须可区分）';

-- [D1] 食品形态：营养数据的最小承载单元
DROP TABLE IF EXISTS `food_state_variant`;
CREATE TABLE `food_state_variant` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `food_id`         BIGINT       NOT NULL,
  `state`           VARCHAR(16)  NOT NULL COMMENT 'RAW / COOKED / PEELED / PROCESSED / DRIED / FROZEN',
  `cooking_method`  VARCHAR(32)      NULL COMMENT '煮/蒸/炒/炸/烤，state=COOKED 时必填',
  `name_suffix`     VARCHAR(64)      NULL COMMENT '展示用后缀，如"（可食部）""（炒）"',
  `edible_ratio`    DECIMAL(5,4)     NULL COMMENT '可食部比例，如 0.94 表示 94% 可食（缺失时 NULL，不填 1.0）',
  `description`     VARCHAR(512)     NULL COMMENT '形态说明',
  `is_default`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '详情页默认选中形态',
  `review_status`   VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
  `publish_status`  VARCHAR(16)  NOT NULL DEFAULT 'UNPUBLISHED',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`      DATETIME         NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_variant_food_state` (`food_id`, `state`, `cooking_method`, `deleted_at`),
  KEY `idx_variant_food`    (`food_id`),
  KEY `idx_variant_publish` (`publish_status`, `review_status`, `deleted_at`),
  CONSTRAINT `fk_variant_food` FOREIGN KEY (`food_id`) REFERENCES `food` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食品形态 [D1]：生/熟/去皮/加工分别建档，营养数据只挂在此表上';

-- =============================================================================
-- 三、营养域
-- =============================================================================

DROP TABLE IF EXISTS `nutrient`;
CREATE TABLE `nutrient` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `code`           VARCHAR(32)  NOT NULL COMMENT 'ENERGY / PROTEIN / FAT / CARBOHYDRATE / ...',
  `name`           VARCHAR(64)  NOT NULL COMMENT '中文名，如 蛋白质',
  `en_name`        VARCHAR(64)      NULL,
  `unit`           VARCHAR(16)  NOT NULL COMMENT '标准单位 g / mg / ug / kcal / kJ',
  `base_amount`    DECIMAL(18,6) NOT NULL DEFAULT 100 COMMENT '单位换算基准，通常为 100（每 100g）',
  `group_code`     VARCHAR(32)  NOT NULL DEFAULT 'MACRO'
                   COMMENT 'MACRO 宏量 / MINERAL 矿物质 / VITAMIN 维生素 / OTHER 其他',
  -- 前端营养表格的固定列顺序，避免每次查询后重排
  `display_order`  INT          NOT NULL DEFAULT 0,
  -- 换算系数：把 amount 从 unit 换算到 base_unit 的乘数（如 mg→g 为 0.001）
  `base_unit`      VARCHAR(16)  NOT NULL COMMENT '汇总基准单位，用于跨单位统一',
  `unit_factor`    DECIMAL(18,8) NOT NULL DEFAULT 1 COMMENT 'amount × unit_factor = base_unit 下的值',
  -- 数据库可能以 kJ 提供能量而前端展示 kcal，需显式声明，禁止隐式假设
  `is_energy`      TINYINT(1)   NOT NULL DEFAULT 0,
  `description`    VARCHAR(512)     NULL COMMENT '营养素的通俗解释（PRD FR-010 营养素基本知识）',
  `enabled`        TINYINT(1)   NOT NULL DEFAULT 1,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_nutrient_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营养素字典 [D3]：指标单位在此定义，扩展新指标只需插入数据（PRD 9.5）';

DROP TABLE IF EXISTS `data_source`;
CREATE TABLE `data_source` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `code`             VARCHAR(64)  NOT NULL,
  `name`             VARCHAR(255) NOT NULL COMMENT '如《中国食物成分表》第6版',
  `source_type`      VARCHAR(24)  NOT NULL DEFAULT 'DATABASE'
                     COMMENT 'DATABASE 成分数据库 / GUIDELINE 膳食指南 / PAPER 文献 / OFFICIAL 官方文件 / DEMO 演示数据',
  `publisher`        VARCHAR(255)     NULL COMMENT '发布机构',
  `version`          VARCHAR(64)      NULL COMMENT '版本/版次',
  `publish_date`     DATE             NULL COMMENT '发布日期（PRD 5.10 要求记录发布日期或版本）',
  `url`              VARCHAR(512)     NULL COMMENT '可核查来源地址',
  `applicable_scope` VARCHAR(512)     NULL COMMENT '适用范围（PRD 8.2：必须注明适用条件）',
  `note`             VARCHAR(512)     NULL,
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据来源 [D4]：营养数据可追溯率 100% 的基础';

-- [D2][D3][D4] 营养数值：三列表达缺失/零/有值，单位来自字典，来源非空
DROP TABLE IF EXISTS `nutrition_value`;
CREATE TABLE `nutrition_value` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `variant_id`     BIGINT        NOT NULL COMMENT '营养数据永远挂在形态上，不挂食品 [D1]',
  `nutrient_id`    BIGINT        NOT NULL,
  -- 参考重量口径：每多少克（或毫升）对应下列数值
  `reference_amount` DECIMAL(10,3) NOT NULL DEFAULT 100 COMMENT '参考重量，如 100 表示每 100g',
  `reference_unit`   VARCHAR(16)   NOT NULL DEFAULT 'g' COMMENT 'g / ml / 份',
  `reference_note`   VARCHAR(128)      NULL COMMENT '口径补充，如"每100g可食部"',
  `value_status`   VARCHAR(16)   NOT NULL DEFAULT 'MISSING' COMMENT 'PRESENT / ZERO / MISSING [D2]',
  `amount`         DECIMAL(18,6)     NULL COMMENT 'PRESENT/ZERO 时非空；MISSING 时为 NULL',
  `source_id`      BIGINT        NOT NULL COMMENT '来源可追溯率达 100% 的前提 [D4]',
  `source_detail`  VARCHAR(255)      NULL COMMENT '来源中的具体表/页码定位',
  `review_status`  VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
  `created_by`     BIGINT            NULL,
  `updated_by`     BIGINT            NULL,
  `reviewed_by`    BIGINT            NULL,
  `reviewed_at`    DATETIME          NULL,
  `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`     DATETIME          NULL,
  PRIMARY KEY (`id`),
  -- 同一形态同一营养素同一口径只允许一条有效记录，避免汇总时重复计算
  UNIQUE KEY `uk_nv_variant_nutrient` (`variant_id`, `nutrient_id`, `reference_amount`, `reference_unit`, `deleted_at`),
  KEY `idx_nv_variant` (`variant_id`),
  KEY `idx_nv_nutrient` (`nutrient_id`),
  KEY `idx_nv_source`   (`source_id`),
  -- 数据库层强制三列的一致性，防止应用层漏改导致"零值被当成缺失"或反之
  CONSTRAINT `ck_nv_status_amount` CHECK (
    (`value_status` = 'MISSING' AND `amount` IS NULL)
    OR (`value_status` = 'ZERO'    AND `amount` = 0)
    OR (`value_status` = 'PRESENT' AND `amount` IS NOT NULL)
  ),
  CONSTRAINT `fk_nv_variant`  FOREIGN KEY (`variant_id`)  REFERENCES `food_state_variant` (`id`),
  CONSTRAINT `fk_nv_nutrient` FOREIGN KEY (`nutrient_id`) REFERENCES `nutrient` (`id`),
  CONSTRAINT `fk_nv_source`   FOREIGN KEY (`source_id`)   REFERENCES `data_source` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营养数值 [D2][D3][D4]：缺失/零/有值三态，单位归一，来源必填';

-- =============================================================================
-- 四、知识库（RAG）
-- =============================================================================

DROP TABLE IF EXISTS `knowledge_doc`;
CREATE TABLE `knowledge_doc` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `title`            VARCHAR(255) NOT NULL,
  `category`         VARCHAR(32)  NOT NULL DEFAULT 'GENERAL'
                     COMMENT 'NUTRIENT 营养素 / FOOD 食品营养 / GUIDELINE 膳食指南 / COOKING 加工烹饪 / FAQ 常见问题 / SAFETY 食品安全',
  `summary`          VARCHAR(512)     NULL,
  `source_id`        BIGINT           NULL COMMENT '原始来源',
  `source_excerpt`   VARCHAR(512)     NULL COMMENT '原文出处定位',
  -- 适用范围直接决定回答边界（PRD 8.2：不同人群目标值不同）
  `applicable_scope` VARCHAR(512)     NULL COMMENT '适用人群/条件，如"成人一般人群"',
  -- 语义检索切换后写入模型标识，便于复现（扩展点，第一版为 NULL）
  `embedding_model`  VARCHAR(64)      NULL,
  `review_status`    VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
  `publish_status`   VARCHAR(16)  NOT NULL DEFAULT 'UNPUBLISHED',
  `published_at`     DATETIME         NULL,
  `valid_until`      DATETIME         NULL COMMENT '过期时间，超期内容不应再被引用（PRD 风险表）',
  `created_by`       BIGINT           NULL,
  `updated_by`       BIGINT           NULL,
  `reviewed_by`      BIGINT           NULL,
  `reviewed_at`      DATETIME         NULL,
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`       DATETIME         NULL,
  PRIMARY KEY (`id`),
  KEY `idx_kdoc_publish`  (`publish_status`, `review_status`, `deleted_at`),
  KEY `idx_kdoc_category` (`category`),
  CONSTRAINT `fk_kdoc_source` FOREIGN KEY (`source_id`) REFERENCES `data_source` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营养知识文档（FR-010）';

DROP TABLE IF EXISTS `knowledge_chunk`;
CREATE TABLE `knowledge_chunk` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `doc_id`       BIGINT       NOT NULL,
  `chunk_index`  INT          NOT NULL DEFAULT 0 COMMENT '文档内顺序，保证拼接上下文时序正确',
  `heading`      VARCHAR(255)     NULL COMMENT '所属小节标题，用于引用时展示',
  `content`      TEXT         NOT NULL COMMENT '分段正文',
  `token_count`  INT              NULL COMMENT '估算 token 数，用于拼装预算控制',
  -- 第一版关键词检索用；升级语义检索后作为混合检索的召回一路
  `keywords`     VARCHAR(512)     NULL COMMENT '抽取的关键词，去空格分隔',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chunk_doc_index` (`doc_id`, `chunk_index`),
  KEY `idx_chunk_doc` (`doc_id`),
  CONSTRAINT `fk_chunk_doc` FOREIGN KEY (`doc_id`) REFERENCES `knowledge_doc` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识分块 [索引单元]：检索与引用均以 chunk 为最小粒度';

-- =============================================================================
-- 五、对话与 Agent
-- =============================================================================

DROP TABLE IF EXISTS `chat_session`;
CREATE TABLE `chat_session` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT           NULL COMMENT 'NULL = 未登录游客会话（PRD 角色A 有限次试用）',
  `guest_key`       VARCHAR(64)      NULL COMMENT '游客标识，用于限制试用次数',
  `title`           VARCHAR(128)     NULL COMMENT '会话标题，取首条提问摘要',
  `last_message_at` DATETIME         NULL COMMENT '历史对话列表按此排序（FR-008）',
  `message_count`   INT          NOT NULL DEFAULT 0,
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`      DATETIME         NULL,
  PRIMARY KEY (`id`),
  KEY `idx_session_user`  (`user_id`, `last_message_at`),
  KEY `idx_session_guest` (`guest_key`),
  CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答会话（FR-005 多轮对话 / FR-008 历史记录）';

DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message` (
  `id`               BIGINT      NOT NULL AUTO_INCREMENT,
  `session_id`       BIGINT      NOT NULL,
  `seq`              INT         NOT NULL COMMENT '会话内序号，替代仅靠时间戳排序（同秒并发会乱序）',
  `role`             VARCHAR(16) NOT NULL COMMENT 'USER / ASSISTANT / SYSTEM / TOOL',
  `content`          TEXT            NULL COMMENT '自然语言内容（对应 Agent 输出的 answer 字段）',
  -- Agent 结构化输出整体存 JSON：answer/foodReferences/nutritionFacts/
  -- recommendations/warnings/sources/uncertainty（PRD 6.4）
  -- 存 JSON 而非拆表的原因：字段按问题类型选择性返回，强结构化会产生大量 NULL 列
  `structured_output` JSON           NULL,
  `intent`           VARCHAR(32)     NULL COMMENT '识别出的意图，用于评估与统计（PRD 2.2 工具调用成功率）',
  `tool_calls`       JSON            NULL COMMENT '工具调用轨迹，用于可解释性与回归测试',
  `model_name`       VARCHAR(64)     NULL COMMENT '实际使用的模型标识',
  `latency_ms`       INT             NULL,
  `token_usage`      INT             NULL,
  `status`           VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / FAILED / TIMEOUT / DEGRADED',
  `error_code`       VARCHAR(64)     NULL COMMENT '失败时的错误码（9 类异常之一）',
  `created_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_session_seq` (`session_id`, `seq`),
  KEY `idx_msg_session` (`session_id`, `created_at`),
  CONSTRAINT `fk_msg_session` FOREIGN KEY (`session_id`) REFERENCES `chat_session` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息（PRD 6.4 结构化输出 / 6.5 异常处理记录）';

-- =============================================================================
-- 六、图片识别
-- =============================================================================

DROP TABLE IF EXISTS `image_recognition`;
CREATE TABLE `image_recognition` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`          BIGINT           NULL,
  `session_id`       BIGINT           NULL COMMENT '识别后继续追问时关联会话',
  `storage_path`     VARCHAR(512) NOT NULL COMMENT '存储路径',
  `original_name`    VARCHAR(255)     NULL,
  `mime_type`        VARCHAR(64)  NOT NULL,
  `file_size`        BIGINT       NOT NULL COMMENT '字节',
  `file_hash`        VARCHAR(64)      NULL COMMENT 'SHA-256，用于去重与审计',
  -- 视觉模型只产出候选，不产出任何营养数值（PRD 5.6 业务规则）
  `candidates`       JSON             NULL COMMENT '候选列表 [{foodId, foodName, confidence, reason}]',
  `confirmed_food_id` BIGINT          NULL COMMENT '用户确认的食品（PRD 5.6 流程第 7 步）',
  `status`           VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                     COMMENT 'PENDING / SUCCESS / FAILED / REJECTED / NEED_MANUAL_SEARCH',
  `failure_reason`   VARCHAR(255)     NULL COMMENT '失败原因，如 格式不支持/超过大小限制',
  `model_name`       VARCHAR(64)      NULL,
  `latency_ms`       INT              NULL,
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_recog_user`   (`user_id`, `created_at`),
  KEY `idx_recog_status` (`status`),
  CONSTRAINT `fk_recog_user` FOREIGN KEY (`user_id`)          REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_recog_food` FOREIGN KEY (`confirmed_food_id`) REFERENCES `food` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片识别记录（FR-006，含状态/耗时/错误信息）';

-- =============================================================================
-- 七、用户行为
-- =============================================================================

DROP TABLE IF EXISTS `user_favorite`;
CREATE TABLE `user_favorite` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT   NOT NULL,
  `food_id`    BIGINT   NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` DATETIME     NULL,
  PRIMARY KEY (`id`),
  -- 含 deleted_at：允许取消收藏后重新收藏
  UNIQUE KEY `uk_fav_user_food` (`user_id`, `food_id`, `deleted_at`),
  KEY `idx_fav_user` (`user_id`, `created_at`),
  CONSTRAINT `fk_fav_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_fav_food` FOREIGN KEY (`food_id`) REFERENCES `food` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食品收藏（FR-008）';

-- =============================================================================
-- 八、审计
-- =============================================================================

DROP TABLE IF EXISTS `admin_audit_log`;
CREATE TABLE `admin_audit_log` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `operator_id`   BIGINT           NULL,
  `operator_name` VARCHAR(64)      NULL COMMENT '冗余用户名，用户删除后审计仍可读',
  `action`        VARCHAR(32)  NOT NULL COMMENT 'CREATE / UPDATE / DELETE / PUBLISH / OFFLINE / REVIEW / REINDEX / LOGIN',
  `target_type`   VARCHAR(32)  NOT NULL COMMENT 'FOOD / VARIANT / NUTRITION / KNOWLEDGE / IMAGE / USER / CONFIG',
  `target_id`     BIGINT           NULL,
  `before_json`   JSON             NULL COMMENT '变更前快照（PRD 2.2 / 9.3 要求可追溯修改前后）',
  `after_json`    JSON             NULL COMMENT '变更后快照',
  `ip`            VARCHAR(64)      NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_target`   (`target_type`, `target_id`),
  KEY `idx_audit_operator` (`operator_id`, `created_at`),
  CONSTRAINT `fk_audit_operator` FOREIGN KEY (`operator_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台操作审计（PRD 9.3）';

-- =============================================================================
-- 九、操作日志（异常与调用统计，PRD 3.1 角色D）
-- =============================================================================

DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `trace_id`    VARCHAR(64)      NULL COMMENT '链路追踪 ID',
  `user_id`     BIGINT           NULL,
  `module`      VARCHAR(32)  NOT NULL COMMENT 'FOOD / NUTRITION / AGENT / RECOGNITION / AUTH / ADMIN',
  `action`      VARCHAR(64)  NOT NULL,
  `level`       VARCHAR(16)  NOT NULL DEFAULT 'INFO' COMMENT 'INFO / WARN / ERROR',
  `success`     TINYINT(1)   NOT NULL DEFAULT 1,
  `error_code`  VARCHAR(64)      NULL,
  -- 注意：敏感信息（对话正文、个人资料、图片路径）禁止写入此处（PRD 8.4 限制日志记录）
  `message`     VARCHAR(1024)    NULL,
  `duration_ms` INT              NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_oplog_module` (`module`, `created_at`),
  KEY `idx_oplog_level`  (`level`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志（限流、错误统计、模型调用统计）';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 附：表名与职责速查
-- =============================================================================
-- sys_user                  用户账户
-- sys_role / sys_user_role  角色与关联（四类角色权限可区分）
-- sys_auth_token            可撤销登录令牌
-- user_profile              用户资料与偏好（全部可空）
-- food_category             食品分类（支持多级）
-- food                      食品主表（只有事实信息，无营养数值）
-- food_alias                别名（唯一约束防劫持标准名）
-- food_image                图片（原材料/切开/成品/品种/食谱，含来源与授权）
-- food_state_variant        食品形态 [D1] 营养数据的最小承载单元
-- nutrient                  营养素字典 [D3] 单位与换算在此定义
-- data_source               数据来源 [D4] 可追溯率 100% 的基础
-- nutrition_value           营养数值 [D2] 缺失/零/有值三态 + 来源必填
-- knowledge_doc             知识文档
-- knowledge_chunk           知识分块（检索与引用的最小粒度）
-- chat_session              问答会话（含游客会话）
-- chat_message              消息（结构化输出 JSON + 工具轨迹 + 异常状态）
-- image_recognition         图片识别记录（只存候选，不存营养数值）
-- user_favorite             收藏
-- admin_audit_log           后台审计（变更前后快照）
-- operation_log             操作日志（禁止写入敏感信息）
