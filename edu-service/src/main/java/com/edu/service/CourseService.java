package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.edu.domain.Course;
import com.edu.domain.Teacher;
import com.edu.repository.CourseRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseService {
   private final CourseRepository courseRepository;
    
    public List<Course> findAll() {
        return courseRepository.findAll();
    }
    
    public Optional<Course> findById(Long id) {
        return courseRepository.findById(id);
    }
    
    public List<Course> findByTeacher(Teacher teacher) {
        return courseRepository.findByTeacher(teacher);
    }
    
    public List<Course> findByStatus(String status) {
        return courseRepository.findByStatus(status);
    }
    
    public List<Course> searchByName(String keyword) {
        return courseRepository.findByNameContaining(keyword);
    }
    
    public Course save(Course course) {
        return courseRepository.save(course);
    }
    
    public Course update(Course course) {
        return courseRepository.save(course);
    }

    public List<Course> findByStudentId(Long studentId) {
    return courseRepository.findByStudentId(studentId);
}
    
    public void deleteById(Long id) {
        courseRepository.deleteById(id);
    }
}
