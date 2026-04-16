package com.edu.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.edu.domain.dto.AiSuggestionDTO;
import com.edu.domain.dto.FieldMapping;
import com.edu.domain.dto.ParseResult;
import com.edu.domain.dto.ValidationError;
import com.edu.repository.ClassRepository;
import com.edu.repository.StudentRepository;
import com.edu.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeepSeekService {
    private final UserRepository userRepository;
     private final  StudentRepository studentRepository;
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
/**
 * 解析文件并提取结构化数据
 * @param fileContent 文件内容（Base64或文本）
 * @param fileName 文件名
 * @param dataType 数据类型
 * @param fieldMappings 字段映射配置
 * @return 解析结果
 */
public ParseResult parseFileData(String fileContent, String fileName, String dataType, 
                                  List<FieldMapping> fieldMappings) {
    log.info("开始解析文件: {}, 数据类型: {}", fileName, dataType);
    
    // 1. 构建提示词
    String prompt = buildParsePrompt(fileContent, fileName, dataType, fieldMappings);
    
    // 2. 调用 AI
    String aiResponse = chatWithDeepSeek(prompt);
    log.info("AI 原始响应: {}", aiResponse);
    
    // 3. 解析 AI 返回的 JSON
    ParseResult result = new ParseResult();
    result.setRawResponse(aiResponse);
    
    try {
        String cleanJson = extractJsonFromResponse(aiResponse);
        JSONObject jsonResult = JSON.parseObject(cleanJson);

        // 5. 提取列名映射
        JSONObject mappingJson = jsonResult.getJSONObject("fieldMapping");
        Map<String, String> columnMapping = new HashMap<>();
        if (mappingJson != null) {
            for (String key : mappingJson.keySet()) {
                columnMapping.put(key, mappingJson.getString(key));
            }
        }
        result.setColumnMapping(columnMapping);
        
        
        // 提取数据
        if (jsonResult.containsKey("data")) {
            String dataArray = jsonResult.getString("data");
            List<Map<String, Object>> dataList = JSON.parseObject(dataArray, List.class);
            result.setData(dataList);
        }
        
        // 提取摘要
        result.setSummary(jsonResult.getString("summary"));
        
        // 4. 验证必填字段
        List<ValidationError> errors = validateData(result.getData(), fieldMappings);
        result.setErrors(errors);
        result.setSuccess(errors.isEmpty());
        
        if (!errors.isEmpty()) {
            log.warn("数据验证失败，发现 {} 个错误", errors.size());
        }
        
    } catch (Exception e) {
        log.error("解析 AI 返回结果失败", e);
        result.setSuccess(false);
        result.setErrors(Arrays.asList(
            createError(0, null, "AI解析失败: " + e.getMessage())
        ));
        result.setSummary("解析失败，请检查文件格式");
    }
    
    return result;
}

/**
 * 构建解析提示词
 */
private String buildParsePrompt(String fileContent, String fileName, String dataType, 
                                 List<FieldMapping> fieldMappings) {
    StringBuilder fieldsDesc = new StringBuilder();
    fieldsDesc.append("需要提取的字段：\n");
    
    for (FieldMapping mapping : fieldMappings) {
        fieldsDesc.append(String.format("- %s (%s): %s，可能的列名包括：%s %s\n",
            mapping.getTargetField(),
            mapping.getDataType(),
            mapping.getFieldDescription(),
             mapping.getPossibleNames(),
            mapping.isRequired() ? "【必填】" : "【可选】"
        ));
    }
    
    return String.format(
        "你是一个专业的数据提取专家。请从以下文件中提取【%s】相关的数据。\n\n" +
        "文件名：%s\n" +
        "文件内容：\n%s\n\n" +
        "%s\n\n" +
        "要求：\n" +
        "1. 仔细分析文件内容，智能识别并提取所有记录\n" +
        "2. 如果字段值缺失，用 null 表示\n" +
        "3. 日期格式统一为 yyyy-MM-ddTHH:MM:SS\n" +
        "4. 数字类型去掉单位，只保留数字\n" +
        "5. 严格按照以下JSON格式输出，不要有其他解释：\n" +
         "6. 第一行通常是列名，请智能识别每个列对应哪个目标字段\n" +
         "7. 无论列名是中文、英文还是缩写，都要正确识别\n" +
         "8. 字母大小写也需要分辨,比如计算机a班也是计算机A班\n" +
         "8. 数字也需要分辨,比如计算机1班也是计算机一班\n" +
        "{\n" +
        "  \"summary\": \"数据提取摘要，说明提取了多少条记录，主要包含哪些信息\",\n" +
        "  \"data\": [\n" +
        "    {\n" +
        "      \"字段1\": \"值1\",\n" +
        "      \"字段2\": \"值2\"\n" +
        "    }\n" +
        "  ]\n" +
        "}",
        dataType, fileName, fileContent, fieldsDesc.toString()
    );
}

/**
 * 验证数据完整性
 */
public List<ValidationError> validateData(List<Map<String, Object>> dataList, 
                                            List<FieldMapping> fieldMappings) {
    List<ValidationError> errors = new ArrayList<>();
    
    if (dataList == null || dataList.isEmpty()) {
        errors.add(createError(0, null, "未提取到任何数据"));
        return errors;
    }

    // 2. 用于唯一性校验的集合
     Map<String, Integer> uniqueCheckMap = new HashMap<>();  // 存储已检查的唯一值
    
    for (int i = 0; i < dataList.size(); i++) {
        Map<String, Object> row = dataList.get(i);
        
        for (FieldMapping mapping : fieldMappings) {
            if (!mapping.isRequired()) continue;
            
            Object value = row.get(mapping.getTargetField());

             boolean isEmpty = (value == null || value.toString().trim().isEmpty());
            
            if (isEmpty && !mapping.isRequired()) {
                continue; // 可选字段为空，跳过后续校验
            }
            
            // 检查是否为空
            if ( mapping.isRequired() && isEmpty) {
                errors.add(createError(i, mapping.getTargetField(), 
                    String.format("第%d行：必填字段【%s】缺失", i + 1, mapping.getTargetField())));
                continue;
            }
            String strValue = value.toString().trim();
            
            // 检查数据类型
            if (!validateDataType(value, mapping.getDataType())) {
                errors.add(createError(i, mapping.getTargetField(),
                    String.format("第%d行：字段【%s】类型错误，期望%s，实际%s", 
                        i + 1, mapping.getTargetField(), mapping.getDataType(), 
                        value.getClass().getSimpleName())));
                continue;
            }

             // ========== 3. 正则校验 ==========
            if (mapping.getRegex() != null && !mapping.getRegex().isEmpty()) {
                if (!strValue.matches(mapping.getRegex())) {
                    errors.add(createError(i, mapping.getTargetField(),
                        String.format("第%d行：字段【%s】格式不正确，应符合格式：%s", 
                            i + 1, mapping.getTargetField(), getRegexDescription(mapping.getRegex()))));
                    continue;
                }
            }

            //唯一性校验
             if (mapping.isUnique()) {
                String key = mapping.getTargetField() + ":" + strValue;
                if (uniqueCheckMap.containsKey(key)) {
                    int firstRow = (int) uniqueCheckMap.get(key);
                    errors.add(createError(i, mapping.getTargetField(),
                        String.format("第%d行：字段【%s】值【%s】与第%d行重复", 
                            i + 1, mapping.getTargetField(), strValue, firstRow + 1)));
                } else {
                    uniqueCheckMap.put(key, i);
                }
            }
      
             if (mapping.isNeedExist()) {
                if (!checkExistsInDatabase(mapping.getTargetField(), strValue)) {
                    errors.add(createError(i, mapping.getTargetField(),
                        String.format("第%d行：字段【%s】值【%s】在系统中不存在", 
                            i + 1, mapping.getTargetField(), strValue)));
                }
            }

        }
    }
    
    return errors;
}

/**
 * 检查数据是否存在于数据库
 * @param field 字段名
 * @param value 字段值
 * @return 是否存在
 */
private boolean checkExistsInDatabase(String field, String value) {
    // 根据字段名调用不同的 repository 方法
    switch (field) {
        case "studentNo":
            // 检查学号是否已存在（用于更新场景）
            return studentRepository.existsByStudentNo(value);
        case "username":
            // 检查用户名是否已存在
            return userRepository.existsByUsername(value);
        default:
            return true; // 默认返回 true，不做存在性校验
    }
}

/**
 * 获取正则表达式的描述（用于错误提示）
 */
private String getRegexDescription(String regex) {
    if (regex == null) return "";
    
    // 常见正则的描述映射
    if (regex.equals("^1[3-9]\\d{9}$")) {
        return "11位手机号码";
    }
    if (regex.equals("^[A-Za-z0-9+_.-]+@(.+)$")) {
        return "电子邮箱地址";
    }
    if (regex.equals("^[\\u4e00-\\u9fa5]{2,4}$")) {
        return "2-4位中文字符";
    }
    if (regex.equals("^[A-Za-z0-9_]{4,20}$")) {
        return "4-20位字母、数字或下划线";
    }
    
    return regex; // 返回原始正则
}

/**
 * 验证数据类型
 */
private boolean validateDataType(Object value, String dataType) {
    if (value == null) return true;
    
    switch (dataType.toLowerCase()) {
        case "number":
            return value instanceof Number;
        case "date":
            // 简单验证日期格式
            String str = value.toString();
            return str.matches("\\d{4}-\\d{2}-\\d{2}");
        case "string":
            return true;
        default:
            return true;
    }
}

private ValidationError createError(int row, String field, String message) {
    ValidationError error = new ValidationError();
    error.setRowIndex(row);
    error.setField(field);
    error.setErrorMessage(message);
    return error;
}
}