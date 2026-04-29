package com.edu.service;

import com.alibaba.fastjson.JSONObject;
import com.edu.domain.*;
import com.edu.domain.dto.AiSuggestionDTO;
import com.edu.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeacherDashboardAiService {

    private final ClassRepository classRepository;
    private final CourseRepository courseRepository;
    private final UnifiedAiAnalysisService unifiedAiAnalysisService;

   /**
     * 获取或创建 AI 分析报告（迁移到统一服务）
     */
    public AiSuggestionDTO getOrCreateReport(String targetType, Long targetId, String reportType) {
        if (targetId == null) {
            log.error("targetId 为空");
            return createFallbackSuggestion(targetType, targetId);
        }
        
        return unifiedAiAnalysisService.getOrCreateAnalysis(
            targetType,
            targetId,
            reportType,
            false
        );
    }    
    
    /**
     * 手动刷新 AI 分析报告
     */
    public AiSuggestionDTO refreshReport(String targetType, Long targetId, String reportType) {
        if (targetId == null) {
            log.error("targetId 为空");
            return createFallbackSuggestion(targetType, targetId);
        }
        
        return unifiedAiAnalysisService.refreshAnalysis(
            targetType,
            targetId,
            reportType
        );
    }

    /**
     * 创建降级建议（AI失败时使用）
     */
    private AiSuggestionDTO createFallbackSuggestion(String targetType, Long targetId) {
        String targetName = getTargetName(targetType, targetId);
        
        return AiSuggestionDTO.builder()
            .summary(String.format("【%s】当前数据量不足，无法生成详细的AI分析报告。", targetName))
            .suggestions(Arrays.asList(
                "1. 请确保已录入完整的考试成绩数据",
                "2. 请确保已批改作业并录入成绩",
                "3. 数据积累足够后，系统将自动生成详细分析报告"
            ))
            .strengths(new ArrayList<>())
            .weaknesses(new ArrayList<>())
            .build();
    }
    
     /**
     * 获取目标名称
     */
    private String getTargetName(String targetType, Long targetId) {
        try {
            if ("CLASS".equals(targetType)) {
                return classRepository.findById(targetId).map(ClassInfo::getName).orElse("班级");
            } else if ("COURSE".equals(targetType)) {
                return courseRepository.findById(targetId).map(Course::getName).orElse("课程");
            }
            return "目标";
        } catch (Exception e) {
            return "目标";
        }
    }
 }