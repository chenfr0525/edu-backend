package com.edu.repository;

import com.edu.domain.ClassInfo;
import com.edu.domain.ClassWrongQuestionStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
