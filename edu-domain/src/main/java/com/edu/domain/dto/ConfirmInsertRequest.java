package com.edu.domain.dto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.Data;
import net.sf.jsqlparser.util.validation.ValidationError;

@Data
public class ConfirmInsertRequest {
   private String sessionId;               // 会话ID
    private List<Map<String, Object>> data; // 确认后的数据（可能被用户修改过）
    private boolean confirmed;              // 是否确认
}
