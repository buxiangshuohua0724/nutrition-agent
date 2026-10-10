-- =============================================================================
-- H2 schema（dev / test profile 专用）
-- =============================================================================
-- 权威结构见 docs/db/schema.sql（MySQL 8.0）。本文件是它的等价副本，
-- 差异仅限 MySQL 专有语法：
--   1) 去掉 ENGINE / DEFAULT CHARSET / SET NAMES / SET FOREIGN_KEY_CHECKS
--   2) KEY idx_x (..) 改为独立的 CREATE INDEX 语句（列名、列序、唯一性完全一致）
--   3) JSON 列改为 VARCHAR(4000)：H2 的 JSON 类型要求值必须是合法 JSON 字面量，
--      而 MySQL 的 JSON 列可接受任意字符串。为让 dev 也能写入非 JSON 调试数据，
--      这里放宽容器的类型，只放宽类型、不改变列名与可空性。
--   4) `ON UPDATE CURRENT_TIMESTAMP` 去掉：H2 不支持列级自动更新，改由应用层
--      统一在 Service 里维护 updated_at（MySQL 侧仍保留该能力，语义等价）。
--
-- 两份 schema 的结构一致性由 SchemaParityTest 自动校验（表名、列名、可空性、
-- 唯一约束涉及的列集合）。任何一侧改动而另一侧未同步，测试会失败。
-- =============================================================================

-- 先删表，关闭引用完整性检查以免受外键顺序约束
SET REFERENTIAL_INTEGRITY FALSE;
DROP TABLE IF EXISTS `sys_user`;
DROP TABLE IF EXISTS `sys_role`;
DROP TABLE IF EXISTS `sys_user_role`;
DROP TABLE IF EXISTS `sys_auth_token`;
DROP TABLE IF EXISTS `user_profile`;
DROP TABLE IF EXISTS `food_category`;
DROP TABLE IF EXISTS `food`;
DROP TABLE IF EXISTS `food_alias`;
DROP TABLE IF EXISTS `food_image`;
DROP TABLE IF EXISTS `food_state_variant`;
DROP TABLE IF EXISTS `nutrient`;
DROP TABLE IF EXISTS `data_source`;
DROP TABLE IF EXISTS `nutrition_value`;
DROP TABLE IF EXISTS `knowledge_doc`;
DROP TABLE IF EXISTS `knowledge_chunk`;
DROP TABLE IF EXISTS `chat_session`;
DROP TABLE IF EXISTS `chat_message`;
DROP TABLE IF EXISTS `image_recognition`;
DROP TABLE IF EXISTS `user_favorite`;
DROP TABLE IF EXISTS `admin_audit_log`;
DROP TABLE IF EXISTS `operation_log`;
SET REFERENTIAL_INTEGRITY TRUE;

-- =============================================================================
-- 一、用户与权限
-- =============================================================================

CREATE TABLE `sys_user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `username`      VARCHAR(64)  NOT NULL,
  `email`         VARCHAR(128)     NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `nickname`      VARCHAR(64)      NULL,
  `avatar_url`    VARCHAR(512)     NULL,
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  `last_login_at` TIMESTAMP        NULL,
  `created_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at`    TIMESTAMP        NULL,
  `gd_deleted_key` TIMESTAMP AS (IFNULL(`deleted_at`, TIMESTAMP '1970-01-01 00:00:00')),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_user_username` UNIQUE (`username`, `deleted_at`, `gd_deleted_key`),
  CONSTRAINT `uk_user_email`    UNIQUE (`email`,    `deleted_at`, `gd_deleted_key`)
);

CREATE TABLE `sys_role` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `code`        VARCHAR(32) NOT NULL,
  `name`        VARCHAR(64) NOT NULL,
  `description` VARCHAR(255)    NULL,
  `created_at`  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_role_code` UNIQUE (`code`)
);

CREATE TABLE `sys_user_role` (
  `user_id`    BIGINT    NOT NULL,
  `role_id`    BIGINT    NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`, `role_id`),
  CONSTRAINT `fk_ur_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_ur_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`)
);
CREATE INDEX `idx_ur_role` ON `sys_user_role` (`role_id`);

