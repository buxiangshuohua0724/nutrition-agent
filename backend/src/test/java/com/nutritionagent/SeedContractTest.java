package com.nutritionagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 种子数据与前端素材的契约测试。
 *
 * <p>项目里有一条容易被忽视的隐式契约：<b>数据库里的分类 slug 与食品 code，
 * 必须和前端 `src/assets/` 下的文件名逐字对应</b>，否则前端会静默丢图——
 * 页面照常渲染，只是图片位置空着，不会抛任何异常，靠人眼看很难在 review 里发现。
 *
 * <p>同一份校验在 `scripts/check_contract.py` 里也做了一遍（供改完种子数据后
 * 快速自检，不需要起 JVM）。这里把它固化成测试，是为了让 `mvn test` 成为
 * 唯一的、无法被跳过的门禁：CI 或本地只要跑测试就能发现漂移。
 *
 * <p>另外还守护 PRD 的核心约束：<b>「未知」不等于 0</b>。种子里刻意保留了
 * 一批 MISSING（未录入）与 ZERO（真实的零）样本，此处断言两者都不为空，
 * 否则三态模型就退化成两态，PRD 4.4 的要求将无从验证。
 */
@SpringBootTest
@ActiveProfiles("dev")
class SeedContractTest {

    /** 前端插画目录：`frontend/src/assets/foods/<code>.svg`。 */
    private static final Path FOOD_ASSETS = PROJECT_ROOT().resolve("frontend/src/assets/foods");

    /** 前端分类徽章目录：`frontend/src/assets/categories/<slug>.svg`。 */
    private static final Path CATEGORY_ASSETS = PROJECT_ROOT().resolve("frontend/src/assets/categories");

    @Autowired
    private JdbcTemplate jdbc;

    private static Path PROJECT_ROOT() {
        // 测试的工作目录是 backend/
        return Paths.get("..").toAbsolutePath().normalize();
    }

    @Test
    @DisplayName("每个 food.code 都必须有同名插画文件（否则前端静默丢图）")
    void everyFoodCodeHasAnIllustration() {
        Set<String> codes = new TreeSet<>(jdbc.queryForList("SELECT code FROM food", String.class));
        Set<String> files = svgBaseNames(FOOD_ASSETS);

        assertThat(codes).as("种子里没有食品，测试失去意义").isNotEmpty();

        Set<String> missing = new TreeSet<>(codes);
        missing.removeAll(files);
        assertThat(missing)
                .as("下列 food.code 在 %s 下没有对应 .svg，前端会显示占位图", FOOD_ASSETS)
                .isEmpty();
    }

    @Test
    @DisplayName("每个 food_category.slug 都必须有同名分类徽章（否则分类页丢图）")
    void everyCategorySlugHasABadge() {
        Set<String> slugs = new TreeSet<>(jdbc.queryForList("SELECT slug FROM food_category", String.class));
        Set<String> files = svgBaseNames(CATEGORY_ASSETS);

        assertThat(slugs).as("种子里没有分类").isNotEmpty();

        Set<String> missing = new TreeSet<>(slugs);
        missing.removeAll(files);
        assertThat(missing)
                .as("下列 food_category.slug 在 %s 下没有对应 .svg", CATEGORY_ASSETS)
                .isEmpty();
    }

    @Test
    @DisplayName("分类 slug 必须用复数形式，且「水产」是 aquatic 而非 seafood")
    void categorySlugsUseTheAgreedSpelling() {
        List<String> slugs = jdbc.queryForList("SELECT slug FROM food_category ORDER BY id", String.class);

        assertThat(slugs).containsExactly(
                "vegetables", "fruits", "meat", "aquatic", "eggs",
                "grains", "dairy", "nuts", "legumes", "others");
        assertThat(slugs)
                .as("历史写法不得回流；前端虽有别名表兜底，但别名表只应服务于外部导入")
                .doesNotContain("vegetable", "seafood", "egg", "grain", "nut", "legume", "other");
    }

    @Test
    @DisplayName("三态样本都要存在：MISSING 与 ZERO 缺一不可")
    void seedKeepsBothMissingAndZeroSamples() {
        Integer missing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM nutrition_value WHERE value_status = 'MISSING'", Integer.class);
        Integer zero = jdbc.queryForObject(
                "SELECT COUNT(*) FROM nutrition_value WHERE value_status = 'ZERO'", Integer.class);

        assertThat(missing).as("种子里没有 MISSING 样本，「暂无数据」这条路径无法被验证").isPositive();
        assertThat(zero).as("种子里没有 ZERO 样本，会把「真实的零」和「未知」混为一谈").isPositive();
    }

    @Test
    @DisplayName("ZERO 必须真的是 0，且不得为 NULL")
    void zeroStatusAlwaysCarriesZeroAmount() {
        Integer broken = jdbc.queryForObject(
                "SELECT COUNT(*) FROM nutrition_value WHERE value_status = 'ZERO' AND (amount IS NULL OR amount <> 0)",
                Integer.class);
        assertThat(broken).as("ZERO 行的 amount 必须是数值 0").isZero();
    }

