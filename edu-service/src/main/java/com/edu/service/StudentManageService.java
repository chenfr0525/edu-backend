// StudentManageService.java
package com.edu.service;

import com.edu.domain.*;
import com.edu.domain.dto.*;
import com.edu.repository.*;
import org.springframework.data.domain.PageImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentManageService {

    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final CourseRepository courseRepository;
    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final ActivityRecordRepository activityRecordRepository;
    private final StudentKnowledgeMasteryRepository masteryRepository;
    private final ExamGradeRepository examGradeRepository;
    private final EnrollmentRepository enrollmentRepository;

    // 临时文件存储（实际生产环境可用Redis或数据库）
    private final Map<String, FileImportPreviewVO> tempFileStore = new HashMap<>();

    /**
     * 获取教师可见的班级列表
     */
    public List<ClassInfo> getTeacherClasses(Long teacherId) {
        Teacher teacher = teacherRepository.findById(teacherId).orElse(null);
        if (teacher == null) {
            return new ArrayList<>();
        }
        return classRepository.findByTeacher(teacher);
    }

    /**
     * 获取教师可见的课程列表
     */
    public List<Course> getTeacherCourses(Long teacherId) {
        Teacher teacher = teacherRepository.findById(teacherId).orElse(null);
        if (teacher == null) {
            return new ArrayList<>();
        }
        return courseRepository.findByTeacher(teacher);
    }

    /**
     * 获取管理员可见的所有班级
     */
    public List<ClassInfo> getAllClasses() {
        return classRepository.findAll();
    }

    /**
     * 获取管理员可见的所有课程
     */
    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    /**
     * 获取学生列表（支持班级、课程筛选和模糊查询）
     */
    @Transactional(readOnly = true)
    public Page<Student> getStudentList(StudentListRequest request, 
                                         Long currentUserId, 
                                         String userRole) {
        // 1. 确定可见的班级ID列表
        List<Long> visibleClassIds = getVisibleClassIds(currentUserId, userRole, request.getClassId());
        
        if (visibleClassIds.isEmpty()) {
            return Page.empty();
        }
        
        Pageable pageable = PageRequest.of(
            request.getPage() != null ? request.getPage() : 0,
            request.getSize() != null ? request.getSize() : 10
        );
        
        // 2. 如果有课程筛选，优先按课程查询
        if (request.getCourseId() != null) {
            return getStudentsByCourse(request.getCourseId(), request.getKeyword(), pageable, visibleClassIds);
        }
        
        // 3. 按班级筛选
        return getStudentsByClasses(visibleClassIds, request.getKeyword(), pageable);
    }

    /**
     * 获取可见的班级ID列表
     */
    private List<Long> getVisibleClassIds(Long userId, String userRole, Long requestClassId) {
        if ("ADMIN".equals(userRole)) {
            if (requestClassId != null) {
                return Arrays.asList(requestClassId);
            }
            return classRepository.findAll().stream()
                .map(ClassInfo::getId)
                .collect(Collectors.toList());
        } else {
            // 教师：只能看自己教的班级
            Teacher teacher = teacherRepository.findByUser(userRepository.findById(userId).orElse(null))
                .orElse(null);
            if (teacher == null) {
                return new ArrayList<>();
            }
            List<ClassInfo> teacherClasses = classRepository.findByTeacher(teacher);
            if (requestClassId != null) {
                boolean hasAccess = teacherClasses.stream()
                    .anyMatch(c -> c.getId().equals(requestClassId));
                return hasAccess ? Arrays.asList(requestClassId) : new ArrayList<>();
            }
            return teacherClasses.stream()
                .map(ClassInfo::getId)
                .collect(Collectors.toList());
        }
    }

    /**
     * 按课程查询学生
     */
    private Page<Student> getStudentsByCourse(Long courseId, String keyword, 
                                               Pageable pageable, List<Long> visibleClassIds) {
        Course course = courseRepository.findById(courseId).orElse(null);
        if (course == null) {
            return Page.empty();
        }
        
        // 获取选修该课程的学生，并过滤班级权限
        Page<Student> studentPage;
        if (keyword != null && !keyword.isEmpty()) {
            studentPage = studentRepository.findByCourseIdAndKeyword(courseId, keyword, pageable);
        } else {
            studentPage = studentRepository.findByCourseId(courseId, pageable);
        }
        
        // 过滤班级权限
        List<Student> filtered = studentPage.getContent().stream()
            .filter(s -> visibleClassIds.contains(s.getClassInfo().getId()))
            .collect(Collectors.toList());
        
        return new PageImpl<>(filtered, pageable, filtered.size());
    }

    /**
     * 按班级查询学生
     */
    private Page<Student> getStudentsByClasses(List<Long> classIds, String keyword, Pageable pageable) {
        if (keyword != null && !keyword.isEmpty()) {
            return studentRepository.findByClassIdsAndKeyword(classIds, keyword, pageable);
        } else {
            return studentRepository.findByClassIds(classIds, pageable);
        }
    }

    /**
     * 获取学生管理统计卡片数据
     */
    @Transactional(readOnly = true)
    public StudentManageStatsVO getStats(Long currentUserId, String userRole, Long classId, Long courseId) {
        // 获取可见的学生列表
        List<Student> students = getVisibleStudents(currentUserId, userRole, classId, courseId);
        
        if (students.isEmpty()) {
            return StudentManageStatsVO.builder()
                .totalStudentCount(0L)
                .activeStudentCount(0L)
                .lowActivityCount(0L)
                .weakPointStudentCount(0L)
                .avgActivityScore(0.0)
                .avgExamScore(0.0)
                .maleCount(0L)
                .femaleCount(0L)
                .build();
        }
        
        Set<Long> studentIds = students.stream()
            .map(Student::getId)
            .collect(Collectors.toSet());
        
        // 统计活跃学生数（近7天有活动）
        LocalDate weekAgo = LocalDate.now().minusDays(7);
        long activeCount = 0;
        double totalActivityScore = 0;
        
        // 统计有薄弱知识点的学生数
        long weakPointCount = 0;
        
        // 统计成绩
        double totalExamScore = 0;
        int examScoreCount = 0;
        
        // 统计性别
        long maleCount = 0;
        long femaleCount = 0;
        
        for (Student student : students) {
            // 性别统计
            User user = student.getUser();
            if ("男".equals(user.getGender())) {
                maleCount++;
            } else if ("女".equals(user.getGender())) {
                femaleCount++;
            }
            
            // 活跃度统计
            List<ActivityRecord> recentActivities = activityRecordRepository
                .findByStudentAndActivityDateBetween(student, weekAgo.atStartOfDay(), LocalDate.now().atStartOfDay());
            if (!recentActivities.isEmpty()) {
                activeCount++;
            }
            
            Double activityScore = getStudentActivityScore(student.getId());
            if (activityScore != null) {
                totalActivityScore += activityScore;
            }
            
            // 薄弱知识点统计
            List<StudentKnowledgeMastery> weakPoints = masteryRepository
                .findByStudentAndMasteryLevelLessThan(student, Double.valueOf(60));
            if (!weakPoints.isEmpty()) {
                weakPointCount++;
            }
            
            // 成绩统计
            Double avgScore = examGradeRepository.getStudentAvgScore(student.getId());
            if (avgScore != null) {
                totalExamScore += avgScore;
                examScoreCount++;
            }
        }
        
        // 低活跃度统计（活跃度<20）
        long lowActivityCount = students.stream()
            .filter(s -> {
                Double score = getStudentActivityScore(s.getId());
                return score != null && score < 20;
            })
            .count();
        
        return StudentManageStatsVO.builder()
            .totalStudentCount((long) students.size())
            .activeStudentCount(activeCount)
            .lowActivityCount(lowActivityCount)
            .weakPointStudentCount(weakPointCount)
            .avgActivityScore(students.isEmpty() ? 0 : Math.round((totalActivityScore / students.size()) * 100) / 100.0)
            .avgExamScore(examScoreCount == 0 ? 0 : Math.round((totalExamScore / examScoreCount) * 100) / 100.0)
            .maleCount(maleCount)
            .femaleCount(femaleCount)
            .build();
    }

    /**
     * 获取可见的学生列表
     */
    private List<Student> getVisibleStudents(Long userId, String userRole, Long classId, Long courseId) {
        List<Long> classIds = getVisibleClassIds(userId, userRole, classId);
        
        if (classIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Student> students = studentRepository.findAllByClassIds(classIds);
        
        // 如果指定了课程，过滤选修该课程的学生
        if (courseId != null) {
            Course course = courseRepository.findById(courseId).orElse(null);
            if (course != null) {
                Set<Long> enrolledStudentIds = enrollmentRepository.findByCourse(course).stream()
                    .map(e -> e.getStudent().getId())
                    .collect(Collectors.toSet());
                students = students.stream()
                    .filter(s -> enrolledStudentIds.contains(s.getId()))
                    .collect(Collectors.toList());
            }
        }
        
        return students;
    }

    /**
     * 获取学生活跃度得分
     */
    private Double getStudentActivityScore(Long studentId) {
        return activityRecordRepository.getTotalActivityScore(studentId);
    }

    /**
     * 上传并解析学生导入文件（预留AI解析）
     */
    @Transactional
    public FileImportPreviewVO parseImportFile(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        String fileExt = fileName != null ? fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase() : "";
        
        List<StudentImportRowVO> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Object> aiAnalysis = new HashMap<>();
        
        if ("csv".equals(fileExt) || "xlsx".equals(fileExt) || "xls".equals(fileExt)) {
            // 解析Excel/CSV文件
            rows = parseExcelFile(file, errors);
        } else if ("txt".equals(fileExt)) {
            // 解析TXT文件（每行一个JSON或特定格式）
            rows = parseTextFile(file, errors);
        } else {
            throw new Exception("不支持的文件格式，请上传CSV、Excel或TXT文件");
        }
        
        // 统计有效/无效行
        long validRows = rows.stream().filter(StudentImportRowVO::getIsValid).count();
        long invalidRows = rows.size() - validRows;
        
        // 调用AI解析（预留）
        aiAnalysis = callAiForFileParse(rows);
        
        // 生成临时文件ID
        String fileId = UUID.randomUUID().toString();
        
        FileImportPreviewVO preview = FileImportPreviewVO.builder()
            .fileId(fileId)
            .fileName(fileName)
            .totalRows(rows.size())
            .validRows((int) validRows)
            .invalidRows((int) invalidRows)
            .rows(rows)
            .errors(errors)
            .aiAnalysis(aiAnalysis)
            .build();
        
        // 存储到临时Map
        tempFileStore.put(fileId, preview);
        
        // 设置30分钟后自动删除（简单实现，实际可用定时任务）
        scheduleTempFileCleanup(fileId);
        
        return preview;
    }

    /**
     * 解析Excel/CSV文件
     */
    private List<StudentImportRowVO> parseExcelFile(MultipartFile file, List<String> errors) {
        List<StudentImportRowVO> rows = new ArrayList<>();
        
        // TODO: 使用 Apache POI 或 EasyExcel 解析
        // 这里先返回模拟数据
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream()))) {
            String line;
            int rowNum = 0;
            while ((line = reader.readLine()) != null && rowNum < 100) { // 限制100行示例
                rowNum++;
                if (rowNum == 1 && line.startsWith("学号")) {
                    continue; // 跳过表头
                }
                
                String[] parts = line.split(",");
                if (parts.length < 4) {
                    rows.add(StudentImportRowVO.builder()
                        .rowNum(rowNum)
                        .isValid(false)
                        .errorMsg("数据列数不足")
                        .build());
                    continue;
                }
                
                String studentNo = parts[0].trim();
                String name = parts[1].trim();
                String username = parts[2].trim();
                String className = parts[3].trim();
                
                // 验证数据有效性
                List<String> validErrors = new ArrayList<>();
                if (studentNo.isEmpty()) validErrors.add("学号不能为空");
                if (name.isEmpty()) validErrors.add("姓名不能为空");
                if (username.isEmpty()) validErrors.add("用户名不能为空");
                
                // 检查学号是否已存在
                if (studentRepository.existsByStudentNo(studentNo)) {
                    validErrors.add("学号已存在");
                }
                
                // 检查用户名是否已存在
                if (userRepository.existsByUsername(username)) {
                    validErrors.add("用户名已存在");
                }
                
                boolean isValid = validErrors.isEmpty();
                
                rows.add(StudentImportRowVO.builder()
                    .rowNum(rowNum)
                    .studentNo(studentNo)
                    .name(name)
                    .username(username)
                    .className(className)
                    .grade(parts.length > 4 ? parts[4].trim() : "大一")
                    .gender(parts.length > 5 ? parts[5].trim() : "未知")
                    .email(parts.length > 6 ? parts[6].trim() : null)
                    .phone(parts.length > 7 ? parts[7].trim() : null)
                    .isValid(isValid)
                    .errorMsg(isValid ? null : String.join("; ", validErrors))
                    .build());
            }
        } catch (Exception e) {
            errors.add("文件解析失败: " + e.getMessage());
            log.error("解析文件失败", e);
        }
        
        return rows;
    }

    /**
     * 解析文本文件
     */
    private List<StudentImportRowVO> parseTextFile(MultipartFile file, List<String> errors) {
        // 类似实现，支持JSON格式
        List<StudentImportRowVO> rows = new ArrayList<>();
        // TODO: 实现TXT解析
        return rows;
    }

    /**
     * 调用AI解析文件数据（预留）
     */
    private Map<String, Object> callAiForFileParse(List<StudentImportRowVO> rows) {
        Map<String, Object> result = new HashMap<>();
        
        // TODO: 接入AI大模型
        // 例如：分析学生数据质量、自动补充缺失信息、智能推荐班级等
        result.put("aiEnabled", false);
        result.put("message", "AI解析功能待接入");
        result.put("dataQuality", "待分析");
        
        // 模拟AI分析
        long validCount = rows.stream().filter(StudentImportRowVO::getIsValid).count();
        result.put("validRate", rows.isEmpty() ? 0 : (validCount * 100.0 / rows.size()));
        result.put("suggestion", "建议检查重复学号和用户名");
        
        return result;
    }

    /**
     * 获取文件预览数据
     */
    public FileImportPreviewVO getFilePreview(String fileId) {
        return tempFileStore.get(fileId);
    }

    /**
     * 确认导入学生数据
     */
    @Transactional
    public StudentImportResultVO confirmImport(StudentImportConfirmRequest request) {
        FileImportPreviewVO preview = tempFileStore.get(request.getFileId());
        if (preview == null) {
            throw new RuntimeException("文件预览已过期，请重新上传");
        }
        
        List<StudentImportRowVO> rowsToImport = new ArrayList<>();
        
        if (request.getSelectedRowIndexes() != null && !request.getSelectedRowIndexes().isEmpty()) {
            // 导入选中的行
             List<Long>  indexes = request.getSelectedRowIndexes();
           for (Long idx : indexes) { 
                if (idx >= 0 && idx < preview.getRows().size()) {
                    rowsToImport.add(preview.getRows().get(idx.intValue()));
                }
            }
        } else {
            // 导入所有有效行
            rowsToImport = preview.getRows().stream()
                .filter(StudentImportRowVO::getIsValid)
                .collect(Collectors.toList());
        }
        
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();
        
        for (StudentImportRowVO row : rowsToImport) {
            try {
                importSingleStudent(row);
                successCount++;
            } catch (Exception e) {
                failCount++;
                errors.add("第" + row.getRowNum() + "行导入失败: " + e.getMessage());
                log.error("导入学生失败", e);
            }
        }
        
        // 导入完成后删除临时文件
        tempFileStore.remove(request.getFileId());
        
        return StudentImportResultVO.builder()
            .successCount(successCount)
            .failCount(failCount)
            .errors(errors)
            .build();
    }

    /**
     * 导入单个学生
     */
    private void importSingleStudent(StudentImportRowVO row) {
        // 创建User
        User user = new User();
        user.setUsername(row.getUsername());
        user.setPassword(passwordEncoder.encode("123456")); // 默认密码
        user.setName(row.getName());
        user.setRole(Role.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        user.setGender(row.getGender());
        user.setEmail(row.getEmail());
        user.setPhone(row.getPhone());
        User savedUser = userRepository.save(user);
        
        // 处理班级
        ClassInfo classInfo = classRepository.findByName(row.getClassName())
            .orElseGet(() -> {
                ClassInfo newClass = new ClassInfo();
                newClass.setName(row.getClassName());
                newClass.setGrade(row.getGrade() != null ? row.getGrade() : "大一");
                return classRepository.save(newClass);
            });
        
        // 创建Student
        Student student = new Student();
        student.setUser(savedUser);
        student.setStudentNo(row.getStudentNo());
        student.setClassInfo(classInfo);
        student.setGrade(row.getGrade() != null ? row.getGrade() : "大一");
        studentRepository.save(student);
    }

    /**
     * 取消导入（删除临时文件）
     */
    public void cancelImport(String fileId) {
        tempFileStore.remove(fileId);
    }

    /**
     * 定时清理临时文件（简化版，实际可用@Scheduled）
     */
    private void scheduleTempFileCleanup(String fileId) {
        // TODO: 使用定时任务或延迟删除
        new Thread(() -> {
            try {
                Thread.sleep(30 * 60 * 1000); // 30分钟后删除
                tempFileStore.remove(fileId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    // 需要注入的依赖
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
}