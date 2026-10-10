package com.nutritionagent.admin.controller;

import com.nutritionagent.admin.service.AdminService;
import com.nutritionagent.common.api.ApiPaths;
import com.nutritionagent.common.api.PageResult;
import com.nutritionagent.common.api.Result;
import com.nutritionagent.common.file.FileStorageService;
import com.nutritionagent.common.file.ImageValidator;
import com.nutritionagent.common.security.RequireRole;
import com.nutritionagent.common.security.UserContext;
import com.nutritionagent.common.security.UserPrincipal;
import com.nutritionagent.common.security.UserRole;
import com.nutritionagent.food.dto.FoodDetail;
import com.nutritionagent.food.service.FoodQueryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 后台内容管理接口。契约第四章 4.7。
 *
 * <p><b>类级 {@link RequireRole} 是本层的唯一入口检查。</b>这里不写任何
 * {@code if (user.isAdmin())} ——「哪些接口属于后台」应当能在代码里被机械检索出来
 * （一个注解扫一遍即可），散落的条件判断做不到这一点，漏掉一处就是一次越权。</p>
 *
 * <p>档位取 {@link UserRole#CONTENT_ADMIN}：内容管理员即可维护食品、营养、知识库与审核，
 * 这与 PRD 中「系统管理员与内容管理员初期可由同一人担任、但权限设计上必须可区分」一致——
 * 需要更高档位的接口（如系统设置）将来单独标注 {@code SYS_ADMIN}，本控制器目前没有这类接口。</p>
 *
 * <p>食品详情的读取刻意复用 {@link FoodQueryService#detail(Long, boolean)} 的后台开关，
 * 而不是另写一份「草稿详情」查询：结构完全一致，才不会出现后台能看到的字段与前台不一致。</p>
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/admin")
@RequireRole(UserRole.CONTENT_ADMIN)
public class AdminController {

    private final AdminService adminService;
    private final FoodQueryService foodQueryService;
    private final ImageValidator imageValidator;
    private final FileStorageService storage;

    public AdminController(AdminService adminService,
                           FoodQueryService foodQueryService,
                           ImageValidator imageValidator,
                           FileStorageService storage) {
        this.adminService = adminService;
        this.foodQueryService = foodQueryService;
        this.imageValidator = imageValidator;
        this.storage = storage;
    }

    // ================================================================ 食品

    /** 食品列表（含草稿）。契约 {@code GET /admin/foods}。 */
    @GetMapping("/foods")
    public Result<PageResult<Map<String, Object>>> listFoods(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "publishStatus", required = false) String publishStatus,
            @RequestParam(name = "reviewStatus", required = false) String reviewStatus,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.ok(adminService.listFoods(keyword, categoryId, publishStatus, reviewStatus, page, pageSize));
    }

    /**
     * 后台食品详情（含草稿与未发布的形态）。
     *
     * <p>后台编辑界面必须能打开一份还没发布的食品，否则「新建 → 编辑 → 提交审核」这条链路
     * 根本走不通——前台接口对草稿一律 40401，正是为了避免泄露草稿是否存在。</p>
     */
    @GetMapping("/foods/{id}")
    public Result<FoodDetail> getFood(@PathVariable("id") Long id) {
        return Result.ok(foodQueryService.detail(id, true));
    }

    @PostMapping("/foods")
    public Result<Map<String, Object>> createFood(@RequestBody Map<String, Object> body) {
        return Result.ok(adminService.createFood(body, principal()));
    }

    @PutMapping("/foods/{id}")
    public Result<Map<String, Object>> updateFood(@PathVariable("id") Long id,
                                                  @RequestBody Map<String, Object> body) {
        return Result.ok(adminService.updateFood(id, body, principal()));
    }

    @DeleteMapping("/foods/{id}")
    public Result<Void> deleteFood(@PathVariable("id") Long id) {
        adminService.offlineFood(id, principal());
        return Result.ok();
    }

    @PostMapping("/foods/{id}/review")
    public Result<Map<String, Object>> reviewFood(@PathVariable("id") Long id,
                                                  @RequestBody ReviewRequest request) {
        boolean approved = Boolean.TRUE.equals(request.approved());
        return Result.ok(adminService.reviewFood(id, approved, request.comment(), principal()));
    }

    @PostMapping("/foods/{id}/publish")
    public Result<Map<String, Object>> publishFood(@PathVariable("id") Long id) {
        return Result.ok(adminService.publishFood(id, principal()));
    }

    // ============================================================ 形态与营养

    @PostMapping("/foods/{id}/variants")
    public Result<Map<String, Object>> createVariant(@PathVariable("id") Long foodId,
                                                     @RequestBody Map<String, Object> body) {
        return Result.ok(adminService.createVariant(foodId, body, principal()));
    }

    /** 形态营养数据（含草稿行）。契约 {@code GET /admin/variants/{id}/nutrition}。 */
    @GetMapping("/variants/{id}/nutrition")
    public Result<Map<String, Object>> variantNutrition(@PathVariable("id") Long variantId) {
        return Result.ok(adminService.variantNutrition(variantId));
    }

    /**
     * 保存形态营养数据。请求体 {@code {items: [{nutrientCode, valueStatus, amount, unit, sourceId}]}}。
     *
     * <p>缺少 {@code sourceId} 的项会被服务层拒绝（40001）——营养数据必须可追溯到来源，
     * 这是本项目的硬约束，不能在后台开一个「先填数值、来源以后补」的口子。</p>
     */
    @PutMapping("/variants/{id}/nutrition")
    public Result<Map<String, Object>> updateVariantNutrition(@PathVariable("id") Long variantId,
                                                              @RequestBody NutritionUpdateRequest request) {
        return Result.ok(adminService.updateVariantNutrition(variantId, request.items(), principal()));
    }

    // ================================================================ 图片

    @PostMapping("/foods/{id}/images")
    public Result<Map<String, Object>> uploadImage(@PathVariable("id") Long foodId,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestParam(name = "cover", defaultValue = "false") boolean cover) {
        // 与用户上传走同一套校验：先看大小与声明的 Content-Type，再验魔数。
        // 只信扩展名会让一个改名成 .png 的可执行文件落进存储目录。
        ImageValidator.Detected detected = imageValidator.validate(file);
        FileStorageService.Stored stored = storage.store(file, "food", detected.extension());
        return Result.ok(adminService.addImage(foodId, stored.publicUrl(),
                file.getOriginalFilename(), cover, principal()));
    }

    @DeleteMapping("/images/{id}")
    public Result<Void> deleteImage(@PathVariable("id") Long imageId) {
        adminService.deleteImage(imageId, principal());
        return Result.ok();
    }

    // ============================================================== 知识库

    @GetMapping("/knowledge")
    public Result<PageResult<Map<String, Object>>> listKnowledge(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.ok(adminService.listKnowledge(keyword, category, page, pageSize));
    }

    @PostMapping("/knowledge")
    public Result<Map<String, Object>> createKnowledge(@RequestBody Map<String, Object> body) {
        return Result.ok(adminService.createKnowledge(body, principal()));
    }

    @PutMapping("/knowledge/{id}")
    public Result<Map<String, Object>> updateKnowledge(@PathVariable("id") Long id,
                                                       @RequestBody Map<String, Object> body) {
        return Result.ok(adminService.updateKnowledge(id, body, principal()));
    }

    /**
     * 重建检索索引。当前实现是关键词检索，如实返回「无需重建外部索引」而不假装重建了向量库。
     */
    @PostMapping("/knowledge/reindex")
    public Result<Map<String, Object>> reindex() {
        return Result.ok(adminService.reindex(principal()));
    }

    // ============================================================ 审计与用户

    @GetMapping("/audit-logs")
    public Result<PageResult<Map<String, Object>>> auditLogs(
            @RequestParam(name = "operatorId", required = false) Long operatorId,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.ok(adminService.auditLogs(operatorId, action, from, to, page, pageSize));
    }

    /**
     * 用户列表。档位要求 {@link UserRole#SYS_ADMIN} —— 查看全部账号属于系统管理职责，
     * 内容管理员只该看到内容，不该看到用户清单（与 PRD 的角色可区分要求一致）。
     */
    @GetMapping("/users")
    @RequireRole(UserRole.SYS_ADMIN)
    public Result<PageResult<Map<String, Object>>> listUsers(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.ok(adminService.listUsers(keyword, role, page, pageSize));
    }

    /**
     * 操作者。类级 {@code @RequireRole} 已保证通过校验的请求一定处于登录态，
     * 这里再取一次是为了把操作人写进审计日志——审计记录的「谁」不能是游客。
     */
    private UserPrincipal principal() {
        return UserContext.requireLoggedIn();
    }

    /** 审核请求体。{@code approved} 为真表示通过，否则驳回。 */
    public record ReviewRequest(Boolean approved, String comment) {
    }

    /** 营养保存请求体。 */
    public record NutritionUpdateRequest(List<Map<String, Object>> items) {
    }
}
