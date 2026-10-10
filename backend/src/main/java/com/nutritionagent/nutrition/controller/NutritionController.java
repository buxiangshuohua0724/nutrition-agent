package com.nutritionagent.nutrition.controller;

import com.nutritionagent.common.api.ApiPaths;
import com.nutritionagent.common.api.Result;
import com.nutritionagent.nutrition.dto.NutritionCalculation;
import com.nutritionagent.nutrition.dto.NutritionTable;
import com.nutritionagent.nutrition.service.NutritionQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 营养查询与按重量折算（契约 4.2，FR-004）。
 *
 * <p>与 {@code FoodController} 一样不依赖任何外部模型：模型全挂时这两个接口必须照常可用。
 */
@RestController
@RequestMapping(ApiPaths.V1)
public class NutritionController {

    private final NutritionQueryService nutritionQueryService;

    public NutritionController(NutritionQueryService nutritionQueryService) {
        this.nutritionQueryService = nutritionQueryService;
    }

    /** 营养素字典，供后台表单与前端展示单位的唯一来源。契约路径是 {@code GET /nutrition/nutrients}。 */
    @GetMapping("/nutrition/nutrients")
    public Result<List<NutritionQueryService.NutrientView>> nutrients() {
        return Result.ok(nutritionQueryService.listNutrients());
    }

    @GetMapping("/foods/{id}/nutrition")
    public Result<NutritionTable> nutrition(
            @PathVariable Long id,
            @RequestParam(required = false) Long variantId) {
        return Result.ok(nutritionQueryService.nutritionTable(id, variantId));
    }

    @PostMapping("/nutrition/calculate")
    public Result<NutritionCalculation> calculate(@Valid @RequestBody CalculateRequest request) {
        return Result.ok(nutritionQueryService.calculate(
                request.foodId(), request.variantId(), request.weight(), request.weightUnit()));
    }

    /**
     * 折算请求。
     *
     * <p>{@code weight} 的"必须大于 0、不超过 100000"由 {@code NutritionMath} 校验并抛 40002，
     * 这里只保证字段存在——因为「0 或负数」属于业务规则而不是格式问题，
     * 交给业务层才能给出统一的中文错误码。
     */
    public record CalculateRequest(
            @NotNull(message = "foodId 不能为空") Long foodId,
            Long variantId,
            @NotNull(message = "weight 不能为空") BigDecimal weight,
            @NotNull(message = "weightUnit 不能为空") String weightUnit) {
    }
}
