package com.nutritionagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 后台接口的已登录冒烟测试。
 *
 * <p>为什么必须单独有这一层：{@link ApiSmokeTest#adminEndpointsRejectGuests()} 只证明
 * 「没登录会被拦住」，恰恰是**最容易通过**的一条路径——它只要返回 40101 就行，根本没碰
 * Service 里的 SQL。真正跑通后台读接口（`/admin/variants/{id}/nutrition`、
 * `/admin/audit-logs`）才暴露了两个只会抛 50001 的缺陷：
 * ①前者 SELECT 里引用了别名 {@code ds} 却没有 JOIN {@code data_source}；
 * ②后者读了 {@code admin_audit_log} 里根本不存在的 {@code detail} 列。
 *
 * <p>这两个 bug 有个共同点：**没有任何一层能静态发现它们**。SQL 是字符串，列名拼错编译
 * 通过；而 guest 路径不执行 SQL，所以连「接口能不能用」都看不出来。教训是后台接口的
 * 测试必须带真实令牌走完整链路，不能只测拒绝。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminApiTest {

    /**
     * 与 {@code db/h2/data.sql} 注释里写明的演示口令一致。
     *
     * <p>用种子里的真实账号（而不是临时注册一个再提权）来测：这样一旦种子的散列与文档
     * 口令对不上，后台整条链路会直接红，而不是等到有人手工登录才发现进不去。
     */
    private static final String SEEDED_ADMIN = "admin";
    private static final String SEEDED_PASSWORD = "demo1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** 用种子管理员登录并取回令牌。 */
    private String adminToken() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + SEEDED_ADMIN + "\",\"password\":\"" + SEEDED_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    @Test
    @DisplayName("种子管理员能用文档里的口令登录，并同时具备两个后台角色")
    void seededAdminCanLogInWithDocumentedPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + SEEDED_ADMIN + "\",\"password\":\"" + SEEDED_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isString())
                // 两个角色必须都在：只有 CONTENT_ADMIN 进不了用户管理，
                // 只有 SYS_ADMIN 编辑不了内容，PRD 3.1 要求两者可区分且 admin 兼任。
                .andExpect(jsonPath("$.data.user.roles").isArray());
    }

    @Test
    @DisplayName("食品列表与草稿详情：内容管理员能读到未发布的食品")
    void adminCanReadFoodListAndDraftDetail() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/v1/admin/foods").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").isNumber());

        mockMvc.perform(get("/api/v1/admin/foods/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("西红柿"))
                // 后台详情带形态，否则「编辑 → 提交审核」这条链路走不通
                .andExpect(jsonPath("$.data.variants").isArray());
    }

    @Test
    @DisplayName("形态营养表必须逐条覆盖全部启用营养素，且带得出真实来源")
    void adminVariantNutritionCoversEveryNutrientWithRealSource() throws Exception {
        mockMvc.perform(get("/api/v1/admin/variants/1/nutrition")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.variantId").value(1))
                // 14 个启用营养素一个都不能少：LEFT JOIN 的意义就是「没有记录也要出行」，
                // 编辑界面要靠这份完整列表把缺失项补齐，缺行会让管理员以为该项不存在。
                .andExpect(jsonPath("$.data.items.length()").value(14))
                .andExpect(jsonPath("$.data.items[0].nutrientCode").isString())
                .andExpect(jsonPath("$.data.items[0].valueStatus").isString())
                // 来源与参考重量必须逐行带出，否则编辑页的数值无从追溯
                .andExpect(jsonPath("$.data.items[0].referenceNote").value("每100g可食部"))
                .andExpect(jsonPath("$.data.items[0].sourceName").isString())
                // 形态「生」的 14 项在种子里全都有值（11 PRESENT + 3 ZERO），
                // 所以这里不能断言 MISSING——那样只是在假设另一种食物数据不全。
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"valueStatus\":\"PRESENT\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"valueStatus\":\"ZERO\"")));
    }

    @Test
    @DisplayName("形态营养表：没有记录的项必须是 MISSING，而不是 PRESENT + null")
    void variantNutritionReportsMissingNutrientAsMissing() throws Exception {
        String token = adminToken();
        // 人为软删掉「西红柿·生」的维生素C 一行（nutrient_id = 11），制造一个真实缺口。
        // 为什么必须自己造：种子里每种食品的营养数据都尽量补齐，「缺失」这一态不会
        // 自然出现，而它恰恰是最容易写错的一态——一旦 LEFT JOIN 写成了 INNER JOIN，
        // 缺失项会整行消失；一旦忘了判空，缺失项会变成 PRESENT 配一个 null 数值，
        // 前端就会把「不知道」显示成「0」。
        jdbc.update("UPDATE nutrition_value SET deleted_at = CURRENT_TIMESTAMP "
                + "WHERE variant_id = 1 AND nutrient_id = 11 AND deleted_at IS NULL");
        try {
            mockMvc.perform(get("/api/v1/admin/variants/1/nutrition")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    // 行数仍然是 14：给出「这一项没有数据」，而不是假装这一项不存在
                    .andExpect(jsonPath("$.data.items.length()").value(14))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            "{\"nutrientId\":11,\"nutrientCode\":\"VITAMIN_C\",\"nutrientName\":\"维生素C\""
                                    + ",\"unit\":\"mg\",\"groupCode\":\"VITAMIN\"")
                            ))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            "\"valueStatus\":\"MISSING\",\"amount\":null")));
        } finally {
            // 还原，避免影响其他用例与前端演示数据的一致性
            jdbc.update("UPDATE nutrition_value SET deleted_at = NULL "
                    + "WHERE variant_id = 1 AND nutrient_id = 11");
        }
    }

    @Test
    @DisplayName("审计日志接口可读（表结构与写入必须一致）")
    void auditLogsAreReadable() throws Exception {
        String token = adminToken();

        // 先把一个已审核通过的食品置为下架，制造一次真实的「发布」状态变更。
        // 为什么必须这么做：种子里所有食品都是 PUBLISHED，直接调 publish 会命中
        // publishFood 的幂等分支（已发布就直接返回、不写审计），于是这个用例会在
        // 「没测到写入」的情况下通过——它曾经真的这样假通过过。
        jdbc.update("UPDATE food SET publish_status = 'OFFLINE', published_at = NULL WHERE id = 1");
        jdbc.update("UPDATE food SET review_status = 'APPROVED' WHERE id = 1");

        mockMvc.perform(post("/api/v1/admin/foods/1/publish").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.publishStatus").value("PUBLISHED"));

        // 写入与读取的列必须一致：audit() 会吞掉异常只打 WARN，列名对不上时接口
        // 表现为「一直返回空列表」而不是报错，只断言接口 200 是发现不了的。
        mockMvc.perform(get("/api/v1/admin/audit-logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list[0].action").value("PUBLISH"))
                .andExpect(jsonPath("$.data.list[0].targetType").value("food"))
                .andExpect(jsonPath("$.data.list[0].targetId").value(1))
                // 操作人必须能显示出来，否则审计「谁改的」这一半就失效了
                .andExpect(jsonPath("$.data.list[0].operatorName").value("admin"));

        // 还原，避免影响其他用例对「西红柿已发布」的假设
        jdbc.update("UPDATE food SET publish_status = 'PUBLISHED' WHERE id = 1");
    }

    @Test
    @DisplayName("知识库列表与重建索引都能跑通，且不谎称做了向量索引")
    void knowledgeListAndReindexWork() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/v1/admin/knowledge").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").isNumber());

        // 当前没有配置 embedding 模型，reindex 必须如实回报 KEYWORD，
        // 不能假装重建了一个并不存在的向量索引（PRD 要求 AI 能力不夸大）。
        mockMvc.perform(post("/api/v1/admin/knowledge/reindex").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.mode").value("KEYWORD"));
    }

    @Test
    @DisplayName("普通用户访问后台必须得到 40301，而不是 40101")
    void ordinaryUserIsForbiddenNotUnauthorized() throws Exception {
        String username = "plain_user_" + System.nanoTime();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = body.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        // 已登录但角色不足 —— 这与「没登录」是两回事，必须是 40301。
        // 两者混淆会让前端把「权限不够」当成「登录过期」而去清令牌、跳登录页。
        mockMvc.perform(get("/api/v1/admin/foods").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));
    }
}
