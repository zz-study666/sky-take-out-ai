package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 聊天请求 DTO
 */
@Data
public class AiChatDTO implements Serializable {

    /**
     * 用户消息内容（必填）
     */
    private String message;

    /**
     * 会话唯一标识（可选）
     * 前端可以传 openid 或 userId 作为会话id，用于后端扩展多轮上下文
     * 当前第一阶段暂不强依赖，留空也不报错
     */
    private String sessionId;
}