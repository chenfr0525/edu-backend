package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.dto.AiSuggestionDTO;
import com.edu.domain.dto.UnifiedAiRequest;
import com.edu.service.UnifiedAiAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/unified")
@RequiredArgsConstructor
@Slf4j
public class UnifiedAiAnalysisController {

    private final UnifiedAiAnalysisService unifiedAiAnalysisService;

    /**
     * 统一AI分析接口
     * POST /api/ai/unified/analyze
     * 
     * 请求体示例：
     * {
     *   "targetType": "STUDENT",
     *   "targetId": 1,
     *   "reportType": "COMPREHENSIVE",
     *   "forceRefresh": false
     * }
     * 
     * targetType可选值：STUDENT, CLASS, COURSE, EXAM, HOMEWORK
     * reportType可选值：
     *   - COMPREHENSIVE: 综合分析
     *   - EXAM_ANALYSIS: 考试分析（仅STUDENT有效）
     *   - HOMEWORK_ANALYSIS: 作业分析（仅STUDENT有效）
     *   - KNOWLEDGE_ANALYSIS: 知识点分析（仅STUDENT有效）
     */
    @PostMapping("/analyze")
    @PreAuthorize("isAuthenticated()")
    public Result<AiSuggestionDTO> analyze(@RequestBody UnifiedAiRequest request) {
        try {
            // 参数校验
            if (request.getTargetType() == null || request.getTargetType().isEmpty()) {
                return Result.error("targetType不能为空");
            }
            if (request.getTargetId() == null) {
                return Result.error("targetId不能为空");
            }
            if (request.getReportType() == null || request.getReportType().isEmpty()) {
                request.setReportType("COMPREHENSIVE");
            }
            
            AiSuggestionDTO result = unifiedAiAnalysisService.getOrCreateAnalysis(
                request.getTargetType(),
                request.getTargetId(),
                request.getReportType(),
                request.isForceRefresh()
            );
            return Result.success(result);
        } catch (Exception e) {
            log.error("AI分析失败", e);
            return Result.error("AI分析失败: " + e.getMessage());
        }
    }

    /**
     * 强制刷新AI分析（删除旧报告重新生成）
     * POST /api/ai/unified/refresh
     */
    @PostMapping("/refresh")
    @PreAuthorize("isAuthenticated()")
    public Result<AiSuggestionDTO> refresh(@RequestBody UnifiedAiRequest request) {
        try {
            if (request.getTargetType() == null || request.getTargetType().isEmpty()) {
                return Result.error("targetType不能为空");
            }
            if (request.getTargetId() == null) {
                return Result.error("targetId不能为空");
            }
            if (request.getReportType() == null || request.getReportType().isEmpty()) {
                request.setReportType("COMPREHENSIVE");
            }
            
            AiSuggestionDTO result = unifiedAiAnalysisService.refreshAnalysis(
                request.getTargetType(),
                request.getTargetId(),
                request.getReportType()
            );
            return Result.success(result);
        } catch (Exception e) {
            log.error("刷新AI分析失败", e);
            return Result.error("刷新失败: " + e.getMessage());
        }
    }
}