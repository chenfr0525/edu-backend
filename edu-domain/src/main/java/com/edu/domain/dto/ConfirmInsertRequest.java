package com.edu.domain.dto;
import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class ConfirmInsertRequest {
    private String type;//"student","exam","exam_grade","homework",homework_grade"
    private List<Map<String, Object>> data; // 确认后的数据（可能被用户修改过）
}
