package com.edu.repository;

import com.edu.domain.Course;
import com.edu.domain.Enrollment;
import com.edu.domain.Student;
import com.edu.domain.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudent(Student student);
    List<Enrollment> findByStudentAndSemester(Student student, Semester semester);

    List<Enrollment> findByStudentAndCourse(Student student, Course course);
}
