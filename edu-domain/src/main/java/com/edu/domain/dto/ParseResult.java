package com.edu.domain.dto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class ParseResult {
   private String sessionId;                    // 会话ID
    private boolean success;                     // 是否解析成功
    private List<Map<String, Object>> data;      // 解析出的数据列表
    private List<ValidationError> errors;        // 验证错误
    private String summary;                      // 解析摘要
    private String rawResponse;                  // AI原始返回（调试用）
     private Map<String, String> columnMapping;   
}
