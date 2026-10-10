package com.nutritionagent.common.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;

/**
 * 时间戳格式化。
 *
 * <p>为什么必须统一走这里：直接把 JDBC 的 {@link Timestamp}（或其 {@code toString()}）
 * 塞进响应体，会带上数据库相关的尾巴——H2 返回 {@code 2025-01-01 00:00:00.0}（末尾多出
 * 纳秒小数），MySQL 返回 {@code 2025-01-01 00:00:00.0} 或带纳秒的不同位数。前端把
 * 「更新时间」当成可比对、可展示的字符串，格式随数据库漂移会让同一条记录在两种环境里
 * 长得不一样，也让测试无法稳定断言。
 */
public final class Timestamps {

    /** 全项目统一的展示格式（与 {@code jackson.date-format} 保持一致）。 */
    public static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Timestamps() {
    }

    /**
     * 读取可空时间列并格式化为 {@code yyyy-MM-dd HH:mm:ss}。
     *
     * @return 列为 NULL 时返回 {@code null}（不返回空串——「没有时间」和「时间为空字符串」不是一回事）
     */
    public static String format(ResultSet rs, String column) throws SQLException {
        return format(rs.getTimestamp(column));
    }

    /** 格式化一个可空时间戳。 */
    public static String format(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().format(DISPLAY);
    }
}
