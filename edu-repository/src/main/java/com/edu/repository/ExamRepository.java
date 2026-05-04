package com.edu.repository;

import com.edu.domain.ClassInfo;
import com.edu.domain.Course;
import com.edu.domain.Exam;
import com.edu.domain.ExamStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    boolean existsByClassInfoAndCourseAndName(ClassInfo classInfo, Course course,String name);
    
    // 查询班级考试平均分
    @Query("SELECT AVG(e.classAvgScore) FROM Exam e WHERE e.classInfo.id = :classId")
    Double getClassAvgScore(@Param("classId") Long classId);

    @Query("SELECT e FROM Exam e WHERE e.classInfo.id = :classId")
    List<Exam> findByClassId(@Param("classId") Long classId);

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

    /**
 * 根据班级列表查询考试（分页）
 */
@Query("SELECT e FROM Exam e WHERE e.classInfo.id IN :classIds")
Page<Exam> findByClassIds(@Param("classIds") List<Long> classIds, Pageable pageable);

/**
 * 根据班级列表和关键词模糊查询
 */
@Query("SELECT e FROM Exam e WHERE e.classInfo.id IN :classIds AND e.name LIKE %:keyword%")
Page<Exam> findByClassIdsAndKeyword(@Param("classIds") List<Long> classIds,
                                     @Param("keyword") String keyword,
                                     Pageable pageable);

/**
 * 根据课程查询考试
 */
@Query("SELECT e FROM Exam e WHERE e.course.id = :courseId")
Page<Exam> findByCourseId(@Param("courseId") Long courseId, Pageable pageable);

/**
 * 根据课程和关键词模糊查询
 */
@Query("SELECT e FROM Exam e WHERE e.course.id = :courseId AND e.name LIKE %:keyword%")
Page<Exam> findByCourseIdAndKeyword(@Param("courseId") Long courseId,
                                     @Param("keyword") String keyword,
                                     Pageable pageable);

/**
 * 获取某课程的所有考试
 */
List<Exam> findByCourseId(Long courseId);

 /**
     * 查找同课程且考试日期在当前之前的考试（用于成绩对比分析）
     * @param course 课程对象
     * @param examDate 当前考试的日期
     * @return 同一课程在指定日期之前的所有考试，按考试日期降序排列
     */
    @Query("SELECT e FROM Exam e " +
           "WHERE e.course = :course " +
           "AND e.examDate < :examDate " +
           "AND e.status = 'COMPLETED' " +
           "ORDER BY e.examDate DESC")
    List<Exam> findByCourseAndExamDateBefore(@Param("course") Course course,
                                              @Param("examDate") LocalDateTime examDate);


}
