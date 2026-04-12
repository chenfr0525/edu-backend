package com.edu.repository;

import com.edu.domain.Course;
import com.edu.domain.Enrollment;
import com.edu.domain.Student;
import com.edu.domain.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudent(Student student);
    List<Enrollment> findByStudentAndSemester(Student student, Semester semester);

    List<Enrollment> findByStudentAndCourse(Student student, Course course);
        // 查询某课程的所有学生
    List<Enrollment> findByCourse(Course course);
    
    // 查询某学期的选课
    List<Enrollment> findBySemester(Semester semester);
    
    // 查询学生的课程平均分
    @Query("SELECT AVG(e.score) FROM Enrollment e WHERE e.student.id = :studentId AND e.score IS NOT NULL")
    Double getAverageScoreByStudent(@Param("studentId") Long studentId);
    
    // 查询课程的学生数量
    long countByCourse(Course course);
}
