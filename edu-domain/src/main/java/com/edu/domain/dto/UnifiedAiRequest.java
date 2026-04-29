package com.edu.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedAiRequest {
   /**
     * 目标类型：STUDENT, CLASS, COURSE, EXAM, HOMEWORK
     */
    private String targetType;
    
    /**
     * 目标ID
     */
    private Long targetId;
    
    /**
     * 报告类型：COMPREHENSIVE, EXAM_ANALYSIS, HOMEWORK_ANALYSIS, KNOWLEDGE_ANALYSIS
     */
    private String reportType = "COMPREHENSIVE";
    
    /**
     * 是否强制刷新（忽略缓存）
     */
    private boolean forceRefresh = false;
}
