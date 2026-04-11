package com.edu.repository;

import com.edu.domain.Teacher;
import com.edu.domain.Course;
import com.edu.domain.CourseStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findById(Long id);
    List<Course> findByTeacher(Teacher teacher);
    Optional<Course> findByName(String name);

    List<Course> findAllByStatus(CourseStatus status);

    List<Course> findAllByStatusAndTeacher(CourseStatus status, Teacher teacher);

    boolean existsByName(String name);
}