CREATE TABLE `sys_auth_token` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT       NOT NULL,
  `token_hash` VARCHAR(128) NOT NULL,
  `issued_at`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` TIMESTAMP    NOT NULL,
  `revoked_at` TIMESTAMP        NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_token_hash` UNIQUE (`token_hash`),
  CONSTRAINT `fk_token_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
);
CREATE INDEX `idx_token_user` ON `sys_auth_token` (`user_id`);

CREATE TABLE `user_profile` (
  `id`               BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`          BIGINT      NOT NULL,
  `age_range`        VARCHAR(16)     NULL,
  `gender`           VARCHAR(16)     NULL,
  `height_cm`        DECIMAL(5,1)    NULL,
  `weight_kg`        DECIMAL(5,1)    NULL,
  `activity_level`   VARCHAR(24)     NULL,
  `diet_goal`        VARCHAR(32)     NULL,
  `allergens`        VARCHAR(4000)   NULL,
  `diet_preferences` VARCHAR(4000)   NULL,
  `consent_at`       TIMESTAMP       NULL,
  `created_at`       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_profile_user` UNIQUE (`user_id`),
  CONSTRAINT `fk_profile_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
);

-- =============================================================================
-- 二、食品域
-- =============================================================================

CREATE TABLE `food_category` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `parent_id`  BIGINT           NULL,
  `slug`       VARCHAR(64)  NOT NULL,
  `name`       VARCHAR(64)  NOT NULL,
  `icon`       VARCHAR(64)      NULL,
  `intro`      VARCHAR(512)     NULL,
  `sort_order` INT          NOT NULL DEFAULT 0,
  `enabled`    TINYINT      NOT NULL DEFAULT 1,
  `created_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` TIMESTAMP        NULL,
  `gd_deleted_key` TIMESTAMP AS (IFNULL(`deleted_at`, TIMESTAMP '1970-01-01 00:00:00')),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_category_slug`  UNIQUE (`slug`, `deleted_at`, `gd_deleted_key`),
  CONSTRAINT `fk_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `food_category` (`id`)
);
CREATE INDEX `idx_category_parent` ON `food_category` (`parent_id`);

CREATE TABLE `food` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `code`            VARCHAR(64)  NOT NULL,
  `category_id`     BIGINT           NULL,
  `name`            VARCHAR(128) NOT NULL,
  `en_name`         VARCHAR(128)     NULL,
  `scientific_name` VARCHAR(128)     NULL,
  `intro`           TEXT             NULL,
  `common_usage`    TEXT             NULL,
  `origin`          VARCHAR(255)     NULL,
  `storage_method`  VARCHAR(255)     NULL,
  `suitable_for`    TEXT             NULL,
  `cautions`        TEXT             NULL,
  `search_keywords` VARCHAR(512)     NULL,
  `review_status`   VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
  `publish_status`  VARCHAR(16)  NOT NULL DEFAULT 'UNPUBLISHED',
  `published_at`    TIMESTAMP        NULL,
  `created_by`      BIGINT           NULL,
  `updated_by`      BIGINT           NULL,
  `reviewed_by`     BIGINT           NULL,
  `reviewed_at`     TIMESTAMP        NULL,
  `created_at`      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at`      TIMESTAMP        NULL,
  `gd_deleted_key` TIMESTAMP AS (IFNULL(`deleted_at`, TIMESTAMP '1970-01-01 00:00:00')),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_food_code`      UNIQUE (`code`, `deleted_at`, `gd_deleted_key`),
  CONSTRAINT `fk_food_category`  FOREIGN KEY (`category_id`) REFERENCES `food_category` (`id`)
);
CREATE INDEX `idx_food_name`     ON `food` (`name`);
CREATE INDEX `idx_food_category` ON `food` (`category_id`);
CREATE INDEX `idx_food_publish`  ON `food` (`publish_status`, `review_status`, `deleted_at`);

