"""从 docs/db/schema.sql 生成 MyBatis-Plus 实体类。

为什么用脚本而不是手写：21 张表、245 个列，手写必然会抄错列名或漏列，
而列名抄错在编译期发现不了、只在运行期报 "Unknown column"。
生成后由 SchemaParityTest 一类的门禁守护名实一致。

列类型 → Java 类型的映射是显式的，遇到未登记的类型会直接抛错退出，
绝不静默回退成 String（那会让金额/比例类字段悄悄失去精度）。
"""

import io
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = r"D:\ds_harness_work\nutrition-agent"
SCHEMA = os.path.join(ROOT, "docs", "db", "schema.sql")
OUT_ROOT = os.path.join(ROOT, "backend", "src", "main", "java", "com", "nutritionagent")

# 表名 -> (包名, 类名, 中文说明)
TABLES = {
    "sys_user": ("user", "SysUser", "用户账户（FR-007）"),
    "sys_role": ("user", "SysRole", "角色字典（PRD 3.1，四类角色权限必须可区分）"),
    "sys_user_role": ("user", "SysUserRole", "用户-角色关联（复合主键，无 id 列）"),
    "sys_auth_token": ("user", "SysAuthToken", "登录令牌（可撤销、可过期）"),
    "user_profile": ("user", "UserProfile", "用户资料与饮食偏好（FR-007，全部业务字段可空）"),
    "food_category": ("food", "FoodCategory", "食品分类（FR-001/FR-002）"),
    "food": ("food", "Food", "食品主表（FR-003）— 只存“食品是什么”，不存任何营养数值"),
    "food_alias": ("food", "FoodAlias", "食品别名（FR-002 搜索范围）"),
    "food_image": ("food", "FoodImage", "食品图片（FR-003）"),
    "food_state_variant": ("food", "FoodStateVariant", "食品形态 [D1]：营养数据的最小承载单元"),
    "nutrient": ("nutrition", "Nutrient", "营养素字典 [D3]：单位与换算在此定义"),
    "data_source": ("nutrition", "DataSource", "数据来源 [D4]：营养数据可追溯率 100% 的基础"),
    "nutrition_value": ("nutrition", "NutritionValue", "营养数值 [D2]：三态 + 来源必填"),
    "knowledge_doc": ("knowledge", "KnowledgeDoc", "知识文档"),
    "knowledge_chunk": ("knowledge", "KnowledgeChunk", "知识分块（检索与引用的最小粒度）"),
    "chat_session": ("agent", "ChatSession", "问答会话（含游客会话）"),
    "chat_message": ("agent", "ChatMessage", "消息（结构化输出 + 工具轨迹）"),
    "image_recognition": ("agent", "ImageRecognition", "图片识别记录（只存候选，不存营养数值）"),
    "user_favorite": ("user", "UserFavorite", "食品收藏（FR-008）"),
    "admin_audit_log": ("admin", "AdminAuditLog", "后台操作审计（PRD 9.3）"),
    "operation_log": ("admin", "OperationLog", "操作日志（禁止写入敏感信息）"),
}

# 需要生成实体的表里，哪些列不出现在实体中（生成列由数据库维护，写入会报错）
SKIP_COLUMNS = {"gd_deleted_key"}

# 明确登记的类型映射。key 是去掉括号参数后的大写类型名。
SIMPLE_TYPES = {
    "BIGINT": "Long",
    "INT": "Integer",
    "TINYINT": "Boolean",
    "DATETIME": "LocalDateTime",
    "DATE": "LocalDate",
    "DECIMAL": "BigDecimal",
    "TEXT": "String",
    "JSON": "String",
}


def camel(name):
    parts = name.split("_")
    return parts[0] + "".join(p.capitalize() for p in parts[1:])


def cap(name):
    return name[0].upper() + name[1:]


def read_schema():
    with io.open(SCHEMA, encoding="utf-8") as fh:
        return fh.read()


def parse_columns(sql):
    """按 CREATE TABLE 段解析列定义。返回 {表名: [(列名, 类型, 是否可空, 注释)]}"""
    result = {}
    pattern = re.compile(r"CREATE TABLE `(\w+)` \((.*?)\n\) ENGINE", re.DOTALL)
    for match in pattern.finditer(sql):
        table = match.group(1)
        body = match.group(2)
        columns = []
        for raw_line in body.split("\n"):
            line = raw_line.strip().rstrip(",")
            if not line.startswith("`"):
                continue
            m = re.match(r"`(\w+)`\s+([A-Za-z]+)(\([^)]*\))?(.*)$", line)
            if not m:
                continue
            col, base, _args, rest = m.group(1), m.group(2).upper(), m.group(3), m.group(4)
            column_key = "VIRTUAL" if "GENERATED ALWAYS" in rest.upper() else None
            nullable = "NOT NULL" not in rest
            comment = ""
            cm = re.search(r"COMMENT '([^']*)'", rest)
            if cm:
                comment = cm.group(1)
            columns.append((col, base, nullable, comment, column_key))
        result[table] = columns
    return result


