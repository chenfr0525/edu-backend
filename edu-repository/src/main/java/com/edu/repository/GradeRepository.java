package com.edu.repository;

import com.edu.domain.Grade;
import com.edu.domain.User;
import com.edu.domain.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    List<Grade> findByStudent(User student);
    List<Grade> findByExam(Exam exam);
}
