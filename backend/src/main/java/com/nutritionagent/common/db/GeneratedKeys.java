package com.nutritionagent.common.db;

import org.springframework.jdbc.support.GeneratedKeyHolder;

/**
 * 自增主键读取。
 *
 * <p><b>必须指名要取哪一列</b>：{@code new String[]{"id"}} 而不是裸的
 * {@code Statement.RETURN_GENERATED_KEYS}。原因是有些表除了自增主键还有
 * {@code DEFAULT CURRENT_TIMESTAMP} 的时间列，驱动会把它们一并放进返回的键集合里，
 * 于是 {@link GeneratedKeyHolder#getKey()} 抛
 * 「The getKey method should only be used when a single key is returned」。
 * 这个异常在开发的 H2 上就会出现（H2 会回传默认值列），但写代码时很容易只在
 * 「以前这样写能跑」的地方复制粘贴，等到某张表加了时间默认值才炸。</p>
 */
public final class GeneratedKeys {

    /** 只请求 {@code id} 一列的用法：{@code prepareStatement(sql, GeneratedKeys.ID_COLUMN)}。 */
    public static final String[] ID_COLUMN = {"id"};

    private GeneratedKeys() {
    }

    /**
     * 从 KeyHolder 取主键。
     *
     * @return 主键值；拿不到时返回 {@code null}，由调用方决定如何报错（通常是 50001）
     */
    public static Long id(GeneratedKeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }
}
