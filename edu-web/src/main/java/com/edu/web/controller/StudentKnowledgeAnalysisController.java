package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.dto.AiSuggestionDTO;
import com.edu.domain.dto.KnowledgePointDetailDTO;
import com.edu.domain.dto.KnowledgePointProgressDTO;
import com.edu.domain.dto.KnowledgePointRadarDTO;
import com.edu.domain.dto.KnowledgePointStatisticsCardsDTO;
import com.edu.domain.dto.KnowledgePointTreeDTO;
import com.edu.service.StudentKnowledgeAnalysisService;

import lombok.RequiredArgsConstructor;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analysis/student/knowledge")
@RequiredArgsConstructor
public class StudentKnowledgeAnalysisController {
   private final StudentKnowledgeAnalysisService analysisService;

    /**
     * 1. 获取知识点树形结构
     * GET /api/analysis/student/knowledge/tree/{studentId}?courseId=
     */
    @GetMapping("/tree/{studentId}")
    public Result<List<KnowledgePointTreeDTO>> getKnowledgePointTree(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long courseId) {
        
        List<KnowledgePointTreeDTO> tree = analysisService.getKnowledgePointTree(studentId, courseId);
        return Result.success(tree);
    }

    /**
     * 2. 获取知识点统计卡片
     * GET /api/analysis/student/knowledge/statistics/{studentId}
     */
    @GetMapping("/statistics/{studentId}")
    public Result<KnowledgePointStatisticsCardsDTO> getStatisticsCards(@PathVariable Long studentId, @RequestParam(required = false) Long courseId) {
        KnowledgePointStatisticsCardsDTO cards = analysisService.getStatisticsCards(studentId, courseId);
        return Result.success(cards);
    }

    /**
     * 3. 获取知识点掌握进度（环图数据）
     * GET /api/analysis/student/knowledge/progress/{studentId}?courseId=
     * TODO:存在问题
     */
    @GetMapping("/progress/{studentId}")
    public Result<KnowledgePointProgressDTO> getKnowledgePointProgress(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long courseId) {
        
        KnowledgePointProgressDTO progress = analysisService.getKnowledgePointProgress(studentId, courseId);
        return Result.success(progress);
    }

    /**
     * 4. 获取知识点雷达图数据
     * GET /api/analysis/student/knowledge/radar/{studentId}?courseId=
     */
    @GetMapping("/radar/{studentId}")
    public Result<KnowledgePointRadarDTO> getKnowledgePointRadar(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long courseId) {
        
        KnowledgePointRadarDTO radar = analysisService.getKnowledgePointRadar(studentId, courseId);
        return Result.success(radar);
    }

    /**
     * 5. 获取知识点详情（包含趋势图）
     * GET /api/analysis/student/knowledge/detail/{studentId}/{knowledgePointId}
     */
    @GetMapping("/detail/{studentId}/{knowledgePointId}")
    public Result<KnowledgePointDetailDTO> getKnowledgePointDetail(
            @PathVariable Long studentId,
            @PathVariable Long knowledgePointId) {
        
        KnowledgePointDetailDTO detail = analysisService.getKnowledgePointDetail(studentId, knowledgePointId);
        return Result.success(detail);
    }

   /**
     * 获取知识点AI分析（使用统一服务）
     * GET /api/analysis/student/knowledge/ai-analysis/{studentId}?courseId=
     */
    @GetMapping("/ai-analysis/{studentId}")
    public Result<AiSuggestionDTO> getAiAnalysis(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long courseId) {
        
        AiSuggestionDTO analysis = analysisService.getKnowledgePointAiAnalysis(studentId, courseId);
        return Result.success(analysis);
    }

    /**
     * 手动刷新 AI 分析报告
     * POST /api/analysis/student/knowledge/refresh/{studentId}?courseId=
     */
    @PostMapping("/refresh/{studentId}")
    public Result<AiSuggestionDTO> refreshAiAnalysis(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long courseId) {
        
        AiSuggestionDTO suggestion = analysisService.refreshKnowledgeAiAnalysis(studentId, courseId);
        return Result.success(suggestion);
    }
}
