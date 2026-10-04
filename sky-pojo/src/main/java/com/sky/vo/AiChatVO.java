package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI 聊天响应 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatVO implements Serializable {

    /**
     * AI 回复的文本内容
     */
    private String reply;
}