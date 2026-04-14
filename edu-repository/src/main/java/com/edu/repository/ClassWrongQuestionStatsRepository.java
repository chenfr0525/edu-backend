package com.edu.repository;

import com.edu.domain.ClassInfo;
import com.edu.domain.ClassWrongQuestionStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ClassWrongQuestionStatsRepository extends JpaRepository<ClassWrongQuestionStats, Long> {
    // 查询某班级最近统计
    List<ClassWrongQuestionStats> findByClassInfoOrderByStatDateDesc(ClassInfo classInfo);
    
    // 查询某班级高频错题排行
    List<ClassWrongQuestionStats> findByClassInfoAndStatDateOrderByRankInClassAsc(ClassInfo classInfo, LocalDate date);
    
    // 查询某班级错误率最高的知识点
    List<ClassWrongQuestionStats> findByClassInfoAndStatDateOrderByErrorRateDesc(ClassInfo classInfo, LocalDate statDate);

/**
 * 查询某班级某课程的高频错题
 */
@Query("SELECT cwq FROM ClassWrongQuestionStats cwq " +
       "WHERE cwq.classInfo.id = :classId " +
       "AND cwq.knowledgePoint.course.id = :courseId " +
       "ORDER BY cwq.errorRate DESC")
List<ClassWrongQuestionStats> findByClassIdAndCourseIdOrderByErrorRateDesc(
    @Param("classId") Long classId, @Param("courseId") Long courseId);

/**
 * 查询某课程下所有班级的高频错题
 */
@Query("SELECT cwq FROM ClassWrongQuestionStats cwq " +
       "WHERE cwq.knowledgePoint.course.id = :courseId " +
       "ORDER BY cwq.errorRate DESC")
List<ClassWrongQuestionStats> findByCourseIdOrderByErrorRateDesc(@Param("courseId") Long courseId);
}
