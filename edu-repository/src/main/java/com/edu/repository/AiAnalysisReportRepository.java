package com.edu.repository;

import com.edu.domain.AiAnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
@Repository
public interface AiAnalysisReportRepository extends JpaRepository<AiAnalysisReport, Long> {
  // 查询某目标的所有报告
    List<AiAnalysisReport> findByTargetTypeAndTargetId(String targetType, Long targetId);
    
    // 查询某目标指定类型的所有报告
    List<AiAnalysisReport> findByTargetTypeAndTargetIdAndReportType(String targetType, Long targetId, String reportType);
    
    // 查询某学生最新的综合报告
    Optional<AiAnalysisReport> findFirstByTargetTypeAndTargetIdAndReportTypeOrderByCreatedAtDesc(
        String targetType, Long targetId, String reportType);
    
    // 查询某班级最近一周的报告
    List<AiAnalysisReport> findByTargetTypeAndTargetIdAndCreatedAtAfter(
        String targetType, Long targetId, LocalDateTime date);
    
    // 根据哈希值查找报告（用于判断数据是否变化）
    Optional<AiAnalysisReport> findByTargetTypeAndTargetIdAndReportTypeAndDataHash(
        String targetType, Long targetId, String reportType, String dataHash);
    
    // 删除指定目标的所有报告
    void deleteByTargetTypeAndTargetId(String targetType, Long targetId);
    
    // 删除指定目标的指定类型报告
    void deleteByTargetTypeAndTargetIdAndReportType(String targetType, Long targetId, String reportType);
    
    // 查询需要生成报告的学生
    @Query("SELECT DISTINCT a.targetId FROM AiAnalysisReport a " +
           "WHERE a.targetType = 'STUDENT' AND a.createdAt < :date")
    List<Long> findStudentsNeedNewReport(@Param("date") LocalDateTime date);
}
