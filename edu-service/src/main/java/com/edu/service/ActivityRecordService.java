package com.edu.service;

import com.edu.domain.ActivityRecord;
import com.edu.domain.Student;
import com.edu.repository.ActivityRecordRepository;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityRecordService {
   private final  ActivityRecordRepository activityRecordRepository;
    
    public List<ActivityRecord> findAll() {
        return activityRecordRepository.findAll();
    }
    
    public ActivityRecord findById(Long id) {
        return activityRecordRepository.findById(id).orElse(null);
    }
    
   public List<ActivityRecord> findByStudent(Student student) {
        return activityRecordRepository.findByStudent(student);
    }
    
    
    public List<ActivityRecord> findByStudentAndDateRange(Student student, LocalDate startDate, LocalDate endDate) {
         LocalDateTime start = startDate.atStartOfDay();
    LocalDateTime end = endDate.atTime(23, 59, 59);
        return activityRecordRepository.findByStudentAndActivityDateBetween(student, start, end);
    }
    
    public List<ActivityRecord> findByClassIdAndDate(Long classId, LocalDate date) {
        return activityRecordRepository.findByClassIdAndDate(classId, date);
    }
    
    public ActivityRecord save(ActivityRecord activityRecord) {
        return activityRecordRepository.save(activityRecord);
    }
    
    public ActivityRecord update(ActivityRecord activityRecord) {
        return activityRecordRepository.save(activityRecord);
    }
    
    public void deleteById(Long id) {
        activityRecordRepository.deleteById(id);
    }
    
    public Double getStudentTotalActivityScore(Long studentId) {
        return activityRecordRepository.getTotalActivityScore(studentId);
    }
    
    public List<Long> findLowActivityStudents(LocalDate startDate, Double threshold) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        BigDecimal thresholdDecimal = BigDecimal.valueOf(threshold);
        return activityRecordRepository.findLowActivityStudents(startDateTime, thresholdDecimal);
    }
    
    public Integer getStudentStudyDuration(Long studentId, LocalDate startDate) {
        return activityRecordRepository.getTotalStudyDuration(studentId, startDate);
    }
}
