package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.edu.domain.ActivityAlert;
import com.edu.domain.ClassInfo;
import com.edu.domain.ClassWrongQuestionStats;
import com.edu.domain.Course;
import com.edu.domain.ScorePrediction;
import com.edu.domain.Student;
import com.edu.repository.ActivityAlertRepository;
import com.edu.repository.ClassWrongQuestionStatsRepository;
import com.edu.repository.ScorePredictionRepository;

import io.jsonwebtoken.lang.Classes;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClassWrongQuestionStatsService {
   private final ClassWrongQuestionStatsRepository statsRepository;
    
    public List<ClassWrongQuestionStats> findAll() {
        return statsRepository.findAll();
    }
    
    public ClassWrongQuestionStats findById(Long id) {
        return statsRepository.findById(id).orElse(null);
    }
    
    public List<ClassWrongQuestionStats> findByClass(ClassInfo classInfo) {
        return statsRepository.findByClassInfoOrderByStatDateDesc(classInfo);
    }
    
    public List<ClassWrongQuestionStats> findTopWrongQuestions(ClassInfo classInfo, LocalDate date) {
        return statsRepository.findByClassInfoAndStatDateOrderByRankInClassAsc(classInfo, date);
    }
    
    public List<ClassWrongQuestionStats> findHighErrorRateQuestions(ClassInfo classInfo, LocalDate date) {
        return statsRepository.findByClassInfoAndStatDateOrderByErrorRateDesc(classInfo, date);
    }
    
    public ClassWrongQuestionStats save(ClassWrongQuestionStats stats) {
        return statsRepository.save(stats);
    }
    
    public ClassWrongQuestionStats update(ClassWrongQuestionStats stats) {
        return statsRepository.save(stats);
    }
    
    public void deleteById(Long id) {
        statsRepository.deleteById(id);
    }
}
