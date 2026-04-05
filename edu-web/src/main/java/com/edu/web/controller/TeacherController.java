package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.ClassInfo;
import com.edu.domain.Exam;
import com.edu.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
    public Result<List<ClassInfo>> getClasses(Authentication authentication) {
        return Result.success(teacherService.getClassesByTeacher(authentication.getName()));
    }

    @GetMapping("/exams")
    public Result<List<Exam>> getExams(@RequestParam Long classId) {
        return Result.success(teacherService.getExamsByClass(classId));
    }

    @GetMapping("/grade-distribution")
    public Result<Map<String, Object>> getGradeDistribution(@RequestParam Long examId) {
        return Result.success(teacherService.getGradeDistribution(examId));
    }

    @GetMapping("/high-frequency-errors")
    public Result<Map<String, Object>> getHighFrequencyErrors(@RequestParam Long examId) {
        return Result.success(teacherService.getHighFrequencyErrors(examId));
    }
}
