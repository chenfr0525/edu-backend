package com.edu.service;

import com.edu.domain.*;
import com.edu.repository.ActivityRecordRepository;
import com.edu.repository.ExamGradeRepository;
import com.edu.repository.StudentRepository;
import com.edu.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动记录同步服务
 * 用于在作业提交、考试成绩导入时自动创建 activity_record 记录
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivitySyncService {

    private final ActivityRecordRepository activityRecordRepository;
    private final StudentRepository studentRepository;
    private final SubmissionRepository submissionRepository;
    private final ExamGradeRepository examGradeRepository;

    /**
     * 同步作业提交活动
     * @param student 学生
     * @param homework 作业
     * @param score 得分
     */
    @Transactional
    public void syncHomeworkActivity(Student student, Homework homework, Double score) {
        if (student == null || homework == null) return;
        
        try {
            // 检查今天是否已有该作业的提交记录
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.atTime(23, 59, 59);
            
            List<ActivityRecord> todayRecords = activityRecordRepository
                .findByStudentAndActivityDateBetween(student, startOfDay, endOfDay);
            
            boolean hasRecord = todayRecords.stream()
                .anyMatch(r -> r.getType() == ActivityStatus.HOMEWORK && 
                              r.getDescription() != null && 
                              r.getDescription().contains("作业ID:" + homework.getId()));
            
            if (!hasRecord) {
                ActivityRecord record = new ActivityRecord();
                record.setStudent(student);
                record.setType(ActivityStatus.HOMEWORK);
                record.setDescription(String.format("提交作业: %s (得分: %.1f)", homework.getName(), score));
                record.setActivityDate(LocalDateTime.now());
                 record.setStudyDuration(0);
                record.setResourceAccessCount(0);
                record.setInteractionCount(0);
                
                // 根据得分计算活跃度分数
                int activityScore = calculateHomeworkActivityScore(score);
                record.setActivityScore(BigDecimal.valueOf(activityScore));
                
                activityRecordRepository.save(record);
                log.info("记录作业提交活动: 学生ID={}, 作业={}", student.getId(), homework.getName());
            }
        } catch (Exception e) {
            log.error("同步作业活动失败", e);
        }
    }

    /**
     * 同步考试参与活动
     * @param student 学生
     * @param exam 考试
     * @param score 得分
     */
    @Transactional
    public void syncExamActivity(Student student, Exam exam, Integer score) {
        if (student == null || exam == null) return;
        
        try {
            // 检查今天是否已有该考试的参与记录
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.atTime(23, 59, 59);
            
            List<ActivityRecord> todayRecords = activityRecordRepository
                .findByStudentAndActivityDateBetween(student, startOfDay, endOfDay);
            
            boolean hasRecord = todayRecords.stream()
                .anyMatch(r -> r.getType() == ActivityStatus.EXAM && 
                              r.getDescription() != null && 
                              r.getDescription().contains("考试ID:" + exam.getId()));
            
            if (!hasRecord) {
                ActivityRecord record = new ActivityRecord();
                record.setStudent(student);
                record.setType(ActivityStatus.EXAM);
                record.setDescription(String.format("参加考试: %s (得分: %d)", exam.getName(), score));
                record.setActivityDate(LocalDateTime.now());
                 record.setStudyDuration(0);
                record.setResourceAccessCount(0);
                record.setInteractionCount(0);
                
                // 根据得分计算活跃度分数
                int activityScore = calculateExamActivityScore(score, exam.getFullScore());
                record.setActivityScore(BigDecimal.valueOf(activityScore));
                
                activityRecordRepository.save(record);
                log.info("记录考试参与活动: 学生ID={}, 考试={}", student.getId(), exam.getName());
            }
        } catch (Exception e) {
            log.error("同步考试活动失败", e);
        }
    }

    /**
     * 同步资源访问活动
     * @param student 学生
     * @param resourceName 资源名称
     * @param count 访问次数
     */
    @Transactional
    public void syncResourceActivity(Student student, String resourceName, int count) {
        if (student == null) return;
        
        try {
            ActivityRecord record = new ActivityRecord();
            record.setStudent(student);
            record.setType(ActivityStatus.RESOURCE);
            record.setDescription(String.format("访问资源: %s (共%d次)", resourceName, count));
            record.setActivityDate(LocalDateTime.now());
            record.setResourceAccessCount(count);
             record.setStudyDuration(0);
            record.setInteractionCount(0);
            
            // 资源访问得分：每次2分，最高20分
            int activityScore = Math.min(count * 2, 20);
            record.setActivityScore(BigDecimal.valueOf(activityScore));
            
            activityRecordRepository.save(record);
            log.info("记录资源访问活动: 学生ID={}, 资源={}", student.getId(), resourceName);
        } catch (Exception e) {
            log.error("同步资源活动失败", e);
        }
    }

    /**
     * 同步视频观看活动
     * @param student 学生
     * @param videoName 视频名称
     * @param duration 观看时长（分钟）
     */
    @Transactional
    public void syncVideoActivity(Student student, String videoName, int duration) {
        if (student == null) return;
        
        try {
            ActivityRecord record = new ActivityRecord();
            record.setStudent(student);
            record.setType(ActivityStatus.VIDEO);
            record.setDescription(String.format("观看视频: %s (时长: %d分钟)", videoName, duration));
            record.setActivityDate(LocalDateTime.now());
            record.setStudyDuration(duration);
            record.setResourceAccessCount(0);
            record.setInteractionCount(0);
            
            // 视频观看得分：每分钟1分，最高30分
            int activityScore = Math.min(duration, 30);
            record.setActivityScore(BigDecimal.valueOf(activityScore));
            
            activityRecordRepository.save(record);
            log.info("记录视频观看活动: 学生ID={}, 视频={}", student.getId(), videoName);
        } catch (Exception e) {
            log.error("同步视频活动失败", e);
        }
    }

    private int calculateHomeworkActivityScore(Double score) {
        if (score == null) return 10;
        // 作业得分率越高，活动分越高（最高20分）
        return (int) Math.min(score * 0.2, 20);
    }

    private int calculateExamActivityScore(Integer score, Integer fullScore) {
        if (score == null) return 10;
        if (fullScore == null || fullScore == 0) {
            return Math.min(score / 5, 20);
        }
        // 考试得分率越高，活动分越高（最高20分）
        double rate = score.doubleValue() / fullScore;
        return (int) Math.min(rate * 20, 20);
    }
}