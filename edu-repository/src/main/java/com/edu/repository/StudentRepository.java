package com.edu.repository;

import com.edu.domain.ClassInfo;
import com.edu.domain.Course;
import com.edu.domain.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    
    // 根据userid查询
    Optional<Student> findByUserId(Long userId);

      // 根据年级查询
    List<Student> findByGrade(String grade);

    @Query("SELECT s FROM Student s JOIN Enrollment e ON s.id = e.student.id WHERE e.course.id = :courseId")
    List<Student> findStudentsByCourseId(@Param("courseId") Long courseId);

     List<Student> findByClassInfo(ClassInfo classInfo);
    //根据id查询
     Optional<Student> findById(Long id);

      // 根据学号查询
    Optional<Student> findByStudentNo(String studentNo);

    // 查询班级学生数量
    long countByClassInfo(ClassInfo classInfo);
    
   
     Page<Student> findByUserNameContaining(String userName, Pageable pageable);

    Page<Student> findAll(Pageable pageable);
    
    // 根据班级查询
    Page<Student> findByClassName(String className, Pageable pageable);
    
    // 检查学号是否存在
    boolean existsByStudentNo(String studentNo);

    @Query("SELECT MAX(s.studentNo) FROM Student s")
    String findMaxStudentNo();

    // 查询低活跃度学生（自定义SQL）
    @Query("SELECT s FROM Student s WHERE s.id IN " +
           "(SELECT a.student.id FROM ActivityRecord a GROUP BY a.student.id HAVING COUNT(a) < :threshold)")
    List<Student> findLowActivityStudents(@Param("threshold") int threshold);

    /**
     * 获取选修某课程的所有学生
     */
    @Query("SELECT DISTINCT e.student FROM Enrollment e WHERE e.course = :course")
    List<Student> findByEnrollmentsCourse(@Param("course") Course course);
    
    /**
     * 统计选修某课程的学生数量
     */
    @Query("SELECT COUNT(DISTINCT e.student) FROM Enrollment e WHERE e.course = :course")
    long countByEnrollmentsCourse(@Param("course") Course course);
}