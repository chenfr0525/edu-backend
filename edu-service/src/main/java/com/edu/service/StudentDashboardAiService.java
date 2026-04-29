package com.edu.service;

import com.edu.domain.AiAnalysisReport;
import com.edu.domain.Student;
import com.edu.domain.dto.AiSuggestionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentDashboardAiService {

    private final StudentService studentService;
    private final UnifiedAiAnalysisService unifiedAiAnalysisService;

     /**
     * 获取学生 Dashboard 的 AI 分析报告（迁移到统一服务）
     */
    public AiAnalysisReport getOrCreateDashboardReport(Long studentId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        // 使用统一服务获取综合分析报告
        AiSuggestionDTO suggestion = unifiedAiAnalysisService.getOrCreateAnalysis(
            "STUDENT",
            studentId,
            "COMPREHENSIVE",  // 综合分析
            false
        );
        
        // 转换为 AiAnalysisReport 格式（保持接口兼容）
        return convertToReport(studentId, suggestion);
    }

    /**
     * 转换 AiSuggestionDTO 为 AiAnalysisReport（保持兼容）
     */
    private AiAnalysisReport convertToReport(Long studentId, AiSuggestionDTO suggestion) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            
            return AiAnalysisReport.builder()
                .targetType("STUDENT_DASHBOARD")
                .targetId(studentId)
                .reportType("COMPREHENSIVE")
                .summary(suggestion.getSummary())
                .suggestions(String.join("\n", suggestion.getSuggestions()))
                .strengths(mapper.writeValueAsString(suggestion.getStrengths()))
                .weaknesses(mapper.writeValueAsString(suggestion.getWeaknesses()))
                .analysisData("{}")
                .createdAt(LocalDateTime.now())
                .build();
        } catch (Exception e) {
            log.error("转换AI报告失败", e);
            return createFallbackReport(studentId);
        }
    }
    
    private AiAnalysisReport createFallbackReport(Long studentId) {
        return AiAnalysisReport.builder()
            .targetType("STUDENT_DASHBOARD")
            .targetId(studentId)
            .reportType("COMPREHENSIVE")
            .summary("数据不足，无法生成详细分析报告")
            .suggestions("请完成更多学习任务后重试")
            .createdAt(LocalDateTime.now())
            .build();
    }

   /**
     * 手动刷新 Dashboard AI 报告
     */
    public AiAnalysisReport refreshDashboardReport(Long studentId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        AiSuggestionDTO suggestion = unifiedAiAnalysisService.refreshAnalysis(
            "STUDENT",
            studentId,
            "COMPREHENSIVE"
        );
        
        return convertToReport(studentId, suggestion);
    }
}