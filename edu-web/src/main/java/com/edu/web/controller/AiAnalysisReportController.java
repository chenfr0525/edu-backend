package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.*;
import com.edu.service.AiAnalysisReportService;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai-report")
@RequiredArgsConstructor
public class AiAnalysisReportController {
   private final AiAnalysisReportService reportService;
    
    @GetMapping("/list")
    public Result<List<AiAnalysisReport>> list() {
        return Result.success(reportService.findAll());
    }
    
    @GetMapping("/target")
    public Result<List<AiAnalysisReport>> getByTarget(
            @RequestParam String targetType,
            @RequestParam Long targetId) {
        return Result.success(reportService.findByTarget(targetType, targetId));
    }
    
    @GetMapping("/target/recent")
    public Result<List<AiAnalysisReport>> getRecentReports(
            @RequestParam String targetType,
            @RequestParam Long targetId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime since) {
        return Result.success(reportService.findRecentReports(targetType, targetId, since));
    }
    
    @PostMapping
    public Result<AiAnalysisReport> add(@RequestBody AiAnalysisReport report) {
        return Result.success(reportService.save(report));
    }
    
    @PutMapping
    public Result<AiAnalysisReport> update(@RequestBody AiAnalysisReport report) {
        return Result.success(reportService.update(report));
    }
    
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reportService.deleteById(id);
        return Result.success(null);
    }
}
