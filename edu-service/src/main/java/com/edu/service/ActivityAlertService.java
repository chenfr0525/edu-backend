package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.stereotype.Service;

import com.edu.domain.ActivityAlert;
import com.edu.domain.ClassInfo;
import com.edu.domain.Student;
import com.edu.repository.ActivityAlertRepository;


@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityAlertService {
   private final  ActivityAlertRepository alertRepository;
    
    public List<ActivityAlert> findAll() {
        return alertRepository.findAll();
    }
    
    public ActivityAlert findById(Long id) {
        return alertRepository.findById(id).orElse(null);
    }
    
    public List<ActivityAlert> findUnresolved() {
        return alertRepository.findByIsResolvedFalse();
    }
    
    public List<ActivityAlert> findByStudent(Student student) {
        return alertRepository.findByStudent(student);
    }
    
    public List<ActivityAlert> findUnresolvedByClass(ClassInfo classInfo) {
        return alertRepository.findByClassInfoAndIsResolvedFalse(classInfo);
    }
    
    public List<ActivityAlert> findCriticalAlerts() {
        return alertRepository.findByAlertLevelAndIsResolvedFalse("CRITICAL");
    }
    
    public ActivityAlert save(ActivityAlert alert) {
        return alertRepository.save(alert);
    }
    
    public ActivityAlert update(ActivityAlert alert) {
        return alertRepository.save(alert);
    }
    
    public void resolveAlert(Long id) {
        ActivityAlert alert = findById(id);
        if (alert != null) {
            alert.setIsResolved(true);
            alert.setResolvedAt(java.time.LocalDateTime.now());
            alertRepository.save(alert);
        }
    }
    
    public void deleteById(Long id) {
        alertRepository.deleteById(id);
    }
}
