package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.dto.HomeworkAnalysisDTO;
import com.edu.domain.dto.HomeworkDetailDTO;
import com.edu.service.HomeworkAnalysisService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

import java.util.Map;


@RestController
@RequestMapping("/api/analysis/homework")
@RequiredArgsConstructor
public class HomeworkAnalysisController {
  private final HomeworkAnalysisService homeworkAnalysisService;

    /**
     * 1. 获取作业列表
     * GET /api/analysis/homework/list?status=&keyword=&courseId=&page=0&size=10
     */
    @GetMapping("/list")
    public Result<Page<HomeworkAnalysisDTO>> getHomeworkList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Page<HomeworkAnalysisDTO> list = homeworkAnalysisService.getHomeworkList(status, keyword, courseId, page, size);
        return Result.success(list);
    }

    /**
     * 2. 获取作业详情（包含分析数据）
     * GET /api/analysis/homework/detail/{homeworkId}
     */
    @GetMapping("/detail/{homeworkId}")
    public Result<HomeworkDetailDTO> getHomeworkDetail(@PathVariable Long homeworkId) {
        HomeworkDetailDTO detail = homeworkAnalysisService.getHomeworkDetail(homeworkId);
        return Result.success(detail);
    }

    /**
     * 3. 获取统计卡片数据（4个指标）
     * GET /api/analysis/homework/statistics
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatisticsCards() {
        Map<String, Object> statistics = homeworkAnalysisService.getStatisticsCards();
        return Result.success(statistics);
    }

    /**
     * 4. 获取作业趋势图数据
     * GET /api/analysis/homework/trend
     */
    @GetMapping("/trend")
    public Result<Map<String, Object>> getTrendData() {
        Map<String, Object> trendData = homeworkAnalysisService.getTrendData();
        return Result.success(trendData);
    }

    /**
     * 5. 获取整体作业AI分析报告
     * GET /api/analysis/homework/overall-analysis
     */
    @GetMapping("/overall-analysis")
    public Result<Map<String, Object>> getOverallAnalysis() {
        Map<String, Object> analysis = homeworkAnalysisService.getOverallAnalysis();
        return Result.success(analysis);
    }
}
