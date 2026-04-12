package com.edu.repository;

import com.edu.domain.KnowledgePoint;
import com.edu.domain.KnowledgePointScoreDetail;
import com.edu.domain.Student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface KnowledgePointScoreDetailRepository extends JpaRepository<KnowledgePointScoreDetail, Long> {
   // 查询某学生的所有知识点得分记录
    List<KnowledgePointScoreDetail> findByStudent(Student student);
    
    // 查询某学生某知识点的所有得分记录
    List<KnowledgePointScoreDetail> findByStudentAndKnowledgePoint(Student student, KnowledgePoint knowledgePoint);
    
    // 查询某学生最近一次某知识点的得分率
    KnowledgePointScoreDetail findFirstByStudentAndKnowledgePointOrderByCreatedAtDesc(
        Student student, KnowledgePoint knowledgePoint);
    
    // 查询学生平均得分率（所有知识点）
    @Query("SELECT AVG(k.scoreRate) FROM KnowledgePointScoreDetail k WHERE k.student = :student")
    BigDecimal getStudentAvgScoreRate(Student student);
}
