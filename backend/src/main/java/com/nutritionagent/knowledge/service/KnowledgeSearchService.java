package com.nutritionagent.knowledge.service;

import com.nutritionagent.common.api.ErrorCode;
import com.nutritionagent.common.api.PageResult;
import com.nutritionagent.common.exception.BusinessException;
import com.nutritionagent.knowledge.dto.KnowledgeHit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库检索（FR-010）。
 *
 * <p>实现策略（PRD FR-010「先关键词、后语义」）：本阶段用<b>关键词匹配</b>，
 * 因为种子里还没有 embedding（{@code embedding_model} 全为 NULL），
 * 有向量数据之前谈语义检索是不成立的。命中计分方式是完全确定的：
 * 标题命中 > 小标题命中 > 关键词列命中 > 正文命中，命中的字段越多分越高。
 *
 * <p>知识库无相关内容时返回空页，而不是 40403——"没找到"是正常的检索结果，
 * 404 语义属于"资源不存在"。这个区别在前端会体现为"换几个关键词试试"的空态。
 */
@Service
public class KnowledgeSearchService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int SNIPPET_LENGTH = 200;

    private final JdbcTemplate jdbc;

    public KnowledgeSearchService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResult<KnowledgeHit> search(String keyword, String category, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "分页参数不合法：page ≥ 1，pageSize ∈ [1, " + MAX_PAGE_SIZE + "]");
        }
        String kw = keyword == null ? "" : keyword.trim();
        String cat = category == null ? "" : category.trim();

        // 关键词与分类都是可选条件；两者都空时返回已发布知识的列表（便于后台预览与空搜索兜底）
        StringBuilder sql = new StringBuilder("""
                SELECT kc.id AS chunk_id, kc.doc_id, kc.chunk_index, kc.heading, kc.content, kc.keywords,
                       kd.title, kd.category,
                       ds.name AS source_name, ds.publisher, ds.version, ds.url, ds.applicable_scope
                FROM knowledge_chunk kc
                JOIN knowledge_doc kd ON kd.id = kc.doc_id
                LEFT JOIN data_source ds ON ds.id = kd.source_id
                WHERE kd.deleted_at IS NULL
                  AND kd.publish_status = 'PUBLISHED'
                  AND kd.review_status = 'APPROVED'
                  AND (kd.valid_until IS NULL OR kd.valid_until >= CURRENT_TIMESTAMP)
                """);
        List<Object> args = new ArrayList<>();
        if (!cat.isEmpty()) {
            sql.append(" AND kd.category = ?");
            args.add(cat);
        }
        if (!kw.isEmpty()) {
            String like = "%" + escapeLike(kw) + "%";
            sql.append("""
                     AND (kd.title LIKE ? ESCAPE '\\'
                       OR COALESCE(kc.heading, '') LIKE ? ESCAPE '\\'
                       OR COALESCE(kc.keywords, '') LIKE ? ESCAPE '\\'
                       OR kc.content LIKE ? ESCAPE '\\')
                    """);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY kd.id, kc.chunk_index");

        List<KnowledgeHit> hits = jdbc.query(sql.toString(), (rs, rowNum) -> {
            String heading = rs.getString("heading");
            String content = rs.getString("content");
            String keywords = rs.getString("keywords");
            double score = score(kw, rs.getString("title"), heading, keywords, content);
            return new KnowledgeHit(
                    rs.getLong("doc_id"),
                    rs.getLong("chunk_id"),
                    rs.getString("title"),
                    heading,
                    snippet(content),
                    rs.getString("category"),
                    score,
                    new KnowledgeHit.Source(
                            rs.getString("source_name"),
                            rs.getString("publisher"),
                            rs.getString("version"),
                            rs.getString("url"),
                            rs.getString("applicable_scope")));
        }, args.toArray());

        if (!kw.isEmpty()) {
            // 相关度降序；同分按 chunkId 稳定排序（否则同一查询两次顺序可能不同）
            hits.sort((a, b) -> {
                int cmp = Double.compare(b.score(), a.score());
                return cmp != 0 ? cmp : Long.compare(a.chunkId(), b.chunkId());
            });
        }

        int total = hits.size();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        return PageResult.of(hits.subList(from, to), page, pageSize, total);
    }

    /**
     * 确定性计分：命中不同字段给不同权重，命中次数累加。
     *
     * <p>不做"相关度算法"的包装——这里的分数只用于排序，不对外声称是相似度。
     */
    private static double score(String keyword, String title, String heading, String keywords, String content) {
        if (keyword == null || keyword.isEmpty()) {
            return 0d;
        }
        String lower = keyword.toLowerCase();
        double score = 0d;
        score += 5d * countOccurrences(lower, title);
        score += 3d * countOccurrences(lower, heading);
        score += 2d * countOccurrences(lower, keywords);
        score += 1d * countOccurrences(lower, content);
        return score;
    }

    private static int countOccurrences(String keyword, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        String lower = text.toLowerCase();
        int count = 0;
        int index = lower.indexOf(keyword);
        while (index >= 0) {
            count++;
            index = lower.indexOf(keyword, index + keyword.length());
        }
        return count;
    }

    private static String snippet(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        return trimmed.length() <= SNIPPET_LENGTH ? trimmed : trimmed.substring(0, SNIPPET_LENGTH) + "…";
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
