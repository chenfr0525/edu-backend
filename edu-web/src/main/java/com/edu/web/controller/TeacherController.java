package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.*;
import com.edu.domain.dto.TeacherStatsDTO;
import com.edu.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teacher")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class TeacherController {
    private final TeacherService teacherService;

    @GetMapping("/classes")
    public Result<List<ClassInfo>> getClasses(org.springframework.security.core.Authentication authentication) {
        return Result.success(teacherService.getClassesByTeacher(authentication.getName()));
    }

    @GetMapping("/exams")
    public Result<List<Exam>> getExams(@RequestParam Long classId) {
        return Result.success(teacherService.getExamsByClass(classId));
    }

    @GetMapping("/stats")
    public Result<TeacherStatsDTO> getStats(@RequestParam Long classId) {
        return Result.success(teacherService.getTeacherStats(classId));
    }

    @GetMapping("/grade-distribution")
    public Result<Map<String, Object>> getGradeDistribution(@RequestParam Long classId, @RequestParam Long examId) {
        return Result.success(teacherService.getGradeDistribution(classId, examId));
    }

    @GetMapping("/high-frequency-errors")
    public Result<Map<String, Object>> getHighFrequencyErrors(@RequestParam Long classId, @RequestParam Long examId) {
        return Result.success(teacherService.getHighFrequencyErrors(classId, examId));
    }

    // Student Management
    @GetMapping("/students")
    public Result<com.edu.common.PageResult<User>> getStudents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize);
        return Result.success(com.edu.common.PageResult.of(teacherService.getStudents(keyword, classId, status, pageable)));
    }

    @PostMapping("/student")
    public Result<User> createStudent(@RequestBody User student) {
        return Result.success(teacherService.createStudent(student));
    }

    @PutMapping("/student/{id}")
    public Result<User> updateStudent(@PathVariable Long id, @RequestBody User student) {
        return Result.success(teacherService.updateStudent(id, student));
    }

    @DeleteMapping("/student/{id}")
    public Result<Void> deleteStudent(@PathVariable Long id) {
        teacherService.deleteStudent(id);
        return Result.success();
    }

    @PostMapping("/student/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        teacherService.resetPassword(id, body.get("password"));
        return Result.success();
    }
}