CREATE TABLE `food_alias` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `food_id`    BIGINT       NOT NULL,
  `alias`      VARCHAR(128) NOT NULL,
  `alias_type` VARCHAR(24)  NOT NULL DEFAULT 'COLLOQUIAL',
  `weight`     INT          NOT NULL DEFAULT 0,
  `created_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` TIMESTAMP        NULL,
  `gd_deleted_key` TIMESTAMP AS (IFNULL(`deleted_at`, TIMESTAMP '1970-01-01 00:00:00')),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_alias_food` UNIQUE (`food_id`, `alias`, `deleted_at`, `gd_deleted_key`),
  CONSTRAINT `fk_alias_food` FOREIGN KEY (`food_id`) REFERENCES `food` (`id`)
);
CREATE INDEX `idx_alias_alias` ON `food_alias` (`alias`);

-- [D1] 食品形态：营养数据的最小承载单元
CREATE TABLE `food_state_variant` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT,
  `food_id`        BIGINT      NOT NULL,
  `state`          VARCHAR(16) NOT NULL,
  `cooking_method` VARCHAR(32)     NULL,
  `name_suffix`    VARCHAR(64)     NULL,
  `edible_ratio`   DECIMAL(5,4)    NULL,
  `description`    VARCHAR(512)    NULL,
  `is_default`     TINYINT     NOT NULL DEFAULT 0,
  `review_status`  VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  `publish_status` VARCHAR(16) NOT NULL DEFAULT 'UNPUBLISHED',
  `created_at`     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at`     TIMESTAMP       NULL,
  `gd_deleted_key` TIMESTAMP AS (IFNULL(`deleted_at`, TIMESTAMP '1970-01-01 00:00:00')),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_variant_food_state` UNIQUE (`food_id`, `state`, `cooking_method`, `deleted_at`, `gd_deleted_key`),
  CONSTRAINT `fk_variant_food`       FOREIGN KEY (`food_id`) REFERENCES `food` (`id`)
);
CREATE INDEX `idx_variant_food`    ON `food_state_variant` (`food_id`);
CREATE INDEX `idx_variant_publish` ON `food_state_variant` (`publish_status`, `review_status`, `deleted_at`);

CREATE TABLE `food_image` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `food_id`       BIGINT       NOT NULL,
  `variant_id`    BIGINT           NULL,
  `url`           VARCHAR(512) NOT NULL,
  `thumb_url`     VARCHAR(512)     NULL,
  `image_type`    VARCHAR(24)  NOT NULL DEFAULT 'RAW_MATERIAL',
  `caption`       VARCHAR(255)     NULL,
  `source_note`   VARCHAR(255)     NULL,
  `license_note`  VARCHAR(255)     NULL,
  `is_cover`      TINYINT      NOT NULL DEFAULT 0,
  `sort_order`    INT          NOT NULL DEFAULT 0,
  `review_status` VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
  `created_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at`    TIMESTAMP        NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_image_food`    FOREIGN KEY (`food_id`)    REFERENCES `food` (`id`),
  CONSTRAINT `fk_image_variant` FOREIGN KEY (`variant_id`) REFERENCES `food_state_variant` (`id`)
);
CREATE INDEX `idx_image_food`    ON `food_image` (`food_id`, `sort_order`);
CREATE INDEX `idx_image_variant` ON `food_image` (`variant_id`);

-- =============================================================================
-- 三、营养域
-- =============================================================================

