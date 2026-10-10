package com.nutritionagent.common.api;

/**
 * API 契约中的固定路径与常量。
 *
 * <p>为什么不用 {@code spring.mvc.servlet.path} 做前缀：那会同时改写 H2 控制台与
 * 静态资源的路径，且让 Controller 上的映射与实际 URL 不再逐字对应。这里让
 * Controller 显式声明 {@code /api/v1/...}，映射与 {@code docs/api-contract.md}
 * 可以逐行机械比对。
 */
public final class ApiPaths {

    /** 契约第一章：基础路径。 */
    public static final String V1 = "/api/v1";

    /** 契约 4.8：文件访问前缀，与 {@code nutrition-agent.storage.public-prefix} 一致。 */
    public static final String FILES = V1 + "/files";

    private ApiPaths() {
    }
}
