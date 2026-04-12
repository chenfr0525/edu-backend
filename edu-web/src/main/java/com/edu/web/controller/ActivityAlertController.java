package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.*;
import com.edu.service.ActivityAlertService;
import com.edu.service.ClassService;
import com.edu.service.StudentService;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alert")
@RequiredArgsConstructor
public class ActivityAlertController {
  private final ActivityAlertService alertService;
    private final StudentService studentService;
    private final ClassService classService;

       @GetMapping("/list")
    public Result<List<ActivityAlert>> list() {
        return Result.success(alertService.findAll());
    }
    
    @GetMapping("/{id}")
    public Result<ActivityAlert> getById(@PathVariable Long id) {
        return Result.success(alertService.findById(id));
    }
    
    @GetMapping("/unresolved")
    public Result<List<ActivityAlert>> getUnresolved() {
        return Result.success(alertService.findUnresolved());
    }
    
    @GetMapping("/student/{studentId}")
    public Result<List<ActivityAlert>> getByStudentId(@PathVariable Long studentId) {
        Student student = studentService.findById(studentId).orElse(null);
        return Result.success(alertService.findByStudent(student));
    }
    
    @GetMapping("/class/{classId}/unresolved")
    public Result<List<ActivityAlert>> getUnresolvedByClassId(@PathVariable Long classId) {
        ClassInfo classInfo = classService.getClassById(classId);
        return Result.success(alertService.findUnresolvedByClass(classInfo));
    }
    
    @GetMapping("/critical")
    public Result<List<ActivityAlert>> getCriticalAlerts() {
        return Result.success(alertService.findCriticalAlerts());
    }
    
    @PostMapping
    public Result<ActivityAlert> add(@RequestBody ActivityAlert alert) {
        return Result.success(alertService.save(alert));
    }
    
    @PutMapping
    public Result<ActivityAlert> update(@RequestBody ActivityAlert alert) {
        return Result.success(alertService.update(alert));
    }
    
    @PutMapping("/{id}/resolve")
    public Result<Void> resolveAlert(@PathVariable Long id) {
        alertService.resolveAlert(id);
        return Result.success(null);
    }
    
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        alertService.deleteById(id);
        return Result.success(null);
    }
}
