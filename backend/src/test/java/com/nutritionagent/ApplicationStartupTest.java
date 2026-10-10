package com.nutritionagent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 启动自检。
 *
 * <p>验证三件事，缺一不可：
 * <ol>
 *   <li>Spring 上下文在 dev（H2）profile 下能完整装配；</li>
 *   <li>{@code db/h2/schema.sql} 能被真正执行，21 张表全部建成；</li>
 *   <li>{@code db/h2/data.sql} 能写入，且营养数据同时存在
 *       PRESENT / ZERO / MISSING 三种状态——这是三态模型可用的最低证据。</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("dev")
class ApplicationStartupTest {

    /** 与 docs/db/schema.sql 的表数量一致，少一张就说明 schema 副本漂移了。 */
    private static final int EXPECTED_TABLE_COUNT = 21;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
        assertThat(jdbcTemplate).isNotNull();
    }

    @Test
    void schemaCreatesAllTables() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema NOT IN ('INFORMATION_SCHEMA', 'information_schema', 'PG_CATALOG', 'pg_catalog')",
                Integer.class);
        assertThat(count).as("H2 中建表数量").isEqualTo(EXPECTED_TABLE_COUNT);
    }

    @Test
    void seedDataCoversAllThreeValueStatuses() {
        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT DISTINCT value_status FROM nutrition_value ORDER BY value_status", String.class);
        assertThat(statuses)
                .as("营养数据必须同时覆盖 MISSING / PRESENT / ZERO，否则三态模型无法被验证")
                .containsExactly("MISSING", "PRESENT", "ZERO");
    }

    @Test
    void missingValuesNeverCarryZeroAmount() {
        Integer broken = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nutrition_value WHERE value_status = 'MISSING' AND amount IS NOT NULL",
                Integer.class);
        assertThat(broken).as("MISSING 行的 amount 必须为 NULL，绝不能用 0 兜底").isZero();
    }

    @Test
    void everyNutritionValueHasASource() {
        Integer orphans = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM nutrition_value nv LEFT JOIN data_source ds ON ds.id = nv.source_id "
                        + "WHERE ds.id IS NULL",
                Integer.class);
        assertThat(orphans).as("来源可追溯率必须为 100%").isZero();
    }
}
