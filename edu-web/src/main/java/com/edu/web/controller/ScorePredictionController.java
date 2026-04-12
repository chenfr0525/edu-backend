package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.*;
import com.edu.service.CourseService;
import com.edu.service.ScorePredictionService;
import com.edu.service.StudentService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/prediction")
@RequiredArgsConstructor
public class ScorePredictionController {
   private final ScorePredictionService predictionService;
    private final StudentService studentService;
    private final CourseService courseService;
    
    @GetMapping("/list")
    public Result<List<ScorePrediction>> list() {
        return Result.success(predictionService.findAll());
    }
    
    @GetMapping("/{id}")
    public Result<ScorePrediction> getById(@PathVariable Long id) {
        return Result.success(predictionService.findById(id));
    }
    
    @GetMapping("/student/{studentId}/course/{courseId}")
    public Result<ScorePrediction> getLatestPrediction(
            @PathVariable Long studentId,
            @PathVariable Long courseId) {
        Student student = studentService.findById(studentId).orElse(null);
        Course course = courseService.findById(courseId).orElse(null);
        return Result.success(predictionService.findLatestByStudentAndCourse(student, course));
    }
    
    @GetMapping("/student/{studentId}")
    public Result<List<ScorePrediction>> getByStudentId(@PathVariable Long studentId) {
        Student student = studentService.findById(studentId).orElse(null);
        return Result.success(predictionService.findByStudent(student));
    }
    
    @GetMapping("/pending")
    public Result<List<ScorePrediction>> getPendingVerification() {
        return Result.success(predictionService.findPendingVerification());
    }
    
    @PostMapping
    public Result<ScorePrediction> add(@RequestBody ScorePrediction prediction) {
        return Result.success(predictionService.save(prediction));
    }
    
    @PutMapping
    public Result<ScorePrediction> update(@RequestBody ScorePrediction prediction) {
        return Result.success(predictionService.update(prediction));
    }
    
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        predictionService.deleteById(id);
        return Result.success(null);
    }
}
