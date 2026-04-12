package com.edu.repository;

import com.edu.domain.ActivityRecord;
import com.edu.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActivityRecordRepository extends JpaRepository<ActivityRecord, Long> {
    List<ActivityRecord> findByStudent(Student student);

     // 查询某学生在某段时间的活动记录
    List<ActivityRecord> findByStudentAndActivityDateBetween(Student student, LocalDateTime start, LocalDateTime end);
    
    // 查询某班级某天的活动记录 - 修正：使用关联对象
    @Query("SELECT a FROM ActivityRecord a WHERE a.student.classInfo.id = :classId AND a.activityDate = :date")
    List<ActivityRecord> findByClassIdAndDate(@Param("classId") Long classId, @Param("date") LocalDate date);
    
    // 统计学生活跃度得分 - 修正：使用关联对象
    @Query("SELECT COALESCE(SUM(a.activityScore), 0) FROM ActivityRecord a WHERE a.student.id = :studentId")
    Double getTotalActivityScore(@Param("studentId") Long studentId);
    
    // 查询低活跃度学生（最近7天活跃度低于阈值）- 修正：使用关联对象
    @Query("SELECT a.student.id FROM ActivityRecord a " +
           "WHERE a.activityDate >= :startDate " +
           "GROUP BY a.student.id " +
           "HAVING COALESCE(SUM(a.activityScore), 0) < :threshold")
    List<Long> findLowActivityStudents(@Param("startDate") LocalDateTime  startDate, @Param("threshold") BigDecimal threshold);
    
    // 统计某学生本月学习总时长 - 修正：使用关联对象
    @Query("SELECT COALESCE(SUM(a.studyDuration), 0) FROM ActivityRecord a " +
           "WHERE a.student.id = :studentId AND a.activityDate >= :startDate")
    Integer getTotalStudyDuration(@Param("studentId") Long studentId, @Param("startDate") LocalDate startDate);
}