def java_type(base, comment):
    if base in SIMPLE_TYPES:
        return SIMPLE_TYPES[base]
    if base == "VARCHAR" or base == "CHAR":
        return "String"
    raise SystemExit("未登记的类型 %s（列注释：%s），请在 SIMPLE_TYPES 中显式登记" % (base, comment))


def render(table, pkg, clazz, doc, columns):
    lines = []
    lines.append("package com.nutritionagent.%s.entity;" % pkg)
    lines.append("")
    lines.append("import com.baomidou.mybatisplus.annotation.IdType;")
    lines.append("import com.baomidou.mybatisplus.annotation.TableField;")
    lines.append("import com.baomidou.mybatisplus.annotation.TableId;")
    lines.append("import com.baomidou.mybatisplus.annotation.TableLogic;")
    lines.append("import com.baomidou.mybatisplus.annotation.TableName;")
    lines.append("import lombok.Data;")
    lines.append("")
    types = set()
    for col, base, _n, comment, _g in columns:
        if col in SKIP_COLUMNS:
            continue
        jt = java_type(base, comment)
        types.add(jt)
    for jt in sorted(types):
        if jt in ("LocalDateTime", "LocalDate"):
            lines.append("import java.time.%s;" % jt)
        elif jt == "BigDecimal":
            lines.append("import java.math.BigDecimal;")
    # java.* 的 import 放在 com.baomidou 之后不符合惯例，重排：先 java，再第三方
    header = [l for l in lines if l.startswith("package") or l == ""]
    imports_java = sorted({l for l in lines if l.startswith("import java")})
    imports_other = sorted({l for l in lines if l.startswith("import") and not l.startswith("import java")})
    body_start = lines.index("import com.baomidou.mybatisplus.annotation.IdType;")

    out = []
    out.append("package com.nutritionagent.%s.entity;" % pkg)
    out.append("")
    out.extend(imports_other)
    if imports_java:
        out.append("")
        out.extend(imports_java)
    out.append("")
    out.append("/**")
    out.append(" * %s（{@code %s}）。" % (doc, table))
    out.append(" *")
    out.append(" * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，")
    out.append(" * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。")
    out.append(" */")
    out.append("@Data")
    out.append('@TableName("%s")' % table)
    out.append("public class %s {" % clazz)

    has_id = any(col == "id" for col, _b, _n, _c, _g in columns)
    for col, base, nullable, comment, generated in columns:
        if col in SKIP_COLUMNS:
            continue
        out.append("")
        jt = java_type(base, comment)
        note = comment.replace("*/", "*")
        if note:
            suffix = "" if nullable else "（必填）"
            out.append("    /** %s%s */" % (note, suffix))
        else:
            out.append("    /** 列 {@code %s}，%s。 */" % (col, "可空" if nullable else "非空"))
        if col == "id" and has_id:
            out.append("    @TableId(type = IdType.AUTO)")
        if col == "deleted_at":
            out.append('    @TableLogic(value = "null", delval = "now()")')
        if col != camel(col):
            out.append('    @TableField("%s")' % col)
        out.append("    private %s %s;" % (jt, camel(col)))

    out.append("}")
    out.append("")
    return "\n".join(out)


def main():
    sql = read_schema()
    parsed = parse_columns(sql)
    missing = [t for t in TABLES if t not in parsed]
    if missing:
        raise SystemExit("schema 里找不到这些表：%s" % missing)

    written = []
    for table, (pkg, clazz, doc) in TABLES.items():
        columns = parsed[table]
        text = render(table, pkg, clazz, doc, columns)
        target_dir = os.path.join(OUT_ROOT, pkg, "entity")
        os.makedirs(target_dir, exist_ok=True)
        path = os.path.join(target_dir, clazz + ".java")
        with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(text)
        written.append((path, len(columns)))

    print("已生成 %d 个实体：" % len(written))
    for path, count in written:
        print("  %-72s %2d 列" % (os.path.relpath(path, ROOT), count))


if __name__ == "__main__":
    main()
