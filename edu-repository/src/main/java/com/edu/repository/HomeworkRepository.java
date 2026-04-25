package com.edu.repository;

import com.edu.domain.Homework;
import com.edu.domain.HomeworkStatus;
import com.edu.domain.KnowledgePoint;
import com.edu.domain.Submission;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.edu.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface HomeworkRepository extends JpaRepository<Homework, Long> {
    // 根据单个课程查询
    List<Homework> findByCourse(Course course);
    
    // 根据多个课程查询（这就是你需要的）
    List<Homework> findByCourseIn(List<Course> courses);
    
    
    // 根据知识点查询
    List<Homework> findByKnowledgePoint(KnowledgePoint knowledgePoint);
    
    // 根据状态查询
    List<Homework> findByStatus(HomeworkStatus status);
    
    // 根据课程和状态查询
    List<Homework> findByCourseAndStatus(Course course, HomeworkStatus status);
    
    // 根据截止日期范围查询
    List<Homework> findByDeadlineBefore(LocalDateTime deadline);
    
    // 分页查询某课程下的作业
    Page<Homework> findByCourse(Course course, Pageable pageable);

    
    // 查询未截止的作业
    List<Homework> findByDeadlineAfterAndStatus(LocalDateTime now, String status);

    
    // 统计某课程作业平均分
    @Query("SELECT AVG(h.avgScore) FROM Homework h WHERE h.course.id = :courseId")
    Double getAvgScoreByCourse(@Param("courseId") Long courseId);

    // 新增：按学期查询（通过课程关联）
    @Query("SELECT DISTINCT h FROM Homework h JOIN h.course c JOIN Enrollment e ON e.course = c WHERE e.semester.id = :semesterId")
    List<Homework> findBySemesterId(@Param("semesterId") Long semesterId);
    
    // 带筛选条件的查询
    @Query("SELECT h FROM Homework h WHERE h.course.id IN :courseIds " +
           "AND (:keyword IS NULL OR h.name LIKE %:keyword%)")
    Page<Homework> findWithFilters(@Param("courseIds") List<Long> courseIds,
                                    @Param("keyword") String keyword,
                                    Pageable pageable);
    
    // 统计卡片：作业平均分
    @Query("SELECT AVG(h.avgScore) FROM Homework h WHERE h.course.id IN :courseIds AND h.avgScore IS NOT NULL")
    BigDecimal getAvgScoreAll(@Param("courseIds") List<Long> courseIds);
    
    // 统计卡片：作业及格率
    @Query("SELECT AVG(h.passRate) FROM Homework h WHERE h.course.id IN :courseIds AND h.passRate IS NOT NULL")
    BigDecimal getAvgPassRateAll(@Param("courseIds") List<Long> courseIds);
    
    // 统计卡片：作业总数
    long countByCourseIdIn(List<Long> courseIds);
    
    // 趋势图数据
    @Query("SELECT h.createdAt, h.avgScore FROM Homework h WHERE h.course.id IN :courseIds AND h.avgScore IS NOT NULL ORDER BY h.createdAt")
    List<Object[]> getScoreTrend(@Param("courseIds") List<Long> courseIds);

     /**
     * 获取某课程的所有作业（按截止时间倒序）
     */
    @Query("SELECT h FROM Homework h WHERE h.course.id = :courseId AND h.status != com.edu.domain.HomeworkStatus.PENDING ORDER BY h.deadline DESC")
    List<Homework> findByCourseIdOrderByDeadlineDesc(@Param("courseId") Long courseId);
    
    /**
     * 获取学生某学期的所有作业（通过选课关联）
     */
    @Query("SELECT DISTINCT h FROM Homework h " +
           "JOIN h.course c " +
           "JOIN Enrollment e ON e.course = c " +
           "WHERE e.student.id = :studentId " +
           "AND e.semester.id = :semesterId " +
           "ORDER BY h.deadline DESC")
    List<Homework> findByStudentIdAndSemesterId(@Param("studentId") Long studentId, 
                                                 @Param("semesterId") Long semesterId);

    /**
     * 获取学生的所有作业（通过选课关联）
     */
    @Query("SELECT DISTINCT h FROM Homework h " +
           "JOIN h.course c " +
           "JOIN Enrollment e ON e.course = c " +
           "WHERE e.student.id = :studentId " +
           "ORDER BY h.deadline DESC")
    List<Homework> findByStudentId(@Param("studentId") Long studentId);
    
    /**
     * 获取学生某课程的所有作业
     */
    @Query("SELECT h FROM Homework h " +
           "JOIN h.course c " +
           "JOIN Enrollment e ON e.course = c " +
           "WHERE e.student.id = :studentId " +
           "AND c.id = :courseId " +
           "ORDER BY h.deadline DESC")
    List<Homework> findByStudentIdAndCourseId(@Param("studentId") Long studentId, 
                                               @Param("courseId") Long courseId);
}
