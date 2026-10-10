package com.nutritionagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * AI 食品营养师 — 后端入口。
 *
 * <p>架构形态为<b>模块化单体</b>（PRD 1.1 "开发模式" 明确排除微服务）。
 * 包结构即模块边界：
 * <ul>
 *   <li>{@code common}   — 统一响应、错误码、异常、工具</li>
 *   <li>{@code config}   — 配置类与拦截器注册</li>
 *   <li>{@code security} — 令牌鉴权与权限校验（自研轻量实现，不引入 Spring Security）</li>
 *   <li>{@code food}     — 食品域</li>
 *   <li>{@code nutrition}— 营养域，含确定性计算纯函数</li>
 *   <li>{@code agent}    — Agent 协调层（工具 + LLM 适配 + 事实校验）</li>
 *   <li>{@code knowledge}— 知识库与检索</li>
 *   <li>{@code user}     — 用户、收藏、对话</li>
 *   <li>{@code admin}    — 后台内容管理</li>
 * </ul>
 */
@SpringBootApplication
@EnableTransactionManagement
@MapperScan("com.nutritionagent.**.mapper")
public class NutritionAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(NutritionAgentApplication.class, args);
    }
}
