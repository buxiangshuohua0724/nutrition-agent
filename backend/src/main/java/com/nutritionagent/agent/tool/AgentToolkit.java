package com.nutritionagent.agent.tool;

import com.nutritionagent.agent.dto.AgentAnswer;
import com.nutritionagent.common.exception.BusinessException;
import com.nutritionagent.food.dto.FoodDetail;
import com.nutritionagent.food.dto.ImageView;
import com.nutritionagent.food.service.FoodQueryService;
import com.nutritionagent.knowledge.dto.KnowledgeHit;
import com.nutritionagent.knowledge.service.KnowledgeSearchService;
import com.nutritionagent.nutrition.dto.NutritionCalculation;
import com.nutritionagent.nutrition.dto.NutritionTable;
import com.nutritionagent.nutrition.service.NutritionQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 的工具层（PRD 第六章：Agent 是协调层，不是数据库，也不代替业务服务做计算）。
 *
 * <p>这里做的唯一一件事：<b>调用既有业务服务取事实</b>，并把「取到了什么、没取到什么」
 * 如实记录下来。所有数值都来自 {@link NutritionQueryService}（结构化数据 + 确定性公式），
 * 来源都来自真实的 {@code data_source} 与 {@code knowledge_chunk} 行。
 *
 * <p>「没取到什么」同样是必须返回的信息：某个营养素在库里就是 MISSING 时，它进入
 * {@code uncertainty}，而不是被静默省略（PRD 硬约束：未知 ≠ 0，缺失要显式说明）。
 */
@Component
public class AgentToolkit {

    private static final Logger log = LoggerFactory.getLogger(AgentToolkit.class);

    private static final int KNOWLEDGE_LIMIT = 3;

    private final FoodQueryService foodQueryService;
    private final NutritionQueryService nutritionQueryService;
    private final KnowledgeSearchService knowledgeSearchService;

    public AgentToolkit(FoodQueryService foodQueryService,
                        NutritionQueryService nutritionQueryService,
                        KnowledgeSearchService knowledgeSearchService) {
        this.foodQueryService = foodQueryService;
        this.nutritionQueryService = nutritionQueryService;
        this.knowledgeSearchService = knowledgeSearchService;
    }

    /**
     * 一次问答所需的全部事实。
     *
     * <p>字段刻意都是「已确定」的形态：数值是字符串（已格式化）或 null，绝不出现"待模型填充"。
     */
    public record AgentContext(
            FoodDetail food,
            Long variantId,
            String variantLabel,
            NutritionTable table,
            NutritionCalculation calculation,
            List<KnowledgeHit> knowledge,
            List<AgentAnswer.Source> sources,
            List<AgentAnswer.Uncertainty> uncertainty,
            List<AgentAnswer.ToolCall> toolCalls,
            List<AgentAnswer.NutritionFact> nutritionFacts,
            List<AgentAnswer.FoodReference> foodReferences) {
    }

