package com.edu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.edu.domain.Course;
import com.edu.domain.ScorePrediction;
import com.edu.domain.Student;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ScorePredictionRepository extends JpaRepository<ScorePrediction, Long> {
    // 查询某学生某课程的最新预测
    ScorePrediction findFirstByStudentAndCourseOrderByPredictionDateDesc(Student student, Course course);
    
    // 查询某学生的所有预测
    List<ScorePrediction> findByStudent(Student student);
    
    // 查询待验证的预测（已有实际成绩）
    List<ScorePrediction> findByActualScoreIsNotNull();
    
    // 查询预测准确率高的记录
    List<ScorePrediction> findByActualScoreIsNotNullAndPredictedScoreBetween(
        BigDecimal min, BigDecimal max);
}
