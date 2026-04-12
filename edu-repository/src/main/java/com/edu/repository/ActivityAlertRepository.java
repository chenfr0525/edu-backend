package com.edu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.edu.domain.ActivityAlert;
import com.edu.domain.ClassInfo;
import com.edu.domain.Semester;
import com.edu.domain.Student;

import java.util.List;

@Repository
public interface ActivityAlertRepository extends JpaRepository<ActivityAlert, Long> {
     // 查询未解决的预警
    List<ActivityAlert> findByIsResolvedFalse();

    List<ActivityAlert> findByClassInfoAndIsResolvedFalse(ClassInfo classInfo);
    
    // 查询某学生的所有预警
    List<ActivityAlert> findByStudent(Student student);
    
    // 查询严重预警
    List<ActivityAlert> findByAlertLevelAndIsResolvedFalse(String alertLevel);
    
    // 查询某学期预警数量
    long countBySemesterAndIsResolvedFalse(Semester semester);
}
