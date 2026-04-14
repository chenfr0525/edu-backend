package com.edu.repository;

import com.edu.domain.ClassInfo;
import com.edu.domain.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface ClassRepository extends JpaRepository<ClassInfo, Long> {
  List<ClassInfo> findByTeacher(Teacher teacher);

  Optional<ClassInfo> findByName(String name);
  Optional<ClassInfo> findById(String id);

  List<ClassInfo> findAllByGrade(String grade);
  
  boolean existsByName(String name);
}
