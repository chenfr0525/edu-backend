package com.edu.repository;

import com.edu.domain.ClassInfo;
import com.edu.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClassRepository extends JpaRepository<ClassInfo, Long> {
    List<ClassInfo> findByTeacher(User teacher);
}
