package com.edu.repository;

import com.edu.domain.ErrorRecord;
import com.edu.domain.KnowledgePoint;
import com.edu.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ErrorRecordRepository extends JpaRepository<ErrorRecord, Long> {
    List<ErrorRecord> findByStudent(Student student);
    
    // 查询某学生某知识点的错题
    List<ErrorRecord> findByStudentAndKnowledgePoint(Student student, KnowledgePoint knowledgePoint);
    
    // 查询班级高频错题（按知识点统计）
    @Query("SELECT e.knowledgePoint.id, COUNT(e) as errorCount " +
           "FROM ErrorRecord e " +
           "WHERE e.student.id IN (SELECT s.id FROM Student s WHERE s.classInfo.id = :classId) " +
           "GROUP BY e.knowledgePoint.id " +
           "ORDER BY errorCount DESC")
    List<Object[]> findHighFrequencyErrorsByClass(@Param("classId") Long classId);
}
