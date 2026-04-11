package com.edu.repository;

import com.edu.domain.Semester;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SemesterRepository extends JpaRepository<Semester, Long> {
    Optional<Semester> findById(Long id);

    List<Semester> findAll();
    Optional<Semester> findByIsCurrentTrue();
}
