package com.sky.service;

import com.sky.dto.AiChatDTO;
import com.sky.vo.AiChatVO;

/**
 * AI 客服服务接口
 */
public interface AiChatService {

    /**
     * 发送消息并获取 AI 回复
     *
     * @param dto 用户请求
     * @return AI 回复
     */
    AiChatVO chat(AiChatDTO dto);
}