package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.*;

import com.edu.service.EnrollmentService;
import com.edu.service.StudentService;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentController {
    private final StudentService studentService;
    private final EnrollmentService enrollmentService;


@GetMapping("/courseList")
public Result<List<Enrollment>> getStudentCourse(@RequestParam Long studentId) {
      Student student = studentService.findById(studentId).orElse(null);
        if (student == null) {
            return Result.error("学生不存在");
        }
     List<Enrollment> enrollments = enrollmentService.findByStudent(student);
    return Result.success(enrollments);
}
   

    // @GetMapping("/dashboard/stats")
    // public Result<StudentStatsDTO> getStats(Authentication authentication) {
    //     return Result.success(studentService.getStudentStats(authentication.getName()));
    // }

    // @GetMapping("/dashboard/knowledge")
    // public Result<KnowledgeMasteryDTO> getKnowledge(@RequestParam(required = false) Long courseId, Authentication authentication) {
    //     return Result.success(studentService.getKnowledgeMastery(authentication.getName(), courseId));
    // }

    // @GetMapping("/dashboard/grade-trend")
    // public Result<GradeTrendDTO> getGradeTrend(Authentication authentication) {
    //     return Result.success(studentService.getGradeTrend(authentication.getName()));
    // }

    // @GetMapping("/dashboard/attendance")
    // public Result<List<Object[]>> getAttendance(Authentication authentication) {
    //     return Result.success(studentService.getAttendanceHeatmap(authentication.getName()));
    // }

    // @GetMapping("/semesters")
    // public Result<List<Semester>> getSemesters() {
    //     return Result.success(studentService.getSemesters());
    // }

    // @GetMapping("/courses")
    // public Result<List<Enrollment>> getEnrollments(@RequestParam(required = false) String semester, Authentication authentication) {
    //     return Result.success(studentService.getEnrollments(authentication.getName(), semester));
    // }

    // @GetMapping("/homeworks")
    // public Result<List<Homework>> getHomeworks(Authentication authentication) {
    //     return Result.success(studentService.getHomeworks(authentication.getName()));
    // }

    // @PostMapping("/homework/submit")
    // public Result<Submission> submitHomework(@RequestBody Map<String, Object> body, Authentication authentication) {
    //     Long homeworkId = Long.valueOf(body.get("homeworkId").toString());
    //     String content = (String) body.get("content");
    //     String files = (String) body.get("files");
    //     return Result.success(studentService.submitHomework(authentication.getName(), homeworkId, content, files));
    // }
}
