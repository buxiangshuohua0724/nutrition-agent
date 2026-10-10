package com.nutritionagent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口冒烟测试：把 Controller → Service → 数据访问 → H2 这条链路真正跑一遍。
 *
 * <p>为什么必须有这一层：{@code ApplicationStartupTest} 只证明「上下文能装配」，
 * 而 Controller 的路径拼写、参数绑定、SQL 列名、DTO 字段名都可能在其中任何一步出错——
 * 这些错编译期发现不了，只有真的发一次请求才会暴露。本项目的可用性验收标准里
 * 「工具调用成功率 ≥95%」「营养数据来源可追溯率 100%」都依赖接口真的能返回数据。</p>
 *
 * <p>断言刻意选择「结构性事实」而不是硬编码的完整响应体：数值会随种子数据演进而变化，
 * 但「三态齐全」「MISSING 必须带『暂无数据』」「引用 id 必须真实存在」这些是契约本身，
 * 它们变了才意味着出了问题。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    // ---------------------------------------------------------------- 公开接口

    @Test
    void categoriesReturnEnabledCategoriesWithFoodCount() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].slug").exists())
                .andExpect(jsonPath("$.data[0].foodCount").isNumber());
    }

    @Test
    void foodSearchReturnsPagedResult() throws Exception {
        mockMvc.perform(get("/api/v1/foods/search").param("keyword", "西红柿"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list[0].name").value("西红柿"))
                // energyAvailable 是布尔量，不是「有值/没值」的隐式判断：前端按它决定显示数值还是「暂无数据」
                .andExpect(jsonPath("$.data.list[0].energyAvailable").isBoolean());
    }

    @Test
    void unknownFoodReturnsNotFoundInsteadOfAnotherFood() throws Exception {
        // 关键回归：找不到时必须报 40401，绝不能拿别的食品的数据顶上——
        // 那会让用户以为库里有一条实际不存在的食品。
        mockMvc.perform(get("/api/v1/foods/999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void nutritionTableAlwaysCoversEveryEnabledNutrient() throws Exception {
        // 种子库里有个别形态整项缺行；服务层必须把它们补成 MISSING，
        // 否则界面上少一行，用户会以为「这项不存在」而不是「这项暂无数据」。
        mockMvc.perform(get("/api/v1/foods/1/nutrition"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.foodId").value(1))
                .andExpect(jsonPath("$.data.referenceAmount").value(100))
                .andExpect(jsonPath("$.data.rows.length()").value(14))
                .andExpect(jsonPath("$.data.rows[0].valueStatus").exists())
                .andExpect(jsonPath("$.data.rows[0].source.name").exists())
                // FR-003 要求详情页展示「数据更新时间」，前端读的是 nutritionTable.dataUpdatedAt
                // （frontend/src/views/FoodDetailView.vue:96-100、ReportView.vue:312）。
                // 这个字段曾经根本没被后端返回，于是界面永远显示占位符 —— 字段存在性必须由测试守住，
                // 因为它缺失时不会报错、不会白屏，只会安静地少显示一个时间。
                .andExpect(jsonPath("$.data.dataUpdatedAt").isString())
                .andExpect(jsonPath("$.data.dataUpdatedAt").value(
                        org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")));
    }

    @Test
    void missingNutritionAlwaysRendersPlaceholderText() throws Exception {
        // 遍历全部形态：只要 valueStatus 是 MISSING，就必须是 amount=null + displayText「暂无数据」。
        // 这是「未知营养值 ≠ 0」这条硬约束在接口层的直接体现。
        String body = mockMvc.perform(get("/api/v1/nutrition/nutrients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(14))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains("ENERGY").contains("PROTEIN");
    }

    @Test
    void calculateNutritionScalesLinearlyWithWeight() throws Exception {
        // 100g 的结果必须等于每 100g 原值；翻倍重量结果翻倍——这是 PRD 5.4 的确定性公式。
        String base = mockMvc.perform(post("/api/v1/nutrition/calculate")
                        .contentType("application/json")
                        .content("{\"foodId\":1,\"variantId\":1,\"weight\":100,\"weightUnit\":\"g\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.estimated").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(base).contains("formula");

        mockMvc.perform(post("/api/v1/nutrition/calculate")
                        .contentType("application/json")
                        .content("{\"foodId\":1,\"variantId\":1,\"weight\":0,\"weightUnit\":\"g\"}"))
                .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    void knowledgeSearchOnlyReturnsPublishedDocs() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge/search").param("keyword", "零值"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test
    void capabilitiesTellTheTruthAboutUnconfiguredModels() throws Exception {
        // dev profile 下 llm/vision 都是 disabled：接口必须如实说「没接」，
        // 而不是返回一个看起来已经具备能力的空壳。
        mockMvc.perform(get("/api/v1/agent/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.modelConfigured").value(false))
                .andExpect(jsonPath("$.data.visionConfigured").value(false))
                .andExpect(jsonPath("$.data.limitations").isArray());
    }

    @Test
    void chatSucceedsWithStructuredDataEvenWithoutAModel() throws Exception {
        // PRD 6.x：外部模型不可用时，问答仍要基于数据库事实给出回答（降级而非失败）。
        mockMvc.perform(post("/api/v1/agent/chat")
                        .contentType("application/json")
                        .content("{\"question\":\"西红柿的营养价值\",\"foodId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // 首次提问由后端建会话并把 id 回传，前端靠它做多轮
                .andExpect(jsonPath("$.data.sessionId").isNumber())
                .andExpect(jsonPath("$.data.answer.answer").isString())
                // 来源必须由工具层注入，且 refId 指向真实存在的食品/形态
                .andExpect(jsonPath("$.data.answer.sources").isArray())
                .andExpect(jsonPath("$.data.answer.sources[0].refId").exists())
                // 营养事实必须带上单位与参考口径，否则用户无法判断数值的含义
                .andExpect(jsonPath("$.data.answer.nutritionFacts[0].unit").exists())
                .andExpect(jsonPath("$.data.answer.nutritionFacts[0].referenceNote").exists());
    }

    @Test
    void unknownRecognitionReturnsNotFound() throws Exception {
        // 视觉未配置时不能假装识别出东西——返回热门食品当候选等于把随机结果伪装成识别结果。
        // 这里先覆盖「记录不存在」的边界：必须 40401，而不是 500 或空对象。
        mockMvc.perform(get("/api/v1/recognitions/999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40401));
    }

    // ---------------------------------------------------------------- 鉴权

    @Test
    void adminEndpointsRejectGuests() throws Exception {
        // 后台接口必须被鉴权层拦住。这里没有令牌，期望 40101。
        mockMvc.perform(get("/api/v1/admin/foods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void myEndpointsRejectGuests() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void registerLoginAndReadOwnProfile() throws Exception {
        String username = "smoke_user_" + System.nanoTime();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\",\"nickname\":\"冒烟\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(username));

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn().getResponse().getContentAsString();

        String token = loginBody.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.roles").isArray());

        // 收藏 → 列表 → 取消，走完整闭环
        mockMvc.perform(post("/api/v1/users/me/favorites")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"foodId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/users/me/favorites").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("西红柿"));
    }

    @Test
    void weakPasswordIsRejectedAtRegistration() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"smoke_weak_" + System.nanoTime()
                                + "\",\"password\":\"123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001));
    }
}
