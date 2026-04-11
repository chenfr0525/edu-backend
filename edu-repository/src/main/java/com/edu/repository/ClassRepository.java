package com.edu.repository;

import com.edu.domain.ClassInfo;
import com.edu.domain.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ClassRepository extends JpaRepository<ClassInfo, Long> {
  List<ClassInfo> findByTeacher(Teacher teacher);

  Optional<ClassInfo> findByName(String name);

  List<ClassInfo> findAllByGrade(String grade);
  
  boolean existsByName(String name);
}
