package com.edu.repository;

import com.edu.domain.Homework;
import com.edu.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HomeworkRepository extends JpaRepository<Homework, Long> {
    List<Homework> findByCourseIn(List<Course> courses);
}