    /**
     * 取数。任何一步失败都<b>不抛异常打断问答</b>，而是记进 {@code uncertainty} 与工具调用记录，
     * 让用户拿到一个"部分信息 + 明确说明缺什么"的回答，而不是一个 500。
     *
     * @param foodId   用户指定的食品，可为 null（此时按问题关键词搜索）
     * @param question 用户问题
     */
    public AgentContext collect(Long foodId, Long variantId, BigDecimal weight, String question) {
        List<AgentAnswer.ToolCall> toolCalls = new ArrayList<>();
        List<AgentAnswer.Uncertainty> uncertainty = new ArrayList<>();
        List<AgentAnswer.Source> sources = new ArrayList<>();

        FoodDetail food = null;
        Long resolvedVariantId = variantId;
        String variantLabel = null;

        Long targetFoodId = foodId != null ? foodId : searchFoodId(question, toolCalls);
        if (targetFoodId != null) {
            long started = System.currentTimeMillis();
            try {
                food = foodQueryService.detail(targetFoodId);
                toolCalls.add(new AgentAnswer.ToolCall("getFoodDetail",
                        Map.of("foodId", targetFoodId), true, elapsed(started)));
            } catch (BusinessException e) {
                // 指定了食品但取不到：这是用户能理解、也应该知道的情况
                toolCalls.add(new AgentAnswer.ToolCall("getFoodDetail",
                        Map.of("foodId", targetFoodId), false, elapsed(started)));
                uncertainty.add(new AgentAnswer.Uncertainty("FOOD_NOT_FOUND",
                        "未找到该食品，以下内容不针对任何具体食品"));
            }
        }

        NutritionTable table = null;
        NutritionCalculation calculation = null;
        if (food != null) {
            long t1 = System.currentTimeMillis();
            try {
                table = nutritionQueryService.nutritionTable(food.id(), resolvedVariantId);
                resolvedVariantId = table.variantId();
                variantLabel = table.variantLabel();
                toolCalls.add(new AgentAnswer.ToolCall("getNutrition",
                        args("foodId", food.id(), "variantId", table.variantId()), true, elapsed(t1)));
            } catch (BusinessException e) {
                toolCalls.add(new AgentAnswer.ToolCall("getNutrition",
                        args("foodId", food.id(), "variantId", resolvedVariantId), false, elapsed(t1)));
                uncertainty.add(new AgentAnswer.Uncertainty("NUTRITION_NOT_FOUND",
                        "该食品形态在数据库中暂无营养数据"));
            }

            if (table != null && weight != null && weight.signum() > 0) {
                long t2 = System.currentTimeMillis();
                try {
                    calculation = nutritionQueryService.calculate(food.id(), table.variantId(), weight, "g");
                    toolCalls.add(new AgentAnswer.ToolCall("calculateNutrition",
                            args("foodId", food.id(), "variantId", table.variantId(), "weight", weight), true, elapsed(t2)));
                } catch (BusinessException e) {
                    toolCalls.add(new AgentAnswer.ToolCall("calculateNutrition",
                            args("foodId", food.id(), "variantId", table.variantId(), "weight", weight), false, elapsed(t2)));
                    uncertainty.add(new AgentAnswer.Uncertainty("CALCULATION_FAILED",
                            "按重量折算未能完成，以下数值为每参考重量的含量"));
                }
            }
        }

        List<KnowledgeHit> knowledge = searchKnowledge(question, toolCalls);

        if (table != null) {
            sources.add(new AgentAnswer.Source("STRUCTURED_DATA",
                    "食品营养数据库（营养值来自结构化数据）",
                    "FOOD_" + food.id() + "#variant" + table.variantId(), null));
        }
        for (KnowledgeHit hit : knowledge) {
            sources.add(new AgentAnswer.Source("KNOWLEDGE", hit.title(),
                    "KDOC_" + pad(hit.docId()) + "#chunk" + hit.chunkId(), hit.source() == null ? null : hit.source().url()));
        }

        List<String> missing = missingNutrientNames(table, calculation);
        if (!missing.isEmpty()) {
            uncertainty.add(new AgentAnswer.Uncertainty("DATA_MISSING",
                    String.join("、", missing) + "在本数据库中尚未录入，本次回答不给出这些项的数值"));
        }

        List<AgentAnswer.NutritionFact> facts = factsOf(table);
        List<AgentAnswer.FoodReference> refs = new ArrayList<>();
        if (food != null) {
            refs.add(new AgentAnswer.FoodReference(food.id(), food.name(), resolvedVariantId, variantLabel,
                    coverUrlOf(food)));
        }
        return new AgentContext(food, resolvedVariantId, variantLabel, table, calculation,
                knowledge, sources, uncertainty, toolCalls, facts, refs);
    }

    // ------------------------------------------------------------ 单步工具

    /** 用问题里的食品名去搜索（"西红柿的营养价值" → foodId）。取不到就返回 null。 */
    private Long searchFoodId(String question, List<AgentAnswer.ToolCall> toolCalls) {
        String keyword = extractKeyword(question);
        if (keyword.isEmpty()) {
            return null;
        }
        long started = System.currentTimeMillis();
        try {
            var page = foodQueryService.search(keyword, null, 1, 1, FoodQueryService.SortMode.RELEVANCE);
            toolCalls.add(new AgentAnswer.ToolCall("searchFood", Map.of("keyword", keyword), true, elapsed(started)));
            return page.list().isEmpty() ? null : page.list().get(0).id();
        } catch (BusinessException e) {
            toolCalls.add(new AgentAnswer.ToolCall("searchFood", Map.of("keyword", keyword), false, elapsed(started)));
            return null;
        }
    }

