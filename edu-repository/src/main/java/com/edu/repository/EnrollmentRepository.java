package com.edu.repository;

import com.edu.domain.Enrollment;
import com.edu.domain.User;
import com.edu.domain.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudent(User student);
    List<Enrollment> findByStudentAndSemester(User student, Semester semester);
}