CREATE TABLE `nutrient` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT,
  `code`          VARCHAR(32)   NOT NULL,
  `name`          VARCHAR(64)   NOT NULL,
  `en_name`       VARCHAR(64)       NULL,
  `unit`          VARCHAR(16)   NOT NULL,
  `base_amount`   DECIMAL(18,6) NOT NULL DEFAULT 100,
  `group_code`    VARCHAR(32)   NOT NULL DEFAULT 'MACRO',
  `display_order` INT           NOT NULL DEFAULT 0,
  `base_unit`     VARCHAR(16)   NOT NULL,
  `unit_factor`   DECIMAL(18,8) NOT NULL DEFAULT 1,
  `is_energy`     TINYINT       NOT NULL DEFAULT 0,
  `description`   VARCHAR(512)      NULL,
  `enabled`       TINYINT       NOT NULL DEFAULT 1,
  `created_at`    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_nutrient_code` UNIQUE (`code`)
);

CREATE TABLE `data_source` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `code`             VARCHAR(64)  NOT NULL,
  `name`             VARCHAR(255) NOT NULL,
  `source_type`      VARCHAR(24)  NOT NULL DEFAULT 'DATABASE',
  `publisher`        VARCHAR(255)     NULL,
  `version`          VARCHAR(64)      NULL,
  `publish_date`     DATE             NULL,
  `url`              VARCHAR(512)     NULL,
  `applicable_scope` VARCHAR(512)     NULL,
  `note`             VARCHAR(512)     NULL,
  `created_at`       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_source_code` UNIQUE (`code`),
  -- [D4] 与 MySQL 侧 ck_source_type 同域；H2 2.x MODE=MySQL 支持 CHECK 子查询一致性由 SchemaParityTest 校验
  CONSTRAINT `ck_source_type` CHECK (`source_type` IN
    ('OFFICIAL','DATABASE','GUIDELINE','LITERATURE','STANDARD','COMMERCIAL','OTHER','DEMO','CUSTOM'))
);

-- [D2][D3][D4] 营养数值：三态 + 单位归一 + 来源必填
CREATE TABLE `nutrition_value` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `variant_id`       BIGINT        NOT NULL,
  `nutrient_id`      BIGINT        NOT NULL,
  `reference_amount` DECIMAL(10,3) NOT NULL DEFAULT 100,
  `reference_unit`   VARCHAR(16)   NOT NULL DEFAULT 'g',
  `reference_note`   VARCHAR(128)      NULL,
  `value_status`     VARCHAR(16)   NOT NULL DEFAULT 'MISSING',
  `amount`           DECIMAL(18,6)     NULL,
  `source_id`        BIGINT        NOT NULL,
  `source_detail`    VARCHAR(255)      NULL,
  `review_status`    VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
  `created_by`       BIGINT            NULL,
  `updated_by`       BIGINT            NULL,
  `reviewed_by`      BIGINT            NULL,
  `reviewed_at`      TIMESTAMP         NULL,
  `created_at`       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at`       TIMESTAMP         NULL,
  `gd_deleted_key` TIMESTAMP AS (IFNULL(`deleted_at`, TIMESTAMP '1970-01-01 00:00:00')),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_nv_variant_nutrient` UNIQUE (`variant_id`, `nutrient_id`, `reference_amount`, `reference_unit`, `deleted_at`, `gd_deleted_key`),
  -- 数据库层强制三列一致，防止应用层漏改导致「零值被当成缺失」或反之
  CONSTRAINT `ck_nv_status_amount` CHECK (
    (`value_status` = 'MISSING' AND `amount` IS NULL)
    OR (`value_status` = 'ZERO'    AND `amount` = 0)
    OR (`value_status` = 'PRESENT' AND `amount` IS NOT NULL)
  ),
  CONSTRAINT `fk_nv_variant`  FOREIGN KEY (`variant_id`)  REFERENCES `food_state_variant` (`id`),
  CONSTRAINT `fk_nv_nutrient` FOREIGN KEY (`nutrient_id`) REFERENCES `nutrient` (`id`),
  CONSTRAINT `fk_nv_source`   FOREIGN KEY (`source_id`)   REFERENCES `data_source` (`id`)
);
CREATE INDEX `idx_nv_variant` ON `nutrition_value` (`variant_id`);
CREATE INDEX `idx_nv_nutrient` ON `nutrition_value` (`nutrient_id`);
CREATE INDEX `idx_nv_source`  ON `nutrition_value` (`source_id`);

