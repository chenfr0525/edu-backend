package com.edu.repository;

import com.edu.domain.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SemesterRepository extends JpaRepository<Semester, Long> {
    Optional<Semester> findByCode(String code);
    Optional<Semester> findByIsCurrentTrue();
}
