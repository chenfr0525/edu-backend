package com.edu.repository;

import com.edu.domain.ClassInfo;
import com.edu.domain.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    List<Exam> findByClassInfo(ClassInfo classInfo);
}