-- =============================================================================
-- 四、知识库（RAG）
-- =============================================================================

CREATE TABLE `knowledge_doc` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `title`            VARCHAR(255) NOT NULL,
  `category`         VARCHAR(32)  NOT NULL DEFAULT 'GENERAL',
  `summary`          VARCHAR(512)     NULL,
  `source_id`        BIGINT           NULL,
  `source_excerpt`   VARCHAR(512)     NULL,
  `applicable_scope` VARCHAR(512)     NULL,
  `embedding_model`  VARCHAR(64)      NULL,
  `review_status`    VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
  `publish_status`   VARCHAR(16)  NOT NULL DEFAULT 'UNPUBLISHED',
  `published_at`     TIMESTAMP        NULL,
  `valid_until`      TIMESTAMP        NULL,
  `created_by`       BIGINT           NULL,
  `updated_by`       BIGINT           NULL,
  `reviewed_by`      BIGINT           NULL,
  `reviewed_at`      TIMESTAMP        NULL,
  `created_at`       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at`       TIMESTAMP        NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_kdoc_source` FOREIGN KEY (`source_id`) REFERENCES `data_source` (`id`)
);
CREATE INDEX `idx_kdoc_publish`  ON `knowledge_doc` (`publish_status`, `review_status`, `deleted_at`);
CREATE INDEX `idx_kdoc_category` ON `knowledge_doc` (`category`);

CREATE TABLE `knowledge_chunk` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `doc_id`      BIGINT       NOT NULL,
  `chunk_index` INT          NOT NULL DEFAULT 0,
  `heading`     VARCHAR(255)     NULL,
  `content`     TEXT         NOT NULL,
  `token_count` INT              NULL,
  `keywords`    VARCHAR(512)     NULL,
  `created_at`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_chunk_doc_index` UNIQUE (`doc_id`, `chunk_index`),
  CONSTRAINT `fk_chunk_doc` FOREIGN KEY (`doc_id`) REFERENCES `knowledge_doc` (`id`)
);
CREATE INDEX `idx_chunk_doc` ON `knowledge_chunk` (`doc_id`);

-- =============================================================================
-- 五、对话与 Agent
-- =============================================================================

CREATE TABLE `chat_session` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT           NULL,
  `guest_key`       VARCHAR(64)      NULL,
  `title`           VARCHAR(128)     NULL,
  `last_message_at` TIMESTAMP        NULL,
  `message_count`   INT          NOT NULL DEFAULT 0,
  `created_at`      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at`      TIMESTAMP        NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
);
CREATE INDEX `idx_session_user`  ON `chat_session` (`user_id`, `last_message_at`);
CREATE INDEX `idx_session_guest` ON `chat_session` (`guest_key`);

