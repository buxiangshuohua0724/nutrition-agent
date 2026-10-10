package com.nutritionagent.user.controller;

import com.nutritionagent.common.api.ApiPaths;
import com.nutritionagent.common.api.Result;
import com.nutritionagent.common.security.UserContext;
import com.nutritionagent.common.security.UserPrincipal;
import com.nutritionagent.food.dto.FoodSummary;
import com.nutritionagent.user.dto.UserView;
import com.nutritionagent.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 注册、登录与个人数据接口（契约 4.6，FR-007 / FR-008）。
 *
 * <p>核心安全约定：{@code /users/me/**} 的每一个方法都<b>只</b>从 {@link UserContext}
 * （即令牌）取 userId，请求体与路径里没有 userId 的位置。这样"越权访问他人数据"
 * 在这层根本无法被表达，而不是靠逐个方法记得校验。
 */
@RestController
@RequestMapping(ApiPaths.V1)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ------------------------------------------------------------------
    // 认证
    // ------------------------------------------------------------------

    @PostMapping("/auth/register")
    public Result<UserService.RegisterResult> register(@Valid @RequestBody RegisterRequest request) {
        UserView user = userService.register(request.username(), request.password(),
                request.nickname(), request.email());
        return Result.ok(new UserService.RegisterResult(user.id(), user.username(), user.nickname(),
                user.email(), user));
    }

    @PostMapping("/auth/login")
    public Result<UserService.LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(userService.login(request.username(), request.password()));
    }

    @PostMapping("/auth/logout")
    public Result<Void> logout() {
        // 未登录时调用登出不算错误：前端可能因令牌过期而重试，报 401 只会制造噪音
        UserPrincipal principal = UserContext.current();
        if (principal.isLoggedIn()) {
            userService.logout(principal);
        }
        return Result.ok();
    }

    // ------------------------------------------------------------------
    // 个人数据
    // ------------------------------------------------------------------

    @GetMapping("/users/me")
    public Result<UserView> me() {
        return Result.ok(userService.loadUser(requireUserId()));
    }

    @PutMapping("/users/me/profile")
    public Result<UserView> updateProfile(@Valid @RequestBody ProfileRequest request) {
        return Result.ok(userService.updateProfile(requireUserId(), request.nickname(), request.email(),
                request.avatarUrl(), request.preferences()));
    }

    @GetMapping("/users/me/favorites")
    public Result<List<FoodSummary>> favorites() {
        return Result.ok(userService.favorites(requireUserId()));
    }

    @PostMapping("/users/me/favorites")
    public Result<Void> addFavorite(@Valid @RequestBody FavoriteRequest request) {
        userService.addFavorite(requireUserId(), request.foodId());
        return Result.ok();
    }

    @DeleteMapping("/users/me/favorites/{foodId}")
    public Result<Void> removeFavorite(@PathVariable Long foodId) {
        userService.removeFavorite(requireUserId(), foodId);
        return Result.ok();
    }

    @DeleteMapping("/users/me/data")
    public Result<Void> deleteMyData() {
        userService.deleteMyData(requireUserId());
        return Result.ok();
    }

    /** 令牌里没有 userId 就说明没登录——这里统一抛 40101。 */
    private Long requireUserId() {
        return UserContext.requireLoggedIn().userId();
    }

    // ------------------------------------------------------------------
    // 请求体
    // ------------------------------------------------------------------

    public record RegisterRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password,
            String nickname,
            String email) {
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    /**
     * 资料更新请求。所有字段可空 = 不修改。
     *
     * <p>{@code preferences} 用 {@code Map} 而不是强类型：契约把它定义为
     * {@code {goal, avoid[]}} 这类开放结构，强行定死会在新增偏好项时逼出一堆兼容字段。
     * 校验放在 {@code UserService} 里按语义做（例如 goal 只接受已知枚举）。
     */
    public record ProfileRequest(
            String nickname,
            String email,
            String avatarUrl,
            Map<String, Object> preferences) {
    }

    public record FavoriteRequest(
            @jakarta.validation.constraints.NotNull(message = "foodId 不能为空") Long foodId) {
    }
}
