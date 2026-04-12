package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.*;
import com.edu.service.ActivityRecordService;
import com.edu.service.StudentService;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityRecordController {
  private final ActivityRecordService activityRecordService;
    private final StudentService studentService;
    
    @GetMapping("/list")
    public Result<List<ActivityRecord>> list() {
        return Result.success(activityRecordService.findAll());
    }
    
    @GetMapping("/{id}")
    public Result<ActivityRecord> getById(@PathVariable Long id) {
        return Result.success(activityRecordService.findById(id));
    }
    
    @GetMapping("/student/{studentId}")
    public Result<List<ActivityRecord>> getByStudentId(@PathVariable Long studentId) {
        Student student = studentService.findById(studentId).orElse(null);
        return Result.success(activityRecordService.findByStudent(student));
    }
    
    @GetMapping("/student/{studentId}/range")
    public Result<List<ActivityRecord>> getByDateRange(
            @PathVariable Long studentId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        Student student = studentService.findById(studentId).orElse(null);
        return Result.success(activityRecordService.findByStudentAndDateRange(student, start, end));
    }
    
    @GetMapping("/class/{classId}/date")
    public Result<List<ActivityRecord>> getByClassAndDate(
            @PathVariable Long classId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return Result.success(activityRecordService.findByClassIdAndDate(classId, date));
    }
    
    @GetMapping("/student/{studentId}/score")
    public Result<Double> getActivityScore(@PathVariable Long studentId) {
        return Result.success(activityRecordService.getStudentTotalActivityScore(studentId));
    }
    
    @GetMapping("/low-activity")
    public Result<List<Long>> getLowActivityStudents(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam Double threshold) {
        return Result.success(activityRecordService.findLowActivityStudents(startDate, threshold));
    }
    
    @PostMapping
    public Result<ActivityRecord> add(@RequestBody ActivityRecord activityRecord) {
        return Result.success(activityRecordService.save(activityRecord));
    }
    
    @PutMapping
    public Result<ActivityRecord> update(@RequestBody ActivityRecord activityRecord) {
        return Result.success(activityRecordService.update(activityRecord));
    }
    
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        activityRecordService.deleteById(id);
        return Result.success(null);
    }
}
