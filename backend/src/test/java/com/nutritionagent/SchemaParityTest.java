package com.nutritionagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 两份 schema 的结构一致性测试。
 *
 * <p>项目里刻意维护了两份建表脚本：
 * <ul>
 *   <li>{@code docs/db/schema.sql} —— MySQL 8 版，生产建表用，是**权威定义**；</li>
 *   <li>{@code backend/src/main/resources/db/h2/schema.sql} —— H2 版，
 *       供 dev / test profile 免外部数据库启动。</li>
 * </ul>
 *
 * <p>两者允许的差异只有下面三类（都是为了适配 H2 语法/能力，不改变语义）：
 * <ol>
 *   <li>去掉 {@code ENGINE} / {@code CHARSET} / {@code SET NAMES} 等 MySQL 专有语句；</li>
 *   <li>{@code KEY idx_x (...)} 行内索引拆成独立的 {@code CREATE INDEX}；</li>
 *   <li>{@code JSON} 列退化为 {@code VARCHAR}（H2 的 JSON 类型行为与 MySQL 不一致）。</li>
 * </ol>
 *
 * <p><b>表名与列名（这是本测试真正守护的不变量）必须逐字一致。</b>
 * 一旦有人在 MySQL 版加了列却忘了同步 H2 版，dev 环境就会静默地少一个字段，
 * 而这种漂移在人工 review 里极难发现，所以用测试卡住。
 */
class SchemaParityTest {

    /** 项目根目录：从 backend/ 往上一级就是。 */
    private static final Path PROJECT_ROOT = Paths.get("..").toAbsolutePath().normalize();

    private static final Path MYSQL_SCHEMA = PROJECT_ROOT.resolve("docs/db/schema.sql");
    private static final Path H2_SCHEMA =
            PROJECT_ROOT.resolve("backend/src/main/resources/db/h2/schema.sql");

