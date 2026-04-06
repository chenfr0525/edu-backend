package com.edu.repository;

import com.edu.domain.Submission;
import com.edu.domain.User;
import com.edu.domain.Homework;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> findByStudent(User student);
    Optional<Submission> findByStudentAndHomework(User student, Homework homework);
}
