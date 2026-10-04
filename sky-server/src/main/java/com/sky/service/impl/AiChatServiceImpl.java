package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sky.dto.AiChatDTO;
import com.sky.properties.AiProperties;
import com.sky.service.AiChatService;
import com.sky.vo.AiChatVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * AI 客服服务实现（阿里千问 DashScope）
 */
@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    @Autowired
    private AiProperties aiProperties;

    @Override
    public AiChatVO chat(AiChatDTO dto) {
        // 1. 组装请求体（OpenAI 兼容格式）
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", aiProperties.getModel());
        requestBody.put("temperature", 0.7);

        // messages 数组：system + user 两条
        JSONArray messages = new JSONArray();
        messages.add(msg("system", aiProperties.getSystemPrompt()));
        messages.add(msg("user", dto.getMessage()));
        requestBody.put("messages", messages);

        // 2. 发起 HTTP POST 请求
        String responseJson = doDashScopePost(requestBody.toJSONString());

        // 3. 解析 AI 回复
        String reply = parseDashScopeReply(responseJson);

        return AiChatVO.builder().reply(reply).build();
    }

    /**
     * 组装一条 message 对象
     */
    private JSONObject msg(String role, String content) {
        JSONObject m = new JSONObject();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    /**
     * 发起 DashScope HTTP POST（携带 Authorization Bearer）
     */
    private String doDashScopePost(String requestBodyJson) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        CloseableHttpResponse response = null;
        try {
            HttpPost httpPost = new HttpPost(aiProperties.getUrl());

            // 关键：加 Authorization Header（项目原有的 HttpClientUtil.doPost4Json 没带 Header，这里自己写）
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + aiProperties.getApiKey());

            StringEntity entity = new StringEntity(requestBodyJson, StandardCharsets.UTF_8);
            httpPost.setEntity(entity);

            response = httpClient.execute(httpPost);
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("调用 DashScope AI 接口失败", e);
            return buildErrorReply("AI服务暂时不可用，请稍后再试（" + e.getMessage() + "）");
        } finally {
            closeQuietly(httpClient, response);
        }
    }

    /**
     * 解析 DashScope 响应 JSON
     *
     * DashScope OpenAI兼容模式返回：
     * {
     *   "choices": [{ "message": { "content": "AI的回答" } }],
     *   "error": null    ← 出错时 error 有值
     * }
     */
    private String parseDashScopeReply(String responseJson) {
        try {
            JSONObject json = JSON.parseObject(responseJson);

            // 先判断有没有 error 字段
            JSONObject error = json.getJSONObject("error");
            if (error != null) {
                log.error("DashScope 返回错误: {}", responseJson);
                return buildErrorReply("AI返回异常：" + error.getString("message"));
            }

            // 正常解析 choices[0].message.content
            JSONArray choices = json.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                String content = message.getString("content");
                if (StringUtils.hasText(content)) {
                    return content.trim();
                }
            }
            return buildErrorReply("AI返回内容为空");
        } catch (Exception e) {
            log.error("解析 DashScope 响应失败, 原始响应={}", responseJson, e);
            return buildErrorReply("解析AI响应时出错");
        }
    }

    private String buildErrorReply(String msg) {
        return "【AI服务异常】" + msg;
    }

    private void closeQuietly(CloseableHttpClient client, CloseableHttpResponse response) {
        try {
            if (response != null) response.close();
            if (client != null) client.close();
        } catch (Exception ignore) {
        }
    }
}