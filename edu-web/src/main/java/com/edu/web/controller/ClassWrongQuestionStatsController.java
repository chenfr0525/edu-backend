package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.*;
import com.edu.service.ClassService;
import com.edu.service.ClassWrongQuestionStatsService;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wrong-questions")
@RequiredArgsConstructor
public class ClassWrongQuestionStatsController {
  
    private final ClassWrongQuestionStatsService statsService;
    private final ClassService classService;
    
    @GetMapping("/list")
    public Result<List<ClassWrongQuestionStats>> list() {
        return Result.success(statsService.findAll());
    }
    
    @GetMapping("/{id}")
    public Result<ClassWrongQuestionStats> getById(@PathVariable Long id) {
        return Result.success(statsService.findById(id));
    }
    
    @GetMapping("/class/{classId}")
    public Result<List<ClassWrongQuestionStats>> getByClassId(@PathVariable Long classId) {
        ClassInfo classInfo = classService.getClassById(classId);
        return Result.success(statsService.findByClass(classInfo));
    }
    
    @GetMapping("/class/{classId}/rank")
    public Result<List<ClassWrongQuestionStats>> getTopWrongQuestions(
            @PathVariable Long classId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        ClassInfo classInfo = classService.getClassById(classId);
        return Result.success(statsService.findTopWrongQuestions(classInfo, date));
    }
    
    @GetMapping("/class/{classId}/high-error")
    public Result<List<ClassWrongQuestionStats>> getHighErrorRateQuestions(
            @PathVariable Long classId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        ClassInfo classInfo = classService.getClassById(classId);
        return Result.success(statsService.findHighErrorRateQuestions(classInfo, date));
    }
    
    @PostMapping
    public Result<ClassWrongQuestionStats> add(@RequestBody ClassWrongQuestionStats stats) {
        return Result.success(statsService.save(stats));
    }
    
    @PutMapping
    public Result<ClassWrongQuestionStats> update(@RequestBody ClassWrongQuestionStats stats) {
        return Result.success(statsService.update(stats));
    }
    
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        statsService.deleteById(id);
        return Result.success(null);
    }
}
