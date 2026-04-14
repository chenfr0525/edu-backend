package com.edu.domain.dto;
import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class KnowledgePointRadarDTO {
   private List<String> indicators;          // 知识点名称列表
    private List<BigDecimal> myValues;        // 我的掌握率
    private List<BigDecimal> classAvgValues;  // 班级平均掌握率
    private Long courseId;
    private String courseName;
}
