package com.edu.repository;

import com.edu.domain.ClassInfo;
import com.edu.domain.Course;
import com.edu.domain.Exam;
import com.edu.domain.ExamStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    List<Exam> findByClassInfo(ClassInfo classInfo);

    List<Exam> findByClassInfoIn(List<ClassInfo> classInfos);

    List<Exam> findByClassInfoIdIn(List<Long> classInfoIds);

    List<Exam> findByCourse(Course course);
    
    // 根据类型查询
    List<Exam> findByType(ExamStatus type);
    
    // 查询即将到来的考试
    List<Exam> findByExamDateAfterAndStatus(LocalDateTime date, ExamStatus status);
    
    // 查询某班级某课程的考试列表
    List<Exam> findByClassInfoAndCourse(ClassInfo classInfo, Course course);
    
    // 查询班级考试平均分
    @Query("SELECT AVG(e.classAvgScore) FROM Exam e WHERE e.classInfo.id = :classId")
    Double getClassAvgScore(@Param("classId") Long classId);

     /**
     * 获取学生某学期的所有考试（通过班级和课程）
     */
    @Query("SELECT DISTINCT e FROM Exam e " +
           "WHERE e.classInfo.id IN (" +
           "  SELECT s.classInfo.id FROM Student s WHERE s.id = :studentId" +
           ") " +
           "AND e.status = 'COMPLETED' " +
           "ORDER BY e.examDate DESC")
    List<Exam> findByStudentIdAndCompleted(@Param("studentId") Long studentId);
    
    /**
     * 获取学生某课程的所有考试
     */
    @Query("SELECT e FROM Exam e " +
           "WHERE e.classInfo.id IN (" +
           "  SELECT s.classInfo.id FROM Student s WHERE s.id = :studentId" +
           ") " +
           "AND e.course.id = :courseId " +
           "AND e.status = 'COMPLETED' " +
           "ORDER BY e.examDate DESC")
    List<Exam> findByStudentIdAndCourseId(@Param("studentId") Long studentId, 
                                           @Param("courseId") Long courseId);
    
                                            /**
     * 根据学生ID获取
     */
    @Query("SELECT e FROM Exam e " +
           "WHERE e.classInfo.id IN (" +
           "  SELECT s.classInfo.id FROM Student s WHERE s.id = :studentId" +
           ") " +
           "ORDER BY e.examDate ASC")
    List<Exam> findByStudentId(@Param("studentId") Long studentId);

    /**
     * 获取即将到来的考试
     */
    @Query("SELECT e FROM Exam e " +
           "WHERE e.classInfo.id IN (" +
           "  SELECT s.classInfo.id FROM Student s WHERE s.id = :studentId" +
           ") " +
           "AND e.examDate >= CURRENT_DATE " +
           "AND e.status != 'COMPLETED' " +
           "ORDER BY e.examDate ASC")
    List<Exam> findUpcomingByStudentId(@Param("studentId") Long studentId);
}
