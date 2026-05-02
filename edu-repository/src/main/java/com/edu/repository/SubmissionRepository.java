package com.edu.repository;

import com.edu.domain.Submission;
import com.edu.domain.SubmissionStatus;
import com.edu.domain.Student;
import com.edu.domain.Homework;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    // 查询某学生的所有提交
    List<Submission> findByStudent(Student student);
    
    // 查询某作业的所有提交
    List<Submission> findByHomework(Homework homework);
 @Modifying
    @Query("DELETE FROM Submission skm WHERE skm.student.id = :studentId")
    void deleteByStudentId(Long studentId);
    
    // 查询某学生某作业的提交
    Submission findByHomeworkAndStudent(Homework homework, Student student);

     // 统计学生作业完成率 - 修正：使用 student.id
    @Query("SELECT COUNT(s) FROM Submission s WHERE s.student.id = :studentId AND s.status = com.edu.domain.SubmissionStatus.GRADED")
long countCompletedByStudent(@Param("studentId") Long studentId);
    
    // 统计学生平均作业分 - 修正：使用 student.id
    @Query("SELECT AVG(s.score) FROM Submission s WHERE s.student.id = :studentId AND s.score IS NOT NULL")
    Double getAvgScoreByStudent(@Param("studentId") Long studentId);

    @Query("SELECT COUNT(s) FROM Submission s WHERE s.student = :student AND s.submissionLateMinutes > 0")
    long countLateByStudent(@Param("student") Student student);

     // 统计作业按时提交率
    @Query("SELECT COUNT(s) FROM Submission s WHERE s.homework.id = :homeworkId AND s.submissionLateMinutes = 0 AND s.status != com.edu.domain.SubmissionStatus.PENDING")
long countOnTimeByHomeworkId(@Param("homeworkId") Long homeworkId);
    
    // 统计已提交人数
    long countByHomeworkIdAndStatusNot(Long homeworkId, SubmissionStatus status);
    
    // 获取作业成绩分布
    @Query("SELECT s.score FROM Submission s WHERE s.homework.id = :homeworkId AND s.score IS NOT NULL")
    List<Integer> findScoresByHomeworkId(@Param("homeworkId") Long homeworkId);
    
@Query(value = "SELECT " +
       "  kp.id as knowledge_point_id, " +
       "  kp.name as knowledge_point_name, " +
       "  JSON_EXTRACT(s.knowledge_point_scores, CONCAT('$.\"', kp.id, '\"')) as kp_score, " +
       "  COUNT(DISTINCT s.student_id) as error_count " +
       "FROM submission s " +
       "CROSS JOIN knowledge_point kp " +
       "WHERE s.homework_id = :homeworkId " +
       "  AND s.status = 'GRADED' " +
       "  AND CAST(JSON_EXTRACT(s.knowledge_point_scores, CONCAT('$.\"', kp.id, '\"')) AS UNSIGNED) < 6 " +
       "GROUP BY kp.id, kp.name, JSON_EXTRACT(s.knowledge_point_scores, CONCAT('$.\"', kp.id, '\"'))", 
       nativeQuery = true)
List<Object[]> countKnowledgePointErrors(@Param("homeworkId") Long homeworkId);

    /**
     * 根据学生ID和作业ID获取提交记录
     */
    Optional<Submission> findByStudentIdAndHomeworkId(Long studentId, Long homeworkId);
    
    /**
     * 获取作业的所有已批改提交
     */
    @Query("SELECT s FROM Submission s WHERE s.homework.id = :homeworkId AND s.status = 'GRADED'")
    List<Submission> findGradedByHomeworkId(@Param("homeworkId") Long homeworkId);

    
    
    /**
     * 获取学生所有已批改的作业提交
     */
    @Query("SELECT s FROM Submission s WHERE s.student.id = :studentId ORDER BY s.submittedAt DESC")
    List<Submission> findGradedByStudentId(@Param("studentId") Long studentId);
    
    /**
     * 获取学生某课程的所有已批改提交
     */
    @Query("SELECT s FROM Submission s " +
           "WHERE s.student.id = :studentId " +
           "AND s.homework.course.id = :courseId " +
           "AND s.status = 'GRADED' " +
           "ORDER BY s.submittedAt ASC")
    List<Submission> findByStudentIdAndCourseIdGraded(@Param("studentId") Long studentId, 
                                                       @Param("courseId") Long courseId);
    
    /**
     * 统计学生迟交次数
     */
    @Query("SELECT COUNT(s) FROM Submission s " +
           "WHERE s.student.id = :studentId " +
           "AND s.submissionLateMinutes > 0 " +
           "AND s.status = 'GRADED'")
    long countLateByStudentId(@Param("studentId") Long studentId);
    
    
}
