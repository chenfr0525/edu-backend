package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.edu.domain.Course;
import com.edu.domain.Enrollment;
import com.edu.domain.Semester;
import com.edu.domain.Student;
import com.edu.repository.EnrollmentRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnrollmentService {
   private final EnrollmentRepository enrollmentRepository;
    
    public List<Enrollment> findAll() {
        return enrollmentRepository.findAll();
    }
    
    public Optional<Enrollment> findById(Long id) {
        return enrollmentRepository.findById(id);
    }
    
    public List<Enrollment> findByStudent(Student student) {
        return enrollmentRepository.findByStudent(student);
    }
    
    public List<Enrollment> findByCourse(Course course) {
        return enrollmentRepository.findByCourse(course);
    }
    
    public List<Enrollment> findByStudentAndCourse(Student student, Course course) {
        return enrollmentRepository.findByStudentAndCourse(student, course);
    }
    
    public List<Enrollment> findBySemester(Semester semester) {
        return enrollmentRepository.findBySemester(semester);
    }
    
    public Enrollment save(Enrollment enrollment) {
        return enrollmentRepository.save(enrollment);
    }
    
    public Enrollment update(Enrollment enrollment) {
        return enrollmentRepository.save(enrollment);
    }
    
    public void deleteById(Long id) {
        enrollmentRepository.deleteById(id);
    }
    
    public Double getStudentAverageScore(Long studentId) {
        return enrollmentRepository.getAverageScoreByStudent(studentId);
    }
    
    // public long countStudentsByCourse(Course course) {
    //     return enrollmentRepository.countByCourse(Course);
    // }
}
