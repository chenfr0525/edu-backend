package com.edu.repository;

import com.edu.domain.ErrorRecord;
import com.edu.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ErrorRecordRepository extends JpaRepository<ErrorRecord, Long> {
    List<ErrorRecord> findByStudent(Student student);
}
