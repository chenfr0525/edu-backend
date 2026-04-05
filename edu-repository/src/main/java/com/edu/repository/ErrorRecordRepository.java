package com.edu.repository;

import com.edu.domain.ErrorRecord;
import com.edu.domain.User;
import com.edu.domain.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ErrorRecordRepository extends JpaRepository<ErrorRecord, Long> {
    List<ErrorRecord> findByStudent(User student);
    List<ErrorRecord> findByExam(Exam exam);
}
