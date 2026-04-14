package com.edu.service;

import com.edu.domain.ClassInfo;
import com.edu.domain.Course;
import com.edu.domain.Role;
import com.edu.domain.Student;
import com.edu.domain.User;
import com.edu.domain.UserStatus;
import com.edu.domain.dto.StudentDTO;
import com.edu.repository.ActivityAlertRepository;
import com.edu.repository.ActivityRecordRepository;
import com.edu.repository.ClassRepository;
import com.edu.repository.EnrollmentRepository;
import com.edu.repository.ErrorRecordRepository;
import com.edu.repository.ExamGradeRepository;
import com.edu.repository.KnowledgePointScoreDetailRepository;
import com.edu.repository.ScorePredictionRepository;
import com.edu.repository.StudentKnowledgeMasteryRepository;
import com.edu.repository.StudentRepository;
import com.edu.repository.SubmissionRepository;
import com.edu.repository.UserRepository;

import antlr.StringUtils;
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
    private final SubmissionRepository submissionRepository;
    private final ErrorRecordRepository errorRecordRepository;
    private final ActivityRecordRepository activityRecordRepository;
    private final ActivityAlertRepository   activityAlertRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ExamGradeRepository examGradeRepository;
    private final KnowledgePointScoreDetailRepository knowledgePointScoreDetailRepository;
    private final ScorePredictionRepository scorePredictionRepository;
    private final StudentKnowledgeMasteryRepository studentKnowledgeMasteryRepository;

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

     @Transactional
public Student createStudent(StudentDTO studentDto) {
    
    // 检查姓名是否为空
    if (studentDto.getName() == null || studentDto.getName().trim().isEmpty()) {
        throw new RuntimeException("姓名不能为空");
    }
    
    // 检查学号是否为空
    if (studentDto.getStudentNo() == null || studentDto.getStudentNo().trim().isEmpty()) {
        throw new RuntimeException("学号不能为空");
    }

    // 参数校验
    if (studentDto == null) {
        throw new RuntimeException("学生信息不能为空");
    }
    
    if (userRepository.existsByUsername(studentDto.getUsername())) {
        throw new RuntimeException("用户名已存在: " + studentDto.getUsername());
    }
    
    if (studentRepository.existsByStudentNo(studentDto.getStudentNo())) {
        throw new RuntimeException("学号已存在: " + studentDto.getStudentNo());
    }
    
    // 3. 校验并获取班级（处理类型转换）
    Long classId = null;
    if (studentDto.getClassId() != null) {
            classId = (Long) studentDto.getClassId();
    }
    
    if (classId == null) {
        throw new RuntimeException("班级ID不能为空");
    }
    
    ClassInfo classInfo = classRepository.findById(classId)
        .orElseThrow(() -> new RuntimeException("班级不存在 "));
    
    // 4. 创建用户
    User user = User.builder()
        .username(studentDto.getStudentNo())
        .password(passwordEncoder.encode("123456"))
        .name(studentDto.getName())
        .role(Role.STUDENT)
        .status(UserStatus.ACTIVE)
        .gender(studentDto.getGender())
        .email(studentDto.getEmail())
        .phone(studentDto.getPhone())
        .build();
    User savedUser = userRepository.save(user);
    
    // 5. 创建学生
    Student student = Student.builder()
        .user(savedUser)
        .studentNo(studentDto.getStudentNo())
        .classInfo(classInfo)
        .grade(studentDto.getGrade())
        .build();
    
    Student savedStudent = studentRepository.save(student);
    log.info("学生创建成功: id={}, name={}", savedStudent.getId(), savedStudent.getUser().getName());
    
    return savedStudent;
}
  /**
 * 更新学生
 */
@Transactional
public Student updateStudent(Long id, StudentDTO dto) {
    log.info("更新学生, ID: {}", id);
    
    Student student = getStudentById(id);
    User user = student.getUser();
    
    // 1. 更新用户名（如果提供且改变）
    if (dto.getUsername() != null && !dto.getUsername().trim().isEmpty()) {
        if (!user.getUsername().equals(dto.getUsername())) {
            // 检查新用户名是否已被其他用户使用
            if (userRepository.existsByUsername(dto.getUsername())) {
                throw new RuntimeException("用户名已存在: " + dto.getUsername());
            }
            user.setUsername(dto.getUsername());
        }
    }
    
    // 2. 更新姓名（如果提供）
    if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
        user.setName(dto.getName());
    }
    
    // 3. 更新学号（如果提供且改变）
    if (dto.getStudentNo() != null && !dto.getStudentNo().trim().isEmpty()) {
        if (!student.getStudentNo().equals(dto.getStudentNo())) {
            if (studentRepository.existsByStudentNo(dto.getStudentNo())) {
                throw new RuntimeException("学号已存在: " + dto.getStudentNo());
            }
            student.setStudentNo(dto.getStudentNo());
        }
    }
    
    // 4. 更新其他字段（只更新非空值）
    if (dto.getGender() != null) {
        user.setGender(dto.getGender());
    }
    if (dto.getEmail() != null) {
        user.setEmail(dto.getEmail());
    }
    if (dto.getPhone() != null) {
        user.setPhone(dto.getPhone());
    }
    if (dto.getGrade() != null) {
        student.setGrade(dto.getGrade());
    }
    
    // 5. 更新班级（如果提供）
    if (dto.getClassId() != null) {
        Long classId = null;
        if (dto.getClassId() instanceof Long) {
            classId = (Long) dto.getClassId();
        }
        
        if (classId != null) {
            ClassInfo classInfo = classRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("班级不存在" ));
            student.setClassInfo(classInfo);
        }
    }
    
    // 注意：不要重新设置 role 和 status，除非需要修改
    // user.setRole(Role.STUDENT);
    // user.setStatus(UserStatus.ACTIVE);
    
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
        // 删除关联的记录
         submissionRepository.deleteByStudentId(id);
        errorRecordRepository.deleteByStudentId(id);
        activityRecordRepository.deleteByStudentId(id);
        activityAlertRepository.deleteByStudentId(id);
        enrollmentRepository.deleteByStudentId(id);
        examGradeRepository.deleteByStudentId(id);
        knowledgePointScoreDetailRepository.deleteByStudentId(id);
        scorePredictionRepository.deleteByStudentId(id);
        studentKnowledgeMasteryRepository.deleteByStudentId(id); 
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