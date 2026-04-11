package com.edu.service;

import com.edu.domain.ClassInfo;
import com.edu.domain.Student;
import com.edu.repository.ClassRepository;
import com.edu.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentService {

    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;

    /**
     * 分页查询所有学生
     */
    @Transactional(readOnly = true)
    public Page<Student> getAllStudents(Pageable pageable) {
        return studentRepository.findAll(pageable);
    }

    /**
     * 根据ID查询学生
     */
    @Transactional(readOnly = true)
    public Student getStudentById(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("学生不存在，ID: " + id));
    }

    /**
     * 模糊搜索学生
     */
    @Transactional(readOnly = true)
    public Page<Student> searchStudents(String keyword, Pageable pageable) {
        if (keyword != null && !keyword.isEmpty()) {
            return studentRepository.findByUserNameContaining(keyword, pageable);
        }
        return studentRepository.findAll(pageable);
    }

     /**
     * 设置学生班级
     */
    @Transactional
    public void setStudentClass(Long id, String className) {
        // 查询或创建班级
        ClassInfo classInfo = classRepository.findByName(className)
            .orElseGet(() -> {
                ClassInfo newClass = new ClassInfo();
                newClass.setName(className);
                newClass.setGrade("大一");
                return classRepository.save(newClass);
            });

        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("学生不存在，ID: " + id));
        student.setClassInfo(classInfo);
        studentRepository.save(student);
    }
}