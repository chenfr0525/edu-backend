package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.*;
import com.edu.service.KnowledgePointScoreDetailService;
import com.edu.service.KnowledgePointService;
import com.edu.service.StudentService;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kp-score-detail")
@RequiredArgsConstructor
public class KnowledgePointScoreDetailController {
  
    private final KnowledgePointScoreDetailService detailService;
    private final StudentService studentService;
    private final KnowledgePointService knowledgePointService;
    
    @GetMapping("/list")
    public Result<List<KnowledgePointScoreDetail>> list() {
        return Result.success(detailService.findAll());
    }
    
    @GetMapping("/{id}")
    public Result<KnowledgePointScoreDetail> getById(@PathVariable Long id) {
        return Result.success(detailService.findById(id));
    }
    
    @GetMapping("/student/{studentId}")
    public Result<List<KnowledgePointScoreDetail>> getByStudentId(@PathVariable Long studentId) {
        Student student = studentService.findById(studentId).orElse(null);
        return Result.success(detailService.findByStudent(student));
    }
    
    @GetMapping("/student/{studentId}/knowledge/{kpId}")
    public Result<List<KnowledgePointScoreDetail>> getByStudentAndKnowledge(
            @PathVariable Long studentId,
            @PathVariable Long kpId) {
        Student student = studentService.findById(studentId).orElse(null);
        KnowledgePoint knowledgePoint = knowledgePointService.findById(kpId).orElse(null);
        return Result.success(detailService.findByStudentAndKnowledgePoint(student, knowledgePoint));
    }
    
    @GetMapping("/student/{studentId}/radar")
    public Result<List<KnowledgePointScoreDetail>> getRadarData(@PathVariable Long studentId) {
        Student student = studentService.findById(studentId).orElse(null);
        return Result.success(detailService.findByStudent(student));
    }
    
    @GetMapping("/student/{studentId}/avg-score-rate")
    public Result<BigDecimal> getAvgScoreRate(@PathVariable Long studentId) {
        Student student = studentService.findById(studentId).orElse(null);
        return Result.success(detailService.getStudentAverageScoreRate(student));
    }
    
    @PostMapping
    public Result<KnowledgePointScoreDetail> add(@RequestBody KnowledgePointScoreDetail detail) {
        return Result.success(detailService.save(detail));
    }
    
    @PutMapping
    public Result<KnowledgePointScoreDetail> update(@RequestBody KnowledgePointScoreDetail detail) {
        return Result.success(detailService.update(detail));
    }
    
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        detailService.deleteById(id);
        return Result.success(null);
    }
}
