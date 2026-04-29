package com.edu.service;

import com.edu.domain.AiAnalysisReport;
import com.edu.repository.AiAnalysisReportRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisReportService {
    private final AiAnalysisReportRepository reportRepository;
    
    public List<AiAnalysisReport> findAll() {
        return reportRepository.findAll();
    }
    
    public Optional<AiAnalysisReport> findById(Long id) {
        return reportRepository.findById(id);
    }
    
    public List<AiAnalysisReport> findByTarget(String targetType, Long targetId) {
        return reportRepository.findByTargetTypeAndTargetId(targetType, targetId);
    }
    
    public List<AiAnalysisReport> findByTargetAndType(String targetType, Long targetId, String reportType) {
        return reportRepository.findByTargetTypeAndTargetIdAndReportType(targetType, targetId, reportType);
    } 
    
    /**
     * 查找最新的报告（按创建时间倒序）
     */
    public Optional<AiAnalysisReport> findLatestReport(String targetType, Long targetId, String reportType) {
        return reportRepository.findFirstByTargetTypeAndTargetIdAndReportTypeOrderByCreatedAtDesc(
            targetType, targetId, reportType);
    }
     /**
     * 查找指定哈希的报告（用于判断是否相同数据）
     */
    public Optional<AiAnalysisReport> findByTargetAndHash(String targetType, Long targetId, String reportType, String dataHash) {
        return reportRepository.findByTargetTypeAndTargetIdAndReportTypeAndDataHash(
            targetType, targetId, reportType, dataHash);
    }
    
    public List<AiAnalysisReport> findRecentReports(String targetType, Long targetId, LocalDateTime since) {
        return reportRepository.findByTargetTypeAndTargetIdAndCreatedAtAfter(targetType, targetId, since);
    }

    @Transactional
    public AiAnalysisReport save(AiAnalysisReport report) {
        if (report.getCreatedAt() == null) {
            report.setCreatedAt(LocalDateTime.now());
        }
        report.setUpdatedAt(LocalDateTime.now());
        return reportRepository.save(report);
    }
    
    @Transactional
    public AiAnalysisReport update(AiAnalysisReport report) {
        report.setUpdatedAt(LocalDateTime.now());
        return reportRepository.save(report);
    }
    
    @Transactional
    public void deleteById(Long id) {
        reportRepository.deleteById(id);
    }

    /**
     * 根据目标类型和ID删除所有报告
     */
    @Transactional
    public void deleteByTarget(String targetType, Long targetId) {
        reportRepository.deleteByTargetTypeAndTargetId(targetType, targetId);
    }
    
    /**
     * 删除指定报告类型的旧报告
     */
    @Transactional
    public void deleteOldReports(String targetType, Long targetId, String reportType) {
        reportRepository.deleteByTargetTypeAndTargetIdAndReportType(targetType, targetId, reportType);
    }


}