    private List<KnowledgeHit> searchKnowledge(String question, List<AgentAnswer.ToolCall> toolCalls) {
        long started = System.currentTimeMillis();
        try {
            var page = knowledgeSearchService.search(question, null, 1, KNOWLEDGE_LIMIT);
            toolCalls.add(new AgentAnswer.ToolCall("searchNutritionKnowledge",
                    Map.of("keyword", question), true, elapsed(started)));
            return page.list();
        } catch (BusinessException e) {
            log.warn("知识检索失败：{}", e.getMessage());
            toolCalls.add(new AgentAnswer.ToolCall("searchNutritionKnowledge",
                    Map.of("keyword", question), false, elapsed(started)));
            return List.of();
        }
    }

    /**
     * 从问题里截出可能的关键词。
     *
     * <p>刻意做得笨：去掉疑问词与语气词，剩下的整串拿去做一次模糊搜索。真正的意图识别
     * 属于模型该做的事，这里只是"模型没配时的确定性回退"，不做分词以免悄悄引入词典。
     */
    static String extractKeyword(String question) {
        if (question == null) {
            return "";
        }
        String cleaned = question
                .replaceAll("[?？!！。,，、；;：:\\s]", "")
                .replace("的营养价值有哪些", "")
                .replace("的营养价值", "")
                .replace("有什么营养", "")
                .replace("有哪些营养", "")
                .replace("营养成分", "")
                .replace("的热量", "")
                .replace("热量", "")
                .replace("怎么样", "")
                .replace("好不好", "")
                .replace("能不能吃", "")
                .replace("是什么", "")
                .replace("请问", "")
                .replace("介绍", "");
        // 仍然太长说明没有明显结构，交给搜索去模糊匹配也无意义，直接返回前 20 字
        return cleaned.length() > 20 ? cleaned.substring(0, 20) : cleaned;
    }

    /** 封面图 URL。没有授权图片时返回 null——前端显示占位，不允许拿别的食品的图顶替。 */
    private static String coverUrlOf(FoodDetail food) {
        if (food.images() == null || food.images().isEmpty()) {
            return null;
        }
        for (ImageView image : food.images()) {
            if (image.isCover()) {
                return image.url();
            }
        }
        return food.images().get(0).url();
    }

    private static List<AgentAnswer.NutritionFact> factsOf(NutritionTable table) {
        if (table == null) {
            return List.of();
        }
        List<AgentAnswer.NutritionFact> facts = new ArrayList<>();
        for (NutritionTable.Row row : table.rows()) {
            facts.add(new AgentAnswer.NutritionFact(
                    table.foodName(), table.variantLabel(),
                    row.nutrientCode(), row.nutrientName(),
                    row.amount() == null ? null : row.amount().toPlainString(),
                    row.unit(), row.valueStatus(), row.displayText(),
                    table.referenceNote(),
                    row.source() == null ? null : row.source().name()));
        }
        return facts;
    }

    /** 缺失项的中文名（MISSING 行 + 有记录但值为 MISSING 的项）。 */
    private static List<String> missingNutrientNames(NutritionTable table, NutritionCalculation calculation) {
        List<String> names = new ArrayList<>();
        if (table != null) {
            for (NutritionTable.Row row : table.rows()) {
                if ("MISSING".equals(row.valueStatus())) {
                    names.add(row.nutrientName());
                }
            }
        }
        if (calculation != null && calculation.missingNutrients() != null) {
            for (String code : calculation.missingNutrients()) {
                if (!names.contains(code)) {
                    names.add(code);
                }
            }
        }
        return names;
    }

    private static Map<String, Object> args(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    private static int elapsed(long started) {
        return (int) (System.currentTimeMillis() - started);
    }

    private static String pad(Long id) {
        return id == null ? "0000" : String.format("%04d", id);
    }

    /** 供实现层判断：这次问答是否真的取到了任何事实。 */
    public static boolean hasFacts(AgentContext context) {
        return context.food() != null;
    }
}
