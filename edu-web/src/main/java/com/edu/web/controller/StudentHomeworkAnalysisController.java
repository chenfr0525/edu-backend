package com.edu.web.controller;

import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.domain.dto.AiSuggestionDTO;
import com.edu.domain.dto.HomeworkStatisticsCards;
import com.edu.domain.dto.HomeworkTrendData;
import com.edu.domain.dto.StudentHomeworkDTO;
import com.edu.domain.dto.StudentHomeworkDetailDTO;
import com.edu.service.StudentHomeworkAnalysisService;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analysis/student/homework")
@RequiredArgsConstructor
public class StudentHomeworkAnalysisController {
   private final StudentHomeworkAnalysisService analysisService;

    @GetMapping("/list/{studentId}")
    public Result<PageResult<StudentHomeworkDTO>> getHomeworkList(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        
        Page<StudentHomeworkDTO> page = analysisService.getStudentHomeworkList(studentId, courseId, pageNum, pageSize);
        return Result.success(PageResult.of(page));
    }

    @GetMapping("/detail/{studentId}/{homeworkId}")
    public Result<StudentHomeworkDetailDTO> getHomeworkDetail(
            @PathVariable Long studentId,
            @PathVariable Long homeworkId) {
        
        StudentHomeworkDetailDTO detail = analysisService.getStudentHomeworkDetail(studentId, homeworkId);
        return Result.success(detail);
    }

    @GetMapping("/statistics/{studentId}")
    public Result<HomeworkStatisticsCards> getStatisticsCards(@PathVariable Long studentId, @RequestParam(required = false) Long courseId) {
        HomeworkStatisticsCards statistics = analysisService.getStudentStatisticsCards(studentId, courseId);
        return Result.success(statistics);
    }

    @GetMapping("/trend/{studentId}")
    public Result<HomeworkTrendData> getTrendData(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long courseId) {
        
        HomeworkTrendData trendData = analysisService.getStudentTrendData(studentId, courseId);
        return Result.success(trendData);
    }

     /**
     * 5. 手动刷新 AI 分析报告
     * POST /api/analysis/student/homework/refresh/{studentId}/{homeworkId}
     */
    @PostMapping("/refresh/{studentId}/{homeworkId}")
    public Result<AiSuggestionDTO> refreshAiAnalysis(
            @PathVariable Long studentId,
            @PathVariable Long homeworkId) {
        
        AiSuggestionDTO suggestion = analysisService.refreshAiAnalysis(studentId, homeworkId);
        return Result.success(suggestion);
    }
}