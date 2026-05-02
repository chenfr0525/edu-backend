package com.edu.repository;

import com.edu.domain.KnowledgePoint;
import com.edu.domain.Student;
import com.edu.domain.StudentKnowledgeMastery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentKnowledgeMasteryRepository extends JpaRepository<StudentKnowledgeMastery, Long> {
  Optional<StudentKnowledgeMastery> findByStudentAndKnowledgePoint(Student student, KnowledgePoint knowledgePoint);
 @Modifying
    @Query("DELETE FROM StudentKnowledgeMastery skm WHERE skm.student.id = :studentId")
  void deleteByStudentId(Long studentId);
   // 查询某学生的所有知识点掌握情况
    List<StudentKnowledgeMastery> findByStudent(Student student);

    // 查询某学生的所有知识点掌握情况
    List<StudentKnowledgeMastery> findByStudentId(Long studentId);
    
    
    // 查询学生的薄弱知识点（掌握度低于阈值）
    List<StudentKnowledgeMastery> findByStudentAndMasteryLevelLessThan(Student student, Double threshold);
    
    // 查询学生的优势知识点（掌握度高于阈值）
    List<StudentKnowledgeMastery> findByStudentAndMasteryLevelGreaterThan(Student student, Double threshold);
    
    // 获取班级平均掌握度 - 修正：使用关联对象
    @Query("SELECT AVG(skm.masteryLevel) FROM StudentKnowledgeMastery skm " +
           "WHERE skm.knowledgePoint.id = :kpId " +
           "AND skm.student.classInfo.id = :classId")
    Double getClassAvgMastery(@Param("kpId") Long kpId, @Param("classId") Long classId);
    
    // 获取学生某个知识点的掌握度
    @Query("SELECT skm.masteryLevel FROM StudentKnowledgeMastery skm " +
           "WHERE skm.student.id = :studentId AND skm.knowledgePoint.id = :kpId")
    Double getStudentKnowledgeMastery(@Param("studentId") Long studentId, @Param("kpId") Long kpId);

     /**
     * 查询某学生某课程的所有知识点掌握度
     */
    @Query("SELECT skm FROM StudentKnowledgeMastery skm " +
           "WHERE skm.student.id = :studentId " +
           "AND skm.knowledgePoint.course.id = :courseId")
    List<StudentKnowledgeMastery> findByStudentIdAndCourseId(@Param("studentId") Long studentId,
                                                              @Param("courseId") Long courseId);
    
    /**
     * 查询某学生所有知识点掌握度
     */
    @Query("SELECT skm FROM StudentKnowledgeMastery skm " +
           "WHERE skm.student.id = :studentId")
    List<StudentKnowledgeMastery> findAllByStudentId(@Param("studentId") Long studentId);


    /**
 * 查询班级在某课程下的知识点掌握度
 */
@Query("SELECT skm FROM StudentKnowledgeMastery skm " +
       "WHERE skm.student.classInfo.id = :classId " +
       "AND skm.knowledgePoint.course.id = :courseId")
List<StudentKnowledgeMastery> findByClassIdAndCourseId(@Param("classId") Long classId,
                                                         @Param("courseId") Long courseId);

/**
 * 查询某课程下所有学生的知识点掌握度
 */
@Query("SELECT skm FROM StudentKnowledgeMastery skm " +
       "WHERE skm.knowledgePoint.course.id = :courseId")
List<StudentKnowledgeMastery> findByCourseId(@Param("courseId") Long courseId);

}
