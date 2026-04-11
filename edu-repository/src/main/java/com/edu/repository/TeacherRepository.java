package com.edu.repository;

import com.edu.domain.Teacher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {
  
   // 根据userid查询
   Optional<Teacher> findByUserId(Long userId);
  
  Optional<Teacher> findByUserName(String name);
  
  Page<Teacher> findByUserNameContaining(String name, Pageable pageable);

  boolean existsByTeacherNo(String teacherNo);

   // 查询最大的教师编号
    @Query("SELECT MAX(t.teacherNo) FROM Teacher t")
    String findMaxTeacherNo();
}
