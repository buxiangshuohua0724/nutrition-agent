"""生成 MyBatis-Plus Mapper 接口。

只给「业务上会被读写」的表生成 Mapper：审计表与操作日志用 JdbcTemplate
写入（它们没有实体业务语义，走 ORM 只会多一层无用抽象），因此不在此列。
"""

import io
import os
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = r"D:\ds_harness_work\nutrition-agent"
OUT_ROOT = os.path.join(ROOT, "backend", "src", "main", "java", "com", "nutritionagent")

MAPPERS = [
    ("user", "SysUser"),
    ("user", "SysAuthToken"),
    ("user", "UserProfile"),
    ("user", "UserFavorite"),
    ("food", "FoodCategory"),
    ("food", "Food"),
    ("food", "FoodAlias"),
    ("food", "FoodImage"),
    ("food", "FoodStateVariant"),
    ("nutrition", "Nutrient"),
    ("nutrition", "DataSource"),
    ("nutrition", "NutritionValue"),
    ("knowledge", "KnowledgeDoc"),
    ("knowledge", "KnowledgeChunk"),
    ("agent", "ChatSession"),
    ("agent", "ChatMessage"),
    ("agent", "ImageRecognition"),
]

DOCS = {
    "SysUser": "用户账户。逻辑删除由 deletedAt 上的 @TableLogic 自动生效。",
    "SysAuthToken": "登录令牌。该表无逻辑删除列，吊销靠 revokedAt。",
    "UserProfile": "用户资料。该表无逻辑删除列，随账户物理删除。",
    "UserFavorite": "食品收藏。取消收藏是逻辑删除（deleted_at 置值）。",
    "FoodCategory": "食品分类。",
    "Food": "食品主表。公开查询必须带 publish_status/review_status/deleted_at 三重条件。",
    "FoodAlias": "食品别名。别名不得劫持标准名，排序权重在服务层处理。",
    "FoodImage": "食品图片。",
    "FoodStateVariant": "食品形态，营养数据的最小承载单元。",
    "Nutrient": "营养素字典，单位与换算在此定义，营养数据不重复存单位。",
    "DataSource": "数据来源。该表无逻辑删除列：来源不允许删除，只能停用。",
    "NutritionValue": "营养数值。三态由 value_status 表达，amount 的合法性由 CHECK 约束兜底。",
    "KnowledgeDoc": "知识文档。检索只返回 PUBLISHED + APPROVED 且未过期的内容。",
    "KnowledgeChunk": "知识分块，引用标识 KDOC_<docId>#chunk<chunkId> 的最小粒度（chunkId 是 knowledge_chunk.id 全局主键，不是 chunk_index）。",
    "ChatSession": "问答会话，含游客会话。",
    "ChatMessage": "会话消息。结构化输出与工具轨迹以 JSON 文本存储。",
    "ImageRecognition": "图片识别记录。只存候选食品，不存任何营养数值。",
}


def main():
    for pkg, clazz in MAPPERS:
        target_dir = os.path.join(OUT_ROOT, pkg, "mapper")
        os.makedirs(target_dir, exist_ok=True)
        path = os.path.join(target_dir, clazz + "Mapper.java")
        text = "\n".join([
            "package com.nutritionagent.%s.mapper;" % pkg,
            "",
            "import com.baomidou.mybatisplus.core.mapper.BaseMapper;",
            "import com.nutritionagent.%s.entity.%s;" % (pkg, clazz),
            "import org.apache.ibatis.annotations.Mapper;",
            "",
            "/** %s */" % DOCS[clazz],
            "@Mapper",
            "public interface %sMapper extends BaseMapper<%s> {" % (clazz, clazz),
            "}",
            "",
        ])
        with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(text)
    print("已生成 %d 个 Mapper 接口" % len(MAPPERS))


if __name__ == "__main__":
    main()
