package com.edu.repository;

import com.edu.domain.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    
    // 根据userid查询
    Optional<Student> findByUserId(Long userId);

    //根据id查询
     Optional<Student> findById(Long id);

    // 根据学号查询
    Optional<Student> findByStudentNo(String studentNo);
    
    // 根据姓名模糊查询（分页）
    Page<Student> findByUserNameContaining(String name, Pageable pageable);
    Page<Student> findAll(Pageable pageable);
    
    // 根据班级查询
    Page<Student> findByClassName(String className, Pageable pageable);
    
    // 检查学号是否存在
    boolean existsByStudentNo(String studentNo);

    @Query("SELECT MAX(s.studentNo) FROM Student s")
    String findMaxStudentNo();
}