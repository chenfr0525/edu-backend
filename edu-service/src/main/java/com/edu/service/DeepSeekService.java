package com.edu.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.edu.domain.dto.AiSuggestionDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeepSeekService {
    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.url}")
    private String apiUrl;

    @Value("${deepseek.api.model}")
    private String model;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    /**
     * 核心方法：调用 DeepSeek API
     */
    public String chatWithDeepSeek(String userPrompt) {
        log.info("准备调用 DeepSeek API，模型: {}，提示词: {}", model, userPrompt);

        JSONObject body = new JSONObject();
        body.put("model", model);

        List<JSONObject> messages = new ArrayList<>();
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是一个数据分析助手，请根据用户提供的数据，给出专业的总结和建议。请始终用中文回答。");
        
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);

        messages.add(systemMessage);
        messages.add(userMessage);
        body.put("messages", messages);
        
        body.put("temperature", 0.7);
        body.put("max_tokens", 2000);

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(body.toJSONString(), JSON_MEDIA_TYPE))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("DeepSeek API 调用失败，HTTP 状态码: {}", response.code());
                if (response.body() != null) {
                    log.error("错误详情: {}", response.body().string());
                }
                return "抱歉，AI 服务暂时不可用，请稍后再试。";
            }

            if (response.body() == null) {
                log.error("DeepSeek API 返回的响应体为空");
                return "抱歉，AI 服务返回了空数据。";
            }

            String responseBody = response.body().string();
            log.debug("DeepSeek API 原始响应: {}", responseBody);

            JSONObject jsonResponse = JSON.parseObject(responseBody);
            String content = jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

            log.info("DeepSeek API 调用成功，回复内容长度: {}", content.length());
            return content;

        } catch (IOException e) {
            log.error("调用 DeepSeek API 时发生网络异常", e);
            return "抱歉，网络开小差了，请检查网络连接。";
        } catch (Exception e) {
            log.error("调用 DeepSeek API 时发生未知异常", e);
            return "抱歉，AI 服务处理请求时遇到问题。";
        }
    }
    
    /**
     * 分析数据，生成总结、建议、优势、不足
     */
    public AiSuggestionDTO analyzeData(String dataJson, String dataType) {
        log.info("开始分析数据，数据类型: {}, 数据内容: {}", dataType, dataJson);
        
        // 构建专业的提示词（新格式）
        String prompt = String.format(
            "你是一名资深的教育数据分析师。请分析以下关于“%s”的数据，并严格按照JSON格式输出结果。\n" +
            "数据内容：%s\n" +
            "输出格式必须为：\n" +
            "{\n" +
            "  \"summary\": \"这里是你的总结，200字以内\",\n" +
            "  \"strengths\": [\"优势1\", \"优势2\", \"优势3\"],\n" +
            "  \"weaknesses\": [\"不足1\", \"不足2\", \"不足3\"],\n" +
            "  \"suggestions\": [\"建议1\", \"建议2\", \"建议3\", \"建议4\", \"建议5\"]\n" +
            "}\n" +
            "要求：\n" +
            "1. summary：精炼准确地概括核心趋势和整体表现\n" +
            "2. strengths：列出3个数据中体现的优势/亮点\n" +
            "3. weaknesses：列出3个需要改进的方面\n" +
            "4. suggestions：给出3-5条具体、可执行的改进建议\n" +
            "5. 所有字段都必须包含，如果某项不明显，请基于专业判断合理推断",
            dataType, dataJson
        );

        // 调用通用对话方法
        String resultJson = chatWithDeepSeek(prompt);
        log.info("DeepSeek 原始返回: {}", resultJson);

        // 尝试解析
        try {
            String cleanJson = extractJsonFromResponse(resultJson);
            log.info("提取后的 JSON: {}", cleanJson);
            
            AiSuggestionDTO response = JSON.parseObject(cleanJson, AiSuggestionDTO.class);
            
            // 验证必要字段
            if (response.getSummary() == null) response.setSummary("分析完成，请查看详细建议");
            if (response.getStrengths() == null) response.setStrengths(new ArrayList<>());
            if (response.getWeaknesses() == null) response.setWeaknesses(new ArrayList<>());
            if (response.getSuggestions() == null) response.setSuggestions(new ArrayList<>());
            
            log.info("数据分析成功 - summary: {}, strengths: {}, weaknesses: {}, suggestions: {}", 
                     response.getSummary(), 
                     response.getStrengths().size(),
                     response.getWeaknesses().size(),
                     response.getSuggestions().size());
            return response;
            
        } catch (Exception e) {
            log.error("解析 DeepSeek 返回的结果失败，原始内容: {}", resultJson, e);
            
            // 返回错误响应
            AiSuggestionDTO errorResponse = new AiSuggestionDTO();
            errorResponse.setSummary("AI 分析生成失败，请检查数据格式或稍后重试。错误：" + e.getMessage());
            errorResponse.setStrengths(Arrays.asList("暂无数据"));
            errorResponse.setWeaknesses(Arrays.asList("无法完成分析", "请检查数据格式", "请确认 API 配置"));
            errorResponse.setSuggestions(Arrays.asList(
                "请确保传入的数据是有效的JSON格式",
                "检查数据是否包含必要字段",
                "稍后重试或联系技术支持"
            ));
            return errorResponse;
        }
    }
    
    /**
     * 辅助方法：从返回的文本中提取 JSON 字符串
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "{}";
        }
        
        String trimmed = response.trim();
        
        // 处理 Markdown 代码块
        if (trimmed.contains("```json")) {
            int start = trimmed.indexOf("```json") + 7;
            int end = trimmed.indexOf("```", start);
            if (end > start) {
                return trimmed.substring(start, end).trim();
            }
        }
        
        if (trimmed.contains("```")) {
            int start = trimmed.indexOf("```") + 3;
            int end = trimmed.indexOf("```", start);
            if (end > start) {
                return trimmed.substring(start, end).trim();
            }
        }
        
        // 提取第一个 { 和最后一个 }
        int start = trimmed.indexOf("{");
        int end = trimmed.lastIndexOf("}");
        if (start != -1 && end != -1 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        
        return response;
    }
}