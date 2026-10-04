package com.sky.controller.user;

import com.sky.dto.AiChatDTO;
import com.sky.result.Result;
import com.sky.service.AiChatService;
import com.sky.vo.AiChatVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C端 AI 客服接口
 */
@RestController("userAiChatController")
@RequestMapping("/user/ai")
@Slf4j
@Api(tags = "C端-AI客服接口")
public class AiChatController {

    @Autowired
    private AiChatService aiChatService;

    /**
     * AI 聊天
     * POST /user/ai/chat
     * Body: {"message": "你好"}
     */
    @PostMapping("/chat")
    @ApiOperation("AI客服-发送消息")
    public Result<AiChatVO> chat(@RequestBody AiChatDTO dto) {
        log.info("AI聊天请求: {}", dto.getMessage());
        AiChatVO vo = aiChatService.chat(dto);
        return Result.success(vo);
    }
}