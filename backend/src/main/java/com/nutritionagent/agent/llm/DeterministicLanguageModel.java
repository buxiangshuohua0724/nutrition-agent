package com.nutritionagent.agent.llm;

import com.nutritionagent.agent.dto.AgentAnswer;
import com.nutritionagent.agent.tool.AgentToolkit;
import com.nutritionagent.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 确定性回答组织器（模型未配置时的实现，也是开发态默认实现）。
 *
 * <p>它<b>不是</b>一个"小模型"：不会推理、不会扩展知识、不会给出未在数据里出现的建议。
 * 它能做的只有两件事：
 * <ol>
 *   <li>把结构化数据渲染成可读文本（数值一律来自 {@link AgentToolkit}）；</li>
 *   <li>把"哪些项没有数据"如实说出来。</li>
 * </ol>
 *
 * <p>之所以让它成为默认实现，是因为这样开发态与测试态下的问答行为是<b>完全可复现</b>的：
 * 同一问题必得同一回答，没有随机性也没有外部依赖。真实模型接入后替换本类即可，
 * 上层（{@code AgentService}、Controller、前端）不需要任何改动。
 */
@Component
public class DeterministicLanguageModel implements LanguageModel {

    /** 推荐语是固定的通用表述，不含任何个性化剂量或疗效承诺（PRD 例外禁区）。 */
    private static final String GENERAL_ADVICE =
            "以上为营养成分层面的客观数据，具体摄入量需结合个人情况；本回答不构成医疗或膳食处方建议。";

    private final AppProperties properties;

    public DeterministicLanguageModel(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        // 若真实模型已配置但本实现仍被使用，回答里如实标出，避免把桩实现的结果当成模型输出
        return properties.getAi().getLlm().isConfigured() ? "deterministic-stub（真实模型未接入）" : "deterministic-stub";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String compose(String question, AgentToolkit.AgentContext context) {
        List<String> lines = new ArrayList<>();

        if (context.food() == null) {
            lines.add("我没有在食品数据库中找到与「" + shorten(question) + "」对应的食品，因此这次回答不包含任何具体食品的营养数值。");
            lines.add("");
            lines.add("你可以尝试：换成食品的标准名称（例如「西红柿」而不是「番茄炒蛋」），或者先到「食品搜索」页找到该食品再提问。");
        } else {
            String title = context.food().name()
                    + (context.variantLabel() == null ? "" : "（" + context.variantLabel() + "）");
            lines.add("🍚 主要营养特点");
            if (context.table() == null) {
                lines.add(title + "在数据库中暂无营养数据，无法给出数值。");
            } else {
                String reference = "每 " + plain(context.table().referenceAmount())
                        + compact(context.table().referenceUnit());
                if (context.table().referenceNote() != null && !context.table().referenceNote().isBlank()) {
                    reference += "（" + context.table().referenceNote() + "）";
                }
                lines.add(title + "，" + reference + "：");
                lines.add(macroLine(context));
            }
            lines.add("");
            lines.add("0️⃣ 已知零值");
            String zeros = joinNames(context, "ZERO");
            lines.add(zeros.isEmpty() ? "无" : zeros);
            lines.add(zeros.isEmpty()
                    ? ""
                    : "以上各项经过确认确实为 0，与「暂无数据」含义不同。");
            lines.add("");
            lines.add("⚠️ 需要注意");
            String missing = joinNames(context, "MISSING");
            lines.add(missing.isEmpty()
                    ? "该食品在数据库中已录入的营养项是完整的，没有缺项。"
                    : missing + "在本数据库中尚未录入，本次回答不给出这些项的数值——"
                    + "「没有数据」不等于「含量为 0」。");
        }

        if (context.knowledge() != null && !context.knowledge().isEmpty()) {
            lines.add("");
            lines.add("📚 相关知识要点");
            context.knowledge().forEach(hit -> lines.add("· " + hit.title()
                    + (hit.heading() == null || hit.heading().isBlank() ? "" : "—" + hit.heading())
                    + "：" + shorten(hit.snippet())));
        }

        if (context.calculation() != null) {
            lines.add("");
            lines.add("🧮 按重量折算");
            context.calculation().items().forEach(item -> lines.add("· " + item.nutrientName()
                    + " " + item.displayText() + "（" + item.formula() + "）"));
        }

        lines.add("");
        lines.add(GENERAL_ADVICE);
        return String.join("\n", lines);
    }

    /** 宏量营养素的单行摘要（热量、蛋白质、脂肪、碳水化合物、膳食纤维）。 */
    private static String macroLine(AgentToolkit.AgentContext context) {
        List<String> parts = new ArrayList<>();
        List<String> wanted = List.of("ENERGY", "PROTEIN", "FAT", "CARBOHYDRATE", "DIETARY_FIBER");
        for (String code : wanted) {
            for (AgentAnswer.NutritionFact fact : context.nutritionFacts()) {
                if (code.equals(fact.nutrientCode()) && !"MISSING".equals(fact.valueStatus())) {
                    parts.add(fact.nutrientName() + " " + fact.amount() + " " + fact.unit());
                }
            }
        }
        return parts.isEmpty() ? "本表暂无已录入的宏量营养素数值。" : String.join("、", parts) + "。";
    }

    /** 把指定状态（ZERO/MISSING）的营养项拼成「甲、乙、丙」。 */
    private static String joinNames(AgentToolkit.AgentContext context, String valueStatus) {
        List<String> names = new ArrayList<>();
        for (AgentAnswer.NutritionFact fact : context.nutritionFacts()) {
            if (valueStatus.equals(fact.valueStatus()) && !names.contains(fact.nutrientName())) {
                names.add(fact.nutrientName());
            }
        }
        return String.join("、", names);
    }

    private static String plain(java.math.BigDecimal value) {
        if (value == null) {
            return "";
        }
        java.math.BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0).toPlainString() : stripped.toPlainString();
    }

    private static String compact(String unit) {
        return unit == null ? "" : unit;
    }

    private static String shorten(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 60) + "…";
    }
}
