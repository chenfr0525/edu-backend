package com.edu.repository;

import com.edu.domain.KnowledgePoint;
import com.edu.domain.KnowledgePointScoreDetail;
import com.edu.domain.Student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface KnowledgePointScoreDetailRepository extends JpaRepository<KnowledgePointScoreDetail, Long> {
   // 查询某学生的所有知识点得分记录
    List<KnowledgePointScoreDetail> findByStudent(Student student);
     @Modifying
    @Query("DELETE FROM  KnowledgePointScoreDetail skm WHERE skm.student.id = :studentId")
    void deleteByStudentId(Long studentId);
    // 查询某学生某知识点的所有得分记录
    List<KnowledgePointScoreDetail> findByStudentAndKnowledgePoint(Student student, KnowledgePoint knowledgePoint);
    
    // 查询某学生最近一次某知识点的得分率
    KnowledgePointScoreDetail findFirstByStudentAndKnowledgePointOrderByCreatedAtDesc(
        Student student, KnowledgePoint knowledgePoint);
    
    // 查询学生平均得分率（所有知识点）
    @Query("SELECT AVG(k.scoreRate) FROM KnowledgePointScoreDetail k WHERE k.student = :student")
    BigDecimal getStudentAvgScoreRateByStudent(Student student);

     /**
     * 查询某学生某课程的所有知识点得分记录（按知识点分组取最新）
     */
     @Query("SELECT k FROM KnowledgePointScoreDetail k " +
           "WHERE k.student.id = :studentId " +
           "AND k.knowledgePoint.course.id = :courseId " +
           "AND k.createdAt = (SELECT MAX(k2.createdAt) FROM KnowledgePointScoreDetail k2 " +
           "                  WHERE k2.student.id = k.student.id " +
           "                  AND k2.knowledgePoint.id = k.knowledgePoint.id)")
    List<KnowledgePointScoreDetail> findLatestByStudentIdAndCourseId(@Param("studentId") Long studentId,
                                                                      @Param("courseId") Long courseId);
    
    /**
     * 查询某学生所有知识点的最新得分记录
     */
    @Query("SELECT k FROM KnowledgePointScoreDetail k " +
           "WHERE k.student.id = :studentId " +
           "AND k.createdAt = (SELECT MAX(k2.createdAt) FROM KnowledgePointScoreDetail k2 " +
           "                  WHERE k2.student.id = k.student.id " +
           "                  AND k2.knowledgePoint.id = k.knowledgePoint.id)")
    List<KnowledgePointScoreDetail> findAllLatestByStudentId(@Param("studentId") Long studentId);
    
    /**
     * 查询某知识点在某班级的平均得分率
     */
    @Query("SELECT AVG(k.scoreRate) FROM KnowledgePointScoreDetail k " +
           "WHERE k.knowledgePoint.id = :kpId " +
           "AND k.student.classInfo.id = :classId")
    BigDecimal getClassAvgScoreRate(@Param("kpId") Long kpId, @Param("classId") Long classId);
    
    /**
     * 查询某学生某知识点的历史得分记录（按时间排序）
     */
     @Query("SELECT k FROM KnowledgePointScoreDetail k " +
           "WHERE k.student.id = :studentId " +
           "AND k.knowledgePoint.id = :kpId " +
           "ORDER BY k.createdAt ASC")
    List<KnowledgePointScoreDetail> findHistoryByStudentIdAndKpId(@Param("studentId") Long studentId,
                                                                   @Param("kpId") Long kpId);
    
    /**
     * 查询某学生某知识点的所有得分记录（用于详情）
     */
    @Query("SELECT k FROM KnowledgePointScoreDetail k " +
           "WHERE k.student.id = :studentId " +
           "AND k.knowledgePoint.id = :kpId " +
           "ORDER BY k.createdAt DESC")
    List<KnowledgePointScoreDetail> findAllByStudentIdAndKpId(@Param("studentId") Long studentId,
                                                               @Param("kpId") Long kpId);

       List<KnowledgePointScoreDetail> findBySourceTypeAndSourceIdAndKnowledgePointId(
        String sourceType, Long sourceId, Long knowledgePointId);

         @Query("SELECT k FROM KnowledgePointScoreDetail k " +
           "WHERE k.sourceType = :sourceType " +
           "AND k.sourceId = :sourceId " +
           "AND k.student.id = :studentId")
    List<KnowledgePointScoreDetail> findBySourceTypeAndSourceIdAndStudentId(
        @Param("sourceType") String sourceType,
        @Param("sourceId") Long sourceId, 
        @Param("studentId") Long studentId);

        @Query("SELECT AVG(k.scoreRate) FROM KnowledgePointScoreDetail k " +
           "WHERE k.student.id = :studentId " +
           "AND k.knowledgePoint.id = :kpId")
    BigDecimal getStudentAvgScoreRate(@Param("studentId") Long studentId, 
                                       @Param("kpId") Long kpId);

}
