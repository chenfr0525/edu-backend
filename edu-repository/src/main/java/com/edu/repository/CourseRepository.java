package com.edu.repository;

import com.edu.domain.Teacher;
import com.edu.domain.Course;
import com.edu.domain.CourseStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findById(Long id);
    List<Course> findByTeacher(Teacher teacher);
    Optional<Course> findByName(String name);

    List<Course> findAllByStatus(CourseStatus status);

    List<Course> findAllByStatusAndTeacher(CourseStatus status, Teacher teacher);
    
    // 根据状态查询
    List<Course> findByStatus(String status);
    
    // 模糊搜索课程
    List<Course> findByNameContaining(String keyword);
    boolean existsByName(String name);

@Query("SELECT c FROM Course c WHERE c.id IN (SELECT e.course.id FROM Enrollment e WHERE e.student.id = :studentId)")
List<Course> findByStudentId(@Param("studentId") Long studentId);
}
