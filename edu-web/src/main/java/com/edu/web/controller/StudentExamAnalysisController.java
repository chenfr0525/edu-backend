package com.edu.web.controller;

import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.domain.*;
import com.edu.domain.dto.AiSuggestionDTO;
import com.edu.domain.dto.ExamStatisticsCards;
import com.edu.domain.dto.ExamTrendData;
import com.edu.domain.dto.StudentExamDTO;
import com.edu.domain.dto.StudentExamDetailDTO;
import com.edu.service.CourseService;
import com.edu.service.ScorePredictionService;
import com.edu.service.StudentExamAnalysisService;
import com.edu.service.StudentService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analysis/student/exam")
@RequiredArgsConstructor
public class StudentExamAnalysisController {
  
    private final StudentExamAnalysisService analysisService;

    /**
     * 1. 获取学生的考试列表（分页）
     * GET /api/analysis/student/exam/list/{studentId}?courseId=&status=&pageNum=1&pageSize=10
     */
    @GetMapping("/list/{studentId}")
    public Result<PageResult<StudentExamDTO>> getExamList(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        
        PageResult<StudentExamDTO> pageResult = analysisService.getStudentExamListPage(
            studentId, courseId, status, pageNum, pageSize);
        return Result.success(pageResult);
    }

    /**
     * 2. 获取单次考试的详细分析
     * GET /api/analysis/student/exam/detail/{studentId}/{examId}
     */
    @GetMapping("/detail/{studentId}/{examId}")
    public Result<StudentExamDetailDTO> getExamDetail(
            @PathVariable Long studentId,
            @PathVariable Long examId) {
        
        StudentExamDetailDTO detail = analysisService.getStudentExamDetail(studentId, examId);
        return Result.success(detail);
    }

    /**
     * 3. 获取学生考试统计卡片
     * GET /api/analysis/student/exam/statistics/{studentId}
     */
    @GetMapping("/statistics/{studentId}")
    public Result<ExamStatisticsCards> getStatisticsCards(@PathVariable Long studentId) {
        ExamStatisticsCards statistics = analysisService.getStudentExamStatisticsCards(studentId);
        return Result.success(statistics);
    }

    /**
     * 4. 获取学生考试趋势图数据
     * GET /api/analysis/student/exam/trend/{studentId}?courseId=
     */
    @GetMapping("/trend/{studentId}")
    public Result<ExamTrendData> getTrendData(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long courseId) {
        
        ExamTrendData trendData = analysisService.getStudentExamTrendData(studentId, courseId);
        return Result.success(trendData);
    }

    /**
     * 5. 获取学生整体考试AI分析报告
     * GET /api/analysis/student/exam/overall-analysis/{studentId}
     */
    @GetMapping("/overall-analysis/{studentId}")
    public Result<Map<String, Object>> getOverallAnalysis(@PathVariable Long studentId) {
        Map<String, Object> analysis = analysisService.getStudentExamOverallAnalysis(studentId);
        return Result.success(analysis);
    }

    /**
     * 6. 获取即将到来的考试提醒
     * GET /api/analysis/student/exam/upcoming/{studentId}
     */
    @GetMapping("/upcoming/{studentId}")
    public Result<List<Map<String, Object>>> getUpcomingExams(@PathVariable Long studentId) {
        List<Map<String, Object>> upcomingExams = analysisService.getUpcomingExams(studentId);
        return Result.success(upcomingExams);
    }

    /**
 * 7. 手动刷新 AI 分析报告
 * POST /api/analysis/student/exam/refresh/{studentId}/{examId}
 */
@PostMapping("/refresh/{studentId}/{examId}")
public Result<AiSuggestionDTO> refreshAiAnalysis(
        @PathVariable Long studentId,
        @PathVariable Long examId) {
    
    AiSuggestionDTO suggestion = analysisService.refreshExamAiAnalysis(studentId, examId);
    return Result.success(suggestion);
}
}
