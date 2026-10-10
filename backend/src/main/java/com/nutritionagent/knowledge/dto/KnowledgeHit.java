package com.nutritionagent.knowledge.dto;

import java.util.List;

/**
 * 知识库检索结果（契约 4.4）。
 *
 * <p>只检索 {@code publish_status = PUBLISHED}、{@code review_status = APPROVED}
 * 且未过 {@code valid_until} 的内容——未审核的知识不得出现在用户回答的依据里。
 */
public record KnowledgeHit(
        Long docId,
        Long chunkId,
        String title,
        String heading,
        String snippet,
        String category,
        Double score,
        Source source) {

    /** 引用的来源档案。字段名与契约 4.4 一致。 */
    public record Source(String name, String publisher, String version, String url, String applicableScope) {
    }

    public record KnowledgeSearchResult(List<KnowledgeHit> list, int page, int pageSize, long total, int totalPages) {
    }
}
