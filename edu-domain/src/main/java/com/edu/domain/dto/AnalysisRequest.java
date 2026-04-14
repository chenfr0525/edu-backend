package com.edu.domain.dto;

import lombok.Data;

@Data
public class AnalysisRequest {
    private String dataJson;  // 待分析的数据
    private String dataType;   // 数据类型，如 "学生成绩数据"
}
