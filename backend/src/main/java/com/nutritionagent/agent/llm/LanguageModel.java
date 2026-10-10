package com.nutritionagent.agent.llm;

import com.nutritionagent.agent.tool.AgentToolkit;

/**
 * 语言模型的抽象。
 *
 * <p>关键设计：<b>模型只负责把已经确定的事实组织成自然语言</b>。数值、来源、缺失项
 * 都由 {@link AgentToolkit} 从数据库取出后作为参数传进来——模型即使"胡说"，也不可能
 * 说出一个不存在的营养数值，因为它从来没有被要求生成数值。
 *
 * <p>这样做的第二个好处是：模型不可用时可以退化为确定性的文本组织器，问答功能整体
 * 仍然可用（契约 4.3 的 {@code degraded: true} 语义），而不是整个接口挂掉。
 */
public interface LanguageModel {

    /** 模型标识，写入回答的 {@code model} 字段，便于事后判断这条回答是谁生成的。 */
    String name();

    boolean available();

    /**
     * 组织回答文本。
     *
     * @param question 用户原始问题（只用于措辞，不用于取数）
     * @param toolkit  已取好的事实与来源
     */
    String compose(String question, AgentToolkit.AgentContext toolkit);
}
