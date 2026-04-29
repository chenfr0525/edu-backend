package com.edu.service;

import com.edu.domain.*;
import com.edu.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TeacherService {
    private final TeacherRepository teacherRepository;

    public List<Teacher> findAll() {
        return teacherRepository.findAll();
    }
    
    public Optional<Teacher> findById(Long id) {
        return teacherRepository.findById(id);
    }
    
    public Optional<Teacher> findByTeacherNo(String teacherNo) {
        return teacherRepository.findByTeacherNo(teacherNo);
    }
    
    public Optional<Teacher> findByUser(User user) {
        return teacherRepository.findByUser(user);
    }
    
    public Teacher save(Teacher teacher) {
        return teacherRepository.save(teacher);
    }
    
    public Teacher update(Teacher teacher) {
        return teacherRepository.save(teacher);
    }
    
    public void deleteById(Long id) {
        teacherRepository.deleteById(id);
    }
}
