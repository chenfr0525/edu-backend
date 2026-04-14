package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.AiAnalysisReport;
import com.edu.domain.Homework;
import com.edu.domain.Student;
import com.edu.domain.dto.AiSuggestionDTO;
import com.edu.domain.dto.HomeworkStatisticsCards;
import com.edu.domain.dto.HomeworkTrendData;
import com.edu.domain.dto.StudentHomeworkDTO;
import com.edu.domain.dto.StudentHomeworkDetailDTO;
import com.edu.repository.HomeworkRepository;
import com.edu.service.AiAnalysisReportService;
import com.edu.service.StudentHomeworkAnalysisService;
import com.edu.service.StudentService;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analysis/student/homework")
@RequiredArgsConstructor
public class StudentHomeworkAnalysisController {
   private final StudentHomeworkAnalysisService analysisService;
   private final StudentService studentService;
   private final HomeworkRepository homeworkRepository;
   private final AiAnalysisReportService aiReportService;

    @GetMapping("/list/{studentId}")
    public Result<List<StudentHomeworkDTO>> getHomeworkList(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String status) {
        
        List<StudentHomeworkDTO> list = analysisService.getStudentHomeworkList(studentId, courseId, status);
        return Result.success(list);
    }

    @GetMapping("/detail/{studentId}/{homeworkId}")
    public Result<StudentHomeworkDetailDTO> getHomeworkDetail(
            @PathVariable Long studentId,
            @PathVariable Long homeworkId) {
        
        StudentHomeworkDetailDTO detail = analysisService.getStudentHomeworkDetail(studentId, homeworkId);
        return Result.success(detail);
    }

    @GetMapping("/statistics/{studentId}")
    public Result<HomeworkStatisticsCards> getStatisticsCards(@PathVariable Long studentId) {
        HomeworkStatisticsCards statistics = analysisService.getStudentStatisticsCards(studentId);
        return Result.success(statistics);
    }

    @GetMapping("/trend/{studentId}")
    public Result<HomeworkTrendData> getTrendData(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long courseId) {
        
        HomeworkTrendData trendData = analysisService.getStudentTrendData(studentId, courseId);
        return Result.success(trendData);
    }

    @GetMapping("/overall-analysis/{studentId}")
    public Result<Map<String, Object>> getOverallAnalysis(@PathVariable Long studentId) {
        Map<String, Object> analysis = analysisService.getStudentOverallAnalysis(studentId);
        return Result.success(analysis);
    }

    @PostMapping("/refresh/{studentId}/{homeworkId}")
    public Result<AiSuggestionDTO> refreshAiAnalysis(
            @PathVariable Long studentId,
            @PathVariable Long homeworkId) {
        
        AiSuggestionDTO suggestion = analysisService.refreshAiAnalysis(studentId, homeworkId);
        return Result.success(suggestion);
    }
}