package com.edu.service;

import com.edu.domain.ClassInfo;
import com.edu.domain.Course;
import com.edu.domain.Role;
import com.edu.domain.Student;
import com.edu.domain.User;
import com.edu.domain.UserStatus;
import com.edu.domain.dto.StudentDTO;
import com.edu.repository.ClassRepository;
import com.edu.repository.StudentRepository;
import com.edu.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

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
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // 查询所有学生
    public List<Student> findAll() {
        return studentRepository.findAll();
    }

     // 根据ID查询
    public Optional<Student> findById(Long id) {
        return studentRepository.findById(id);
    }

    public List<Student> findByClassInfo(ClassInfo classInfo){
        return studentRepository.findByClassInfo(classInfo);
    }

     // 根据学号查询
    public Optional<Student> findByStudentNo(String studentNo) {
        return studentRepository.findByStudentNo(studentNo);
    }

    // 根据用户ID查询
    public Optional<Student> findByUserId(Long userId) {
        return studentRepository.findByUserId(userId);
    }

    public List<Student> findByCourseId(Long courseId) {
        return studentRepository.findStudentsByCourseId(courseId);
    }
    
     // 查询班级学生数量
    public long countByClassInfo(ClassInfo classInfo) {
        return studentRepository.countByClassInfo(classInfo);
    }

      // 查询低活跃度学生
    public List<Student> findLowActivityStudents(int threshold) {
        return studentRepository.findLowActivityStudents(threshold);
    }

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
     * 设置学生班级（注册时候用)
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

     /**
     * 创建学生
     */
    public Student createStudent(StudentDTO studnetDto){
         log.info("创建学生: {}", studnetDto.getName());

          if (studentRepository.existsByStudentNo(studnetDto.getStudentNo())) {
            throw new RuntimeException("学号已存在: " + studnetDto.getStudentNo());
        }

         User user = new User();
        user.setUsername(studnetDto.getUsername());
        user.setPassword(passwordEncoder.encode("123456"));
        user.setName(studnetDto.getName());
        user.setRole(Role.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        user.setGender(studnetDto.getGender());
        user.setEmail(studnetDto.getEmail());
        user.setPhone(studnetDto.getPhone());
        User savedUser= userRepository.save(user);

        Student student = new Student();
        student.setUser(savedUser);
        student.setStudentNo(studnetDto.getStudentNo());
        student.setClassInfo(classRepository.findByName(studnetDto.getClassName()).orElseThrow(() -> new RuntimeException("班级不存在，ID: " + studnetDto.getClassName())));
        student.setGrade(studnetDto.getGrade());
        return studentRepository.save(student);
    }

    /**
     * 更新学生
     */
    @Transactional
    public Student updateStudent(Long id, StudentDTO dto) {
        log.info("更新学生, ID: {}", id);
        
        Student student = getStudentById(id);
        User user = student.getUser();
        
        // 如果修改了学号，检查新学号是否被其他学生使用
        if (!student.getStudentNo().equals(dto.getStudentNo()) 
                && studentRepository.existsByStudentNo(dto.getStudentNo())) {
            throw new RuntimeException("学号已存在: " + dto.getStudentNo());
        }
         user.setUsername(dto.getUsername());
        user.setName(dto.getName());
        user.setRole(Role.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        user.setGender(dto.getGender());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        User savedUser= userRepository.save(user);
        student.setUser(savedUser);
        student.setStudentNo(dto.getStudentNo());
        student.setClassInfo(classRepository.findByName(dto.getClassName()).orElseThrow(() -> new RuntimeException("班级不存在，ID: " + dto.getClassName())));
        student.setGrade(dto.getGrade());
        
        return studentRepository.save(student);
    }

      /**
     * 删除学生
     */
    @Transactional
    public void deleteStudent(Long id) {
        log.info("删除学生, ID: {}", id);
        Student student = getStudentById(id);
        User user=student.getUser();
       // 先解除关联
    student.setUser(null);
    studentRepository.save(student);
    
    // 先删除 Student，再删除 User
    studentRepository.delete(student);
    if (user != null) {
        userRepository.delete(user);
    }
    }

     /**
     * 密码重置
     */
    @Transactional
    public void resetPassword(Long id) {
        log.info("重置学生密码, ID: {}", id);
        Student student = getStudentById(id);
        User user = student.getUser();
        user.setPassword(passwordEncoder.encode("123456"));
        userRepository.save(user);
    }

    /**
     * 获取选修某课程的所有学生
     */
    public List<Student> findByCourse(Course course) {
        return studentRepository.findByEnrollmentsCourse(course);
    }
    
    /**
     * 获取某课程的学生总数
     */
    public long countByCourse(Course course) {
        return studentRepository.countByEnrollmentsCourse(course);
    }
}