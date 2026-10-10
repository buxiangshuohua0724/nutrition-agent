package com.nutritionagent.knowledge.controller;

import com.nutritionagent.common.api.ApiPaths;
import com.nutritionagent.common.api.PageResult;
import com.nutritionagent.common.api.Result;
import com.nutritionagent.knowledge.dto.KnowledgeHit;
import com.nutritionagent.knowledge.service.KnowledgeSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库检索（契约 4.4，公开）。
 *
 * <p>关键词与分类都允许为空：都为空时返回已发布知识列表，便于前端在用户没输入时
 * 展示"可以先看这些"，而不是一个空页面。
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/knowledge")
public class KnowledgeController {

    private final KnowledgeSearchService knowledgeSearchService;

    public KnowledgeController(KnowledgeSearchService knowledgeSearchService) {
        this.knowledgeSearchService = knowledgeSearchService;
    }

    @GetMapping("/search")
    public Result<PageResult<KnowledgeHit>> search(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String category,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(knowledgeSearchService.search(keyword, category, page, pageSize));
    }
}