    /** 匹配 `CREATE TABLE [IF NOT EXISTS] `name` (` ... `) ENGINE=...;` */
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([A-Za-z0-9_]+)`?\\s*\\((.*?)\\)\\s*(?:ENGINE|COMMENT|;)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 列级/表级 `COMMENT '...'`：解析前必须剥掉，否则注释里的括号会破坏列切分。 */
    private static final Pattern COMMENT_CLAUSE = Pattern.compile(
            "\\bCOMMENT\\s+'(?:[^']|'')*'", Pattern.CASE_INSENSITIVE);

    /**
     * 索引名 / 唯一约束名的**声明位置**：只认真正的声明行，不认正文里的同名文字。
     *
     * <p>四种写法都要覆盖：
     * <ul>
     *   <li>MySQL 行内索引 {@code KEY idx_x (...)}；</li>
     *   <li>MySQL 行内唯一键 {@code UNIQUE KEY uk_x (...)}；</li>
     *   <li>H2 / MySQL 显式唯一约束 {@code CONSTRAINT uk_x UNIQUE (...)}；</li>
     *   <li>H2 独立索引 {@code CREATE [UNIQUE] INDEX idx_x ON ...}。</li>
     * </ul>
     *
     * <p>用负向后顾排除 {@code FOREIGN KEY} 与 {@code PRIMARY KEY}：否则
     * {@code fk_category_parent} 这类外键名也会被当成索引名匹配进来，
     * 造成"两份 schema 索引不一致"的假象。
     */
    private static final Pattern INDEX_DECLARATION = Pattern.compile(
            "(?<![A-Za-z_])(?:UNIQUE\\s+KEY|KEY|INDEX|CONSTRAINT)\\s+`?((?:uk_|idx_)[A-Za-z0-9_]*)`?",
            Pattern.CASE_INSENSITIVE);

    /** H2 的独立索引语句：{@code CREATE [UNIQUE] INDEX idx_x ON ...}。 */
    private static final Pattern CREATE_INDEX_STATEMENT = Pattern.compile(
            "CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+`?((?:uk_|idx_)[A-Za-z0-9_]*)`?",
            Pattern.CASE_INSENSITIVE);

    /** 外键与 CHECK 约束——两份 schema 都写成 `CONSTRAINT <name> FOREIGN KEY|CHECK`。 */
    private static final Pattern NAMED_CONSTRAINT = Pattern.compile(
            "CONSTRAINT\\s+`?((?:fk_|ck_)[A-Za-z0-9_]*)`?",
            Pattern.CASE_INSENSITIVE);

    /** 需要在比对列集合时忽略的行（表级约束、索引、外键、主键）。 */
    private static final Pattern NON_COLUMN_LINE = Pattern.compile(
            "^\\s*(PRIMARY\\s+KEY|UNIQUE\\s+KEY|UNIQUE\\s+INDEX|KEY|INDEX|CONSTRAINT|FOREIGN\\s+KEY|CHECK)\\b",
            Pattern.CASE_INSENSITIVE);

    /** 列定义行的第一个 token 就是列名。 */
    private static final Pattern COLUMN_NAME = Pattern.compile("^`?([A-Za-z0-9_]+)`?\\s+");

    @Test
    @DisplayName("两份 schema.sql 的表集合必须完全一致")
    void tableSetsMatch() {
        Map<String, Set<String>> mysql = parseTables(read(MYSQL_SCHEMA));
        Map<String, Set<String>> h2 = parseTables(read(H2_SCHEMA));

        assertThat(mysql).as("docs/db/schema.sql 未解析出任何表，请检查正则是否失效").isNotEmpty();
        assertThat(h2).as("H2 schema.sql 未解析出任何表，请检查正则是否失效").isNotEmpty();

        assertThat(new TreeSet<>(h2.keySet()))
                .as("H2 schema 的表集合与 MySQL 权威定义不一致")
                .isEqualTo(new TreeSet<>(mysql.keySet()));
        assertThat(mysql).hasSize(21);
    }

    @Test
    @DisplayName("每张表的列集合必须完全一致（顺序不敏感，但名字与数量要相同）")
    void columnSetsMatchPerTable() {
        Map<String, Set<String>> mysql = parseTables(read(MYSQL_SCHEMA));
        Map<String, Set<String>> h2 = parseTables(read(H2_SCHEMA));

        Map<String, String> mismatches = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : mysql.entrySet()) {
            Set<String> h2Columns = h2.get(entry.getKey());
            if (h2Columns == null) {
                continue; // 表缺失由 tableSetsMatch 负责报错
            }
            Set<String> onlyInMysql = new TreeSet<>(entry.getValue());
            onlyInMysql.removeAll(h2Columns);
            Set<String> onlyInH2 = new TreeSet<>(h2Columns);
            onlyInH2.removeAll(entry.getValue());
            if (!onlyInMysql.isEmpty() || !onlyInH2.isEmpty()) {
                mismatches.put(entry.getKey(),
                        "仅 MySQL 有 " + onlyInMysql + "；仅 H2 有 " + onlyInH2);
            }
        }

        assertThat(mismatches)
                .as("下列表的列集合在两份 schema 之间漂移了，必须同步")
                .isEmpty();
    }

    @Test
    @DisplayName("索引名集合必须一致（MySQL 写行内 KEY，H2 写独立 CREATE INDEX）")
    void indexSetsMatch() {
        String mysql = stripComments(read(MYSQL_SCHEMA));
        String h2 = stripComments(read(H2_SCHEMA));

        Set<String> mysqlIndexes = collect(INDEX_DECLARATION, mysql);
        mysqlIndexes.addAll(collect(CREATE_INDEX_STATEMENT, mysql));

        Set<String> h2Indexes = collect(INDEX_DECLARATION, h2);
        h2Indexes.addAll(collect(CREATE_INDEX_STATEMENT, h2));

        assertThat(mysqlIndexes).as("MySQL 版没解析出索引，正则可能已失效").isNotEmpty();
        assertThat(h2Indexes)
                .as("索引名在两份 schema 之间漂移了；索引名影响查询计划与排障，必须同名")
                .isEqualTo(mysqlIndexes);
    }

    @Test
    @DisplayName("唯一约束名集合必须一致（MySQL 写 UNIQUE KEY，H2 写 CONSTRAINT ... UNIQUE）")
    void uniqueKeySetsMatch() {
        String mysql = stripComments(read(MYSQL_SCHEMA));
        String h2 = stripComments(read(H2_SCHEMA));

        Set<String> mysqlUnique = collect(INDEX_DECLARATION, mysql).stream()
                .filter(name -> name.startsWith("uk_"))
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        Set<String> h2Unique = collect(INDEX_DECLARATION, h2).stream()
                .filter(name -> name.startsWith("uk_"))
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

        assertThat(mysqlUnique).as("MySQL 版没解析出唯一约束，正则可能已失效").isNotEmpty();
        assertThat(h2Unique)
                .as("唯一约束名不一致：唯一键名是业务语义的一部分（例如 uk_food_code）")
                .isEqualTo(mysqlUnique);
    }

    @Test
    @DisplayName("外键与 CHECK 约束名集合必须一致")
    void namedConstraintSetsMatch() {
        String mysql = stripComments(read(MYSQL_SCHEMA));
        String h2 = stripComments(read(H2_SCHEMA));

        // 只统计 `CONSTRAINT <name> (FOREIGN KEY|CHECK)`；`CONSTRAINT <name> UNIQUE`
        // 属于唯一键，已在 uniqueKeySetsMatch 里单独比对。
        Set<String> mysqlNamed = collect(NAMED_CONSTRAINT, mysql);
        Set<String> h2Named = collect(NAMED_CONSTRAINT, h2);

        assertThat(mysqlNamed).as("MySQL 版没解析出命名约束，正则可能已失效").isNotEmpty();
        assertThat(h2Named).as("外键/CHECK 约束名不一致，排障时会对不上").isEqualTo(mysqlNamed);
    }

    @Test
    @DisplayName("三态一致性 CHECK 约束必须同时存在于两份 schema（数据库层兜底）")
    void statusAmountCheckConstraintExistsInBoth() {
        assertThat(read(MYSQL_SCHEMA))
                .as("MySQL 版缺少 ck_nv_status_amount")
                .contains("ck_nv_status_amount");
        assertThat(read(H2_SCHEMA))
                .as("H2 版缺少 ck_nv_status_amount：应用层漏改时数据库就不再拦截了")
                .contains("ck_nv_status_amount");
    }

    // ------------------------------------------------------------------ 解析

    /**
     * 解析出「表名 → 列名集合」。
     *
     * <p>解析前会先剥掉 {@code COMMENT '...'} 子句：这些注释是中文自由文本，
     * 里面常带括号（例如「营养成分（每100g）」），会让「取 CREATE TABLE 的第一个
     * 右括号」这种粗暴切分提前收尾，把后面的列吞掉。剥掉注释既避免误判，
     * 也不影响本测试要守护的东西（列名）。
     * @param sql 建表脚本全文
     * @return 表名到列名集合的映射（保持出现顺序）
     */
    private static Map<String, Set<String>> parseTables(String sql) {
        Map<String, Set<String>> tables = new LinkedHashMap<>();
        Matcher matcher = CREATE_TABLE.matcher(stripComments(sql));
        while (matcher.find()) {
            String table = matcher.group(1).toLowerCase();
            Set<String> columns = new LinkedHashSet<>();
            for (String rawLine : matcher.group(2).split("\n")) {
                String line = rawLine.trim();
                if (line.isEmpty() || NON_COLUMN_LINE.matcher(line).find()) {
                    continue;
                }
                Matcher column = COLUMN_NAME.matcher(line);
                if (column.find()) {
                    columns.add(column.group(1).toLowerCase());
                }
            }
            tables.put(table, columns);
        }
        return tables;
    }

    /**
     * 剥掉行注释与 {@code COMMENT '...'} 子句。
     * <p>刻意保留行内 {@code --} 之后的换行，避免把两行粘成一行而误判列名。
     * @param sql 原始 SQL
     * @return 不含注释的 SQL
     */
    private static String stripComments(String sql) {
        String withoutBlock = sql.replaceAll("(?s)/\\*.*?\\*/", " ");
        String withoutLine = withoutBlock.replaceAll("(?m)--[^\\n]*", " ");
        return COMMENT_CLAUSE.matcher(withoutLine).replaceAll(" ");
    }

    /**
     * 用给定正则收集第一个捕获组，转小写去重排序。
     * @param pattern 带一个捕获组的正则
     * @param text 待扫描文本
     * @return 名称集合
     */
    private static Set<String> collect(Pattern pattern, String text) {
        Set<String> names = new TreeSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1).toLowerCase());
        }
        return names;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读不到 " + path + "（测试假设工作目录是 backend/）", e);
        }
    }
}
