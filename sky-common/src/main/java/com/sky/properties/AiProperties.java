package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 大模型配置（阿里千问 DashScope）
 * 绑定 yml 中 sky.ai 段
 */
@Data
@Component
@ConfigurationProperties(prefix = "sky.ai")
public class AiProperties {

    /**
     * DashScope API Key（Bearer Token 用）
     */
    private String apiKey;

    /**
     * 模型名称，默认 qwen-plus
     * 可选值：qwen-turbo / qwen-plus / qwen-max 等
     */
    private String model = "qwen-plus";

    /**
     * OpenAI 兼容端点（阿里百炼）
     */
    private String url = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    /**
     * 系统提示词：给 AI 的人设
     * 你可以在 yml 里覆盖，让 AI 更懂苍穹外卖业务
     */
    private String systemPrompt = "你是苍穹外卖的AI智能客服，请用亲切专业的语气回答用户问题。" +
            "你目前只能进行简单问答，点餐、查订单等业务功能正在开发中。";
}