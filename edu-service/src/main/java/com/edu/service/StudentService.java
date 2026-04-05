package com.edu.service;

import com.edu.domain.*;
import com.edu.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class StudentService {
    private final GradeRepository gradeRepository;
    private final ActivityRecordRepository activityRecordRepository;
    private final UserRepository userRepository;

    public Map<String, Object> getStudentStats(String username) {
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        
        List<Grade> grades = gradeRepository.findByStudent(student);
        List<ActivityRecord> activities = activityRecordRepository.findByUser(student);

        double avgScore = grades.stream().mapToDouble(Grade::getScore).average().orElse(0.0);
        int totalHours = activities.stream().mapToInt(ActivityRecord::getDuration).sum() / 60;
        int courseCount = (int) grades.stream().map(Grade::getSubject).distinct().count();
        
        // Mock rank for now
        int rank = 14; 

        Map<String, Object> result = new HashMap<>();
        result.put("avgScore", avgScore);
        result.put("totalHours", totalHours);
        result.put("courseCount", courseCount);
        result.put("rank", rank);
        return result;
    }

    public List<Grade> getGradeTrends(String username) {
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        return gradeRepository.findByStudent(student);
    }
}