CREATE TABLE `chat_message` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT,
  `session_id`        BIGINT      NOT NULL,
  `seq`               INT         NOT NULL,
  `role`              VARCHAR(16) NOT NULL,
  `content`           TEXT            NULL,
  `structured_output` VARCHAR(4000)   NULL,
  `intent`            VARCHAR(32)     NULL,
  `tool_calls`        VARCHAR(4000)   NULL,
  `model_name`        VARCHAR(64)     NULL,
  `latency_ms`        INT             NULL,
  `token_usage`       INT             NULL,
  `status`            VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
  `error_code`        VARCHAR(64)     NULL,
  `created_at`        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_msg_session_seq` UNIQUE (`session_id`, `seq`),
  CONSTRAINT `fk_msg_session` FOREIGN KEY (`session_id`) REFERENCES `chat_session` (`id`)
);
CREATE INDEX `idx_msg_session` ON `chat_message` (`session_id`, `created_at`);

-- =============================================================================
-- 六、图片识别
-- =============================================================================

CREATE TABLE `image_recognition` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`           BIGINT           NULL,
  `session_id`        BIGINT           NULL,
  `storage_path`      VARCHAR(512) NOT NULL,
  `original_name`     VARCHAR(255)     NULL,
  `mime_type`         VARCHAR(64)  NOT NULL,
  `file_size`         BIGINT       NOT NULL,
  `file_hash`         VARCHAR(64)      NULL,
  `candidates`        VARCHAR(4000)    NULL,
  `confirmed_food_id` BIGINT           NULL,
  `status`            VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
  `failure_reason`    VARCHAR(255)     NULL,
  `model_name`        VARCHAR(64)      NULL,
  `latency_ms`        INT              NULL,
  `created_at`        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_recog_user` FOREIGN KEY (`user_id`)           REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_recog_food` FOREIGN KEY (`confirmed_food_id`) REFERENCES `food` (`id`)
);
CREATE INDEX `idx_recog_user`   ON `image_recognition` (`user_id`, `created_at`);
CREATE INDEX `idx_recog_status` ON `image_recognition` (`status`);

-- =============================================================================
-- 七、用户行为
-- =============================================================================

CREATE TABLE `user_favorite` (
  `id`         BIGINT    NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT    NOT NULL,
  `food_id`    BIGINT    NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` TIMESTAMP     NULL,
  `gd_deleted_key` TIMESTAMP AS (IFNULL(`deleted_at`, TIMESTAMP '1970-01-01 00:00:00')),
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_fav_user_food` UNIQUE (`user_id`, `food_id`, `deleted_at`, `gd_deleted_key`),
  CONSTRAINT `fk_fav_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_fav_food` FOREIGN KEY (`food_id`) REFERENCES `food` (`id`)
);
CREATE INDEX `idx_fav_user` ON `user_favorite` (`user_id`, `created_at`);

-- =============================================================================
-- 八、审计
-- =============================================================================

CREATE TABLE `admin_audit_log` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `operator_id`   BIGINT           NULL,
  `operator_name` VARCHAR(64)      NULL,
  `action`        VARCHAR(32)  NOT NULL,
  `target_type`   VARCHAR(32)  NOT NULL,
  `target_id`     BIGINT           NULL,
  `before_json`   VARCHAR(4000)    NULL,
  `after_json`    VARCHAR(4000)    NULL,
  `ip`            VARCHAR(64)      NULL,
  `created_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_audit_operator` FOREIGN KEY (`operator_id`) REFERENCES `sys_user` (`id`)
);
CREATE INDEX `idx_audit_target`   ON `admin_audit_log` (`target_type`, `target_id`);
CREATE INDEX `idx_audit_operator` ON `admin_audit_log` (`operator_id`, `created_at`);

-- =============================================================================
-- 九、操作日志
-- =============================================================================

CREATE TABLE `operation_log` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `trace_id`    VARCHAR(64)       NULL,
  `user_id`     BIGINT            NULL,
  `module`      VARCHAR(32)   NOT NULL,
  `action`      VARCHAR(64)   NOT NULL,
  `level`       VARCHAR(16)   NOT NULL DEFAULT 'INFO',
  `success`     TINYINT       NOT NULL DEFAULT 1,
  `error_code`  VARCHAR(64)       NULL,
  `message`     VARCHAR(1024)     NULL,
  `duration_ms` INT               NULL,
  `created_at`  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);
CREATE INDEX `idx_oplog_module` ON `operation_log` (`module`, `created_at`);
CREATE INDEX `idx_oplog_level`  ON `operation_log` (`level`, `created_at`);