    @Test
    @DisplayName("三态一致性由数据库 CHECK 约束兜底：应用层漏改时插入必须失败")
    void checkConstraintRejectsInconsistentStatus() {
        // 造一条「声称 MISSING 却带着数值」的记录，数据库应当直接拒绝。
        // 这条断言的价值在于：即使将来有人绕过 Service 层直接写 SQL，也不会污染数据。
        Integer anyVariant = jdbc.queryForObject("SELECT MIN(id) FROM food_state_variant", Integer.class);
        Integer anyNutrient = jdbc.queryForObject("SELECT MIN(id) FROM nutrient", Integer.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO nutrition_value "
                        + "(id, variant_id, nutrient_id, reference_amount, reference_unit, value_status, amount, source_id) "
                        + "VALUES (999901, ?, ?, 100, 'g', 'MISSING', 12.5, 1)",
                anyVariant, anyNutrient))
                .as("ck_nv_status_amount 没能拦住「MISSING 带数值」的脏数据")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("知识库正文里不得混入 SQL 注释残留（生成种子数据时的拼接 bug）")
    void knowledgeChunksDoNotLeakGeneratorComments() {
        // 判据是「正文以 -- 结尾」而不是「正文含 --」：知识分块的正文里本来就允许
        // 出现破折号（中文习惯的「——」以及区间写法都可能带 --），
        // 但生成脚本把 SQL 注释拼进 content 时，残留总是落在字符串末尾。
        Integer leaked = jdbc.queryForObject(
                "SELECT COUNT(*) FROM knowledge_chunk WHERE TRIM(content) LIKE '%--'", Integer.class);
        assertThat(leaked)
                .as("knowledge_chunk.content 以 `--` 结尾：多半是把种子 SQL 的内部注释拼进了正文")
                .isZero();
    }

    @Test
    @DisplayName("每个形态都必须挂在一个存在的食品上（外键之外再查一次归属）")
    void everyVariantBelongsToAnExistingFood() {
        Integer orphans = jdbc.queryForObject(
                "SELECT COUNT(*) FROM food_state_variant v "
                        + "LEFT JOIN food f ON f.id = v.food_id WHERE f.id IS NULL", Integer.class);
        assertThat(orphans).isZero();
    }

    @Test
    @DisplayName("别名不得重复指向同一食品的同一种写法")
    void aliasesAreUniquePerFood() {
        Integer duplicates = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ("
                        + "  SELECT food_id, alias FROM food_alias WHERE deleted_at IS NULL "
                        + "  GROUP BY food_id, alias HAVING COUNT(*) > 1"
                        + ") t", Integer.class);
        assertThat(duplicates).as("同一食品下出现重复别名，会影响搜索命中与权重计算").isZero();
    }

    @Test
    @DisplayName("演示账号的密码散列必须是 BCrypt，不允许明文")
    void demoAccountsUseBcryptHashes() {
        List<String> hashes = jdbc.queryForList("SELECT password_hash FROM sys_user", String.class);
        assertThat(hashes).isNotEmpty();
        assertThat(hashes)
                .as("出现非 BCrypt 的密码存储（$2a$/$2b$/$2y$ 开头），演示数据也不能破例")
                .allMatch(hash -> hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"));
    }

    @Test
    @DisplayName("系统管理员账号必须同时具备后台所需的两个角色，否则进不了后台")
    void adminAccountHasBothAdminRoles() {
        List<String> codes = jdbc.queryForList(
                "SELECT r.code FROM sys_user u "
                        + "JOIN sys_user_role ur ON ur.user_id = u.id "
                        + "JOIN sys_role r ON r.id = ur.role_id "
                        + "WHERE u.username = 'admin' ORDER BY r.code", String.class);
        assertThat(codes)
                .as("演示 admin 账号需要 SYS_ADMIN + CONTENT_ADMIN 两个角色")
                .containsExactly("CONTENT_ADMIN", "SYS_ADMIN");
    }

    /**
     * 列出目录下全部 `.svg` 的文件名主干。
     * @param dir 素材目录
     * @return 不含扩展名的文件名集合
     */
    private static Set<String> svgBaseNames(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".svg"))
                    .map(name -> name.substring(0, name.length() - ".svg".length()))
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "读不到素材目录 " + dir + "（测试假设工作目录是 backend/，且前端素材已生成）", e);
        }
    }

    /*
     * 说明：本类只做「数据库 ↔ 前端素材文件名」的比对，不解析前端源码。
     * 前端侧的语法与编码检查由 scripts/check_contract.py 与
     * frontend/scripts/verify-sfc.mjs 负责，避免同一件事有两套实现。
     */
}
