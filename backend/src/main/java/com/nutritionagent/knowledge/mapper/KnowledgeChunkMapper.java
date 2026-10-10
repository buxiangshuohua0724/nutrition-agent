package com.nutritionagent.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nutritionagent.knowledge.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识分块，引用标识 {@code KDOC_<docId>#chunk<chunkId>} 的最小粒度。
 *
 * <p>这里的 {@code chunkId} 是 {@code knowledge_chunk.id}（全局自增主键），
 * <b>不是</b>文档内的 {@code chunk_index}——两者在 docId ≥ 2 时不再相等，写成
 * {@code chunkIndex} 会让引用解析到**另一篇文档**的分块，而界面上看不出来
 * （标题是引用方自己写的）。构造逻辑见
 * {@code AgentToolkit.java:144}（{@code String.format("%04d", id)}）。
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {
}
