package com.edu.repository;

import com.edu.domain.Course;
import com.edu.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findByCode(String code);
    List<Course> findByTeacher(User teacher);
}
