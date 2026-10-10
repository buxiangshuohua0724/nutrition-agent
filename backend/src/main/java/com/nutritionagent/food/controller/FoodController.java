package com.nutritionagent.food.controller;

import com.nutritionagent.common.api.ApiPaths;
import com.nutritionagent.common.api.PageResult;
import com.nutritionagent.common.api.Result;
import com.nutritionagent.food.dto.CategorySummary;
import com.nutritionagent.food.dto.FoodDetail;
import com.nutritionagent.food.dto.FoodSummary;
import com.nutritionagent.food.service.FoodQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类与食品的公开查询接口（契约 4.1，FR-001 / FR-002 / FR-003）。
 *
 * <p>这些接口<b>全部不需要登录</b>，也<b>绝不依赖任何外部模型</b>：
 * 模型不可用时它们必须照常返回（PRD 6.6）。因此在类级别不加 {@code @RequireRole}。
 */
@RestController
@RequestMapping(ApiPaths.V1)
public class FoodController {

    private final FoodQueryService foodQueryService;

    public FoodController(FoodQueryService foodQueryService) {
        this.foodQueryService = foodQueryService;
    }

    @GetMapping("/categories")
    public Result<List<CategorySummary>> categories() {
        return Result.ok(foodQueryService.listCategories());
    }

    @GetMapping("/categories/{slug}/foods")
    public Result<PageResult<FoodSummary>> categoryFoods(
            @PathVariable String slug,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(foodQueryService.foodsOfCategory(slug, page, pageSize));
    }

    @GetMapping("/foods/search")
    public Result<PageResult<FoodSummary>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String sort) {
        return Result.ok(foodQueryService.search(keyword, categoryId, page, pageSize,
                FoodQueryService.SortMode.parse(sort)));
    }

    @GetMapping("/foods/featured")
    public Result<List<FoodSummary>> featured(
            @RequestParam(defaultValue = "hot") String type,
            @RequestParam(defaultValue = "5") int limit) {
        return Result.ok(foodQueryService.featured(type, limit));
    }

    @GetMapping("/foods/{id}")
    public Result<FoodDetail> detail(@PathVariable Long id) {
        return Result.ok(foodQueryService.detail(id));
    }

    @GetMapping("/foods/{id}/related")
    public Result<List<FoodSummary>> related(
            @PathVariable Long id,
            @RequestParam(defaultValue = "6") int limit) {
        return Result.ok(foodQueryService.related(id, limit));
    }
}
