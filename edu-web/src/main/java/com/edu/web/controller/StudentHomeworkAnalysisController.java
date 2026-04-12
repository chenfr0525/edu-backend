package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.dto.HomeworkStatisticsCards;
import com.edu.domain.dto.HomeworkTrendData;
import com.edu.domain.dto.StudentHomeworkDTO;
import com.edu.domain.dto.StudentHomeworkDetailDTO;
import com.edu.service.StudentHomeworkAnalysisService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analysis/student/homework")
@RequiredArgsConstructor
public class StudentHomeworkAnalysisController {
   private final StudentHomeworkAnalysisService analysisService;

    /**
     * 1. 获取学生的作业列表
     * GET /api/analysis/student/homework/list/{studentId}?courseId=&status=
     */
    @GetMapping("/list/{studentId}")
    public Result<List<StudentHomeworkDTO>> getHomeworkList(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String status) {
        
        List<StudentHomeworkDTO> list = analysisService.getStudentHomeworkList(studentId, courseId, status);
        return Result.success(list);
    }

    /**
     * 2. 获取单次作业的详细分析
     * GET /api/analysis/student/homework/detail/{studentId}/{homeworkId}
     */
    @GetMapping("/detail/{studentId}/{homeworkId}")
    public Result<StudentHomeworkDetailDTO> getHomeworkDetail(
            @PathVariable Long studentId,
            @PathVariable Long homeworkId) {
        
        StudentHomeworkDetailDTO detail = analysisService.getStudentHomeworkDetail(studentId, homeworkId);
        return Result.success(detail);
    }

    /**
     * 3. 获取学生作业统计卡片
     * GET /api/analysis/student/homework/statistics/{studentId}
     */
    @GetMapping("/statistics/{studentId}")
    public Result<HomeworkStatisticsCards> getStatisticsCards(@PathVariable Long studentId) {
        HomeworkStatisticsCards statistics = analysisService.getStudentStatisticsCards(studentId);
        return Result.success(statistics);
    }

    /**
     * 4. 获取学生作业成绩趋势图
     * GET /api/analysis/student/homework/trend/{studentId}?courseId=
     */
    @GetMapping("/trend/{studentId}")
    public Result<HomeworkTrendData> getTrendData(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long courseId) {
        
        HomeworkTrendData trendData = analysisService.getStudentTrendData(studentId, courseId);
        return Result.success(trendData);
    }

    /**
     * 5. 获取学生整体作业AI分析报告
     * GET /api/analysis/student/homework/overall-analysis/{studentId}
     */
    @GetMapping("/overall-analysis/{studentId}")
    public Result<Map<String, Object>> getOverallAnalysis(@PathVariable Long studentId) {
        Map<String, Object> analysis = analysisService.getStudentOverallAnalysis(studentId);
        return Result.success(analysis);
    }
}
