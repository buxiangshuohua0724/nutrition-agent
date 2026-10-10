package com.nutritionagent.nutrition.service;

import com.nutritionagent.common.api.ErrorCode;
import com.nutritionagent.common.exception.BusinessException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

/**
 * 营养折算的<b>确定性</b>算法（PRD 5.4），刻意做成不依赖 Spring、不依赖数据库的纯函数。
 *
 * <p>为什么单独抽出来：验收要求"营养计算必须可独立测试"，而这个公式又是全项目唯一
 * 允许从已有数值推导新数值的地方。它一旦出错，界面上会显示一个看起来很像事实的错数字，
 * 比缺数据危险得多。因此这里：
 * <ul>
 *   <li>不查库、不做 IO，输入输出全是值对象，可逐位复算；</li>
 *   <li>非法输入一律抛 {@link BusinessException}，绝不"尽力而为"地猜一个值；</li>
 *   <li>缺失项（{@code MISSING}）不参与折算，也<b>绝不</b>当作 0。</li>
 * </ul>
 */
public final class NutritionMath {

    /** 重量上限（契约 4.2：超过即 40002）。 */
    public static final BigDecimal MAX_WEIGHT = new BigDecimal("100000");

    /** 各重量单位对克的换算系数。ml/l 按密度 1 处理，属于"近似"，因此结果标记 estimated。 */
    public static final Map<String, BigDecimal> UNIT_TO_GRAM = Map.of(
            "g", BigDecimal.ONE,
            "kg", new BigDecimal("1000"),
            "ml", BigDecimal.ONE,
            "l", new BigDecimal("1000"));

    /** 折算结果保留的小数位。 */
    private static final int SCALE = 3;

    private NutritionMath() {
    }

    /**
     * 校验并换算重量为克。
     *
     * @param weight     用户输入重量，必须 &gt;0 且 ≤100000
     * @param weightUnit 单位，必须是 g/kg/ml/l 之一
     * @return 换算后的克数
     * @throws BusinessException 重量非法 → 40002；单位非法 → 40001
     */
    public static BigDecimal toGrams(BigDecimal weight, String weightUnit) {
        if (weight == null) {
            throw new BusinessException(ErrorCode.INVALID_WEIGHT, "weight 不能为空");
        }
        if (weight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_WEIGHT, "weight 必须大于 0");
        }
        if (weight.compareTo(MAX_WEIGHT) > 0) {
            throw new BusinessException(ErrorCode.INVALID_WEIGHT,
                    "weight 不能超过 " + MAX_WEIGHT.stripTrailingZeros().toPlainString());
        }
        BigDecimal factor = factorOf(weightUnit);
        return weight.multiply(factor, MathContext.DECIMAL64);
    }

    /** 单位换算系数；未知单位直接拒绝，不静默按克处理。 */
    public static BigDecimal factorOf(String weightUnit) {
        if (weightUnit == null || weightUnit.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "weightUnit 不能为空，可选 g / kg / ml / l");
        }
        BigDecimal factor = UNIT_TO_GRAM.get(weightUnit.trim().toLowerCase(Locale.ROOT));
        if (factor == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "不支持的重量单位：" + weightUnit + "（可选 g / kg / ml / l）");
        }
        return factor;
    }

    /**
     * 单个营养素的折算：{@code 每 referenceAmount 的含量 × 实际克数 ÷ referenceAmount}。
     *
     * @param originalAmount  每参考重量的含量（必须非空；缺失项不应调用本方法）
     * @param referenceAmount 参考重量（种子数据恒为 100，但算法不假设这一点）
     * @param grams           实际食用克数
     */
    public static BigDecimal compute(BigDecimal originalAmount, BigDecimal referenceAmount, BigDecimal grams) {
        if (originalAmount == null) {
            throw new IllegalArgumentException("originalAmount 不能为空：缺失项不应参与折算");
        }
        if (referenceAmount == null || referenceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("referenceAmount 必须为正数");
        }
        if (grams == null) {
            throw new IllegalArgumentException("grams 不能为空");
        }
        return originalAmount.multiply(grams, MathContext.DECIMAL64)
                .divide(referenceAmount, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 数值的展示文本：去掉无意义的尾随零。
     *
     * <p>{@code ZERO} 必须显示为 "0" 而不是 "暂无数据" —— 这正是三态的用处。
     */
    public static String displayText(BigDecimal value) {
        if (value == null) {
            return "暂无数据";
        }
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0, RoundingMode.UNNECESSARY);
        }
        return stripped.toPlainString();
    }

    /**
     * 生成可复核的算式文本，例如 {@code 15 kcal/100g × 150 g ÷ 100 = 22.5 kcal}。
     *
     * <p>把算式写给用户看，是为了让"这是折算值、不是数据库里的原始数值"这件事无法被忽略。
     */
    public static String formula(BigDecimal originalAmount, String unit, BigDecimal grams,
                                 BigDecimal referenceAmount, String referenceUnit, BigDecimal computed) {
        String refUnit = referenceUnit == null || referenceUnit.isBlank() ? "g" : referenceUnit;
        return displayText(originalAmount) + " " + unit + "/" + displayText(referenceAmount) + refUnit
                + " × " + displayText(grams) + " g ÷ " + displayText(referenceAmount) + refUnit
                + " = " + displayText(computed) + " " + unit;
    }
}
