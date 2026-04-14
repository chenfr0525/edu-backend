package com.edu.repository;

import com.edu.domain.Student;
import com.edu.domain.Exam;
import com.edu.domain.ExamGrade;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamGradeRepository extends JpaRepository<ExamGrade, Long> {
  
  List<ExamGrade> findByExamAndStudent(Exam exam, Student student);

  List<ExamGrade> findByExamInAndStudent(List<Exam> exams, Student student);

  List<ExamGrade> findByExamInAndStudentIn(List<Exam> exams, List<Student> students);

  // 查询某学生的所有成绩
    List<ExamGrade> findByStudent(Student student);
     @Modifying
    @Query("DELETE FROM ExamGrade skm WHERE skm.student.id = :studentId")
    void deleteByStudentId(Long studentId);
  List<ExamGrade> findByExam(Exam exam); 
    
    // 查询学生历次考试成绩（用于趋势图）
    List<ExamGrade> findByStudentOrderByCreatedAtAsc(Student student);
    
    // 查询班级成绩排名
    List<ExamGrade> findByExamOrderByScoreDesc(Exam exam);
    
     // 查询学生平均分
    @Query("SELECT AVG(eg.score) FROM ExamGrade eg WHERE eg.student.id = :studentId")
    Double getStudentAvgScore(@Param("studentId") Long studentId);
    
    // 查询学生最新排名 - 修正：使用 Pageable 或返回 List
    @Query("SELECT eg.classRank FROM ExamGrade eg WHERE eg.student.id = :studentId ORDER BY eg.createdAt DESC")
    List<Integer> getLatestRank(@Param("studentId") Long studentId);
    
    // 查询学生进步/退步情况 - 修正：使用 Pageable 或返回 List
    @Query("SELECT eg.scoreTrend FROM ExamGrade eg WHERE eg.student.id = :studentId ORDER BY eg.createdAt DESC")
    List<String> getLatestTrend(@Param("studentId") Long studentId);
    
    // 可选：添加使用 Pageable 的版本
    @Query("SELECT eg.classRank FROM ExamGrade eg WHERE eg.student.id = :studentId ORDER BY eg.createdAt DESC")
    List<Integer> getLatestRank(@Param("studentId") Long studentId, Pageable pageable);
    
    @Query("SELECT eg.scoreTrend FROM ExamGrade eg WHERE eg.student.id = :studentId ORDER BY eg.createdAt DESC")
    List<String> getLatestTrend(@Param("studentId") Long studentId, Pageable pageable);

     /**
     * 获取学生某考试的成绩
     */
    Optional<ExamGrade> findByExamIdAndStudentId(Long examId, Long studentId);
    
    /**
     * 获取学生所有考试成绩（按时间正序）
     */
    @Query("SELECT eg FROM ExamGrade eg " +
           "WHERE eg.student.id = :studentId " +
           "AND eg.score IS NOT NULL " +
           "ORDER BY eg.exam.examDate ASC")
    List<ExamGrade> findAllByStudentIdOrderByDateAsc(@Param("studentId") Long studentId);
    
    /**
     * 获取学生某课程的所有考试成绩
     */
    @Query("SELECT eg FROM ExamGrade eg " +
           "WHERE eg.student.id = :studentId " +
           "AND eg.exam.course.id = :courseId " +
           "AND eg.score IS NOT NULL " +
           "ORDER BY eg.exam.examDate ASC")
    List<ExamGrade> findByStudentIdAndCourseIdOrderByDateAsc(@Param("studentId") Long studentId,
                                                              @Param("courseId") Long courseId);
    
    /**
     * 获取考试的所有成绩（用于统计）
     */
    @Query("SELECT eg.score FROM ExamGrade eg WHERE eg.exam.id = :examId AND eg.score IS NOT NULL")
    List<Integer> findScoresByExamId(@Param("examId") Long examId);
    
    /**
     * 统计学生高于班级平均分的考试次数
     */
    @Query("SELECT COUNT(eg) FROM ExamGrade eg " +
           "WHERE eg.student.id = :studentId " +
           "AND eg.score > (SELECT AVG(eg2.score) FROM ExamGrade eg2 WHERE eg2.exam.id = eg.exam.id)")
    Long countAboveClassAvg(@Param("studentId") Long studentId);

/**
 * 获取某班级某课程的所有成绩（用于统计）
 */
@Query("SELECT eg FROM ExamGrade eg " +
       "WHERE eg.student.classInfo.id = :classId " +
       "AND eg.exam.course.id = :courseId " +
       "AND eg.score IS NOT NULL")
List<ExamGrade> findByClassIdAndCourseId(@Param("classId") Long classId, 
                                          @Param("courseId") Long courseId);
/**
 * 获取某课程下所有班级的成绩
 */
@Query("SELECT eg FROM ExamGrade eg " +
       "WHERE eg.exam.course.id = :courseId " +
       "AND eg.score IS NOT NULL")
List<ExamGrade> findByCourseId(@Param("courseId") Long courseId);

}
