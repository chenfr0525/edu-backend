package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.Grade;
import com.edu.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentController {
    private final StudentService studentService;

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats(Authentication authentication) {
        return Result.success(studentService.getStudentStats(authentication.getName()));
    }

    @GetMapping("/grade-trends")
    public Result<List<Grade>> getGradeTrends(Authentication authentication) {
        return Result.success(studentService.getGradeTrends(authentication.getName()));
    }
}
