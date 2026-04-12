package com.edu.service;

import com.edu.domain.AiAnalysisReport;
import com.edu.repository.AiAnalysisReportRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisReportService {
   private final AiAnalysisReportRepository reportRepository;
    
    public List<AiAnalysisReport> findAll() {
        return reportRepository.findAll();
    }
    
    public AiAnalysisReport findById(Long id) {
        return reportRepository.findById(id).orElse(null);
    }
    
    public List<AiAnalysisReport> findByTarget(String targetType, Long targetId) {
        return reportRepository.findByTargetTypeAndTargetId(targetType, targetId);
    }
    
    public AiAnalysisReport findLatestReport(String targetType, Long targetId, String reportType) {
        return reportRepository.findFirstByTargetTypeAndTargetIdAndReportTypeOrderByCreatedAtDesc(
            targetType, targetId, reportType);
    }
    
    public List<AiAnalysisReport> findRecentReports(String targetType, Long targetId, LocalDateTime since) {
        return reportRepository.findByTargetTypeAndTargetIdAndCreatedAtAfter(targetType, targetId, since);
    }
    
    public AiAnalysisReport save(AiAnalysisReport report) {
        return reportRepository.save(report);
    }
    
    public AiAnalysisReport update(AiAnalysisReport report) {
        return reportRepository.save(report);
    }
    
    public void deleteById(Long id) {
        reportRepository.deleteById(id);
    }
}
