package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.var;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu.domain.AiAnalysisReport;
import com.edu.domain.Course;
import com.edu.domain.CourseStatus;
import com.edu.domain.Enrollment;
import com.edu.domain.Exam;
import com.edu.domain.ExamGrade;
import com.edu.domain.ExamStatus;
import com.edu.domain.KnowledgePoint;
import com.edu.domain.KnowledgePointScoreDetail;
import com.edu.domain.Role;
import com.edu.domain.Semester;
import com.edu.domain.Student;
import com.edu.domain.StudentKnowledgeMastery;
import com.edu.domain.User;
import com.edu.domain.dto.AiSuggestionDTO;
import com.edu.domain.dto.ExamGradeImportResult;
import com.edu.domain.dto.FieldMapping;
import com.edu.domain.dto.ValidationError;
import com.edu.repository.ExamGradeRepository;
import com.edu.repository.ExamRepository;
import com.edu.repository.StudentRepository;
import com.edu.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.edu.repository.KnowledgePointRepository;
import com.edu.repository.KnowledgePointScoreDetailRepository;
import com.edu.repository.SemesterRepository;
import com.edu.repository.StudentKnowledgeMasteryRepository;
import com.edu.repository.AiAnalysisReportRepository;
import com.edu.repository.EnrollmentRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamGradeImportValidator {

    private final DeepSeekService deepSeekService;
    private final ExamGradeRepository examGradeRepository;
    private final ExamRepository examRepository;
    private final StudentRepository studentRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final StudentKnowledgeMasteryRepository masteryRepository;
    private final AiAnalysisReportRepository aiReportRepository;
    private final UserRepository userRepository;
    private final KnowledgePointScoreDetailRepository kpScoreDetailRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ActivitySyncService activitySyncService;
    private final UnifiedAiAnalysisService unifiedAiAnalysisService;

    /**
     * 确认导入考试成绩
     * @param examId 考试ID
     * @param data 成绩数据列表
     * @return 导入结果
     */
   @Transactional
    public ExamGradeImportResult insertExamGradeData(Long examId, List<Map<String, Object>> data) {
        Exam exam = examRepository.findById(examId)
            .orElseThrow(() -> new RuntimeException("考试不存在"));

        List<FieldMapping> mappings = getExamGradeFieldMappings();

        List<ValidationError> errors = deepSeekService.validateData(data, mappings);
        if (!errors.isEmpty()) {
            log.error("数据验证失败：{}", errors);
            return ExamGradeImportResult.builder()
                .success(false)
                .errorMessage(buildErrorMessage(errors))
                .errors(errors)
                .build();
        }

        int successCount = 0;
        int failCount = 0;
        int updateCount = 0;
        List<ExamGrade> savedGrades = new ArrayList<>();
        StringBuilder resultMsg = new StringBuilder();

        for (Map<String, Object> row : data) {
            try {
                ExamGrade grade = insertOrUpdateExamGrade(exam, row);
                savedGrades.add(grade);
                successCount++;
                if (examGradeRepository.findByExamIdAndStudentId(examId, grade.getStudent().getId()).isPresent()) {
                    updateCount++;
                }
                log.info("成功导入成绩 - 学生：{}，成绩：{}", row.get("studentName"), row.get("score"));
            } catch (Exception e) {
                failCount++;
                String errorMsg = String.format("导入失败 - 学生：%s，原因：%s",
                    row.get("studentName"), e.getMessage());
                log.error(errorMsg);
                resultMsg.append(errorMsg).append("\n");
            }
        }

         // 更新考试统计数据
        updateExamStatistics(exam);

        // 处理知识点掌握度（触发AI分析）
        boolean aiCompleted = processExamGradesAndUpdateMastery(exam, savedGrades);

        String summary = String.format("导入完成！成功：%d条（新增%d条，更新%d条），失败：%d条", 
            successCount, successCount - updateCount, updateCount, failCount);
        log.info(summary);

        return ExamGradeImportResult.builder()
            .success(failCount == 0)
            .successCount(successCount)
            .failCount(failCount)
            .updateCount(updateCount)
            .message(summary)
            .errorMessage(resultMsg.toString())
            .aiAnalysisCompleted(aiCompleted)
            .build();
    }

     /**
     * 处理考试成绩并更新知识点掌握度（迁移到统一服务）
     */
    private boolean processExamGradesAndUpdateMastery(Exam exam, List<ExamGrade> grades) {
        try {
            // 获取考试关联的知识点ID列表
            List<Long> knowledgePointIds = exam.getKnowledgePointIds();
            
            if (knowledgePointIds == null || knowledgePointIds.isEmpty()) {
                log.warn("考试 {} 未关联知识点，跳过掌握度计算", exam.getId());
                return false;
            }
            
            List<KnowledgePoint> kps = knowledgePointRepository.findAllById(knowledgePointIds);
            if (kps.isEmpty()) {
                log.warn("未找到知识点信息，跳过掌握度计算");
                return false;
            }

            // 1. 更新学生知识点掌握度（原有逻辑保留）
            for (ExamGrade grade : grades) {
                Student student = grade.getStudent();
                Double scoreRate = (grade.getScore() != null && exam.getFullScore() != null && exam.getFullScore() > 0)
                    ? grade.getScore().doubleValue() / exam.getFullScore() * 100
                    : (grade.getScore() != null ? grade.getScore().doubleValue() : 0);
                
                // 自动创建选课记录
                ensureEnrollment(student, exam.getCourse());
                
               for (KnowledgePoint kp : kps) {
                    updateStudentMastery(student, kp, scoreRate);
                    saveKnowledgePointScoreDetail(student, kp, "EXAM", exam.getId(), scoreRate);
                }
            }
            // 2. 使用统一服务生成AI分析报告（自动保存）
            // 注意：这里不需要手动保存，统一服务会自动处理
            AiSuggestionDTO aiResult = unifiedAiAnalysisService.getOrCreateAnalysis(
                "EXAM",           // targetType: 考试类型
                exam.getId(),     // targetId: 考试ID
                "EXAM_ANALYSIS",  // reportType: 考试分析
                true              // 强制刷新（因为刚导入新数据）
            );
            
            log.info("考试成绩AI分析完成，考试ID: {}, 摘要: {}", exam.getId(), 
                aiResult != null ? aiResult.getSummary() : "无");
            
            return true;
            
        } catch (Exception e) {
            log.error("处理考试成绩失败", e);
            return false;
        }
    }



   /**
     * 插入或更新单条考试成绩
     */
    private ExamGrade insertOrUpdateExamGrade(Exam exam, Map<String, Object> row) {
        String studentIdentifier = (String) row.get("studentName");
        Double score = ((Number) row.get("score")).doubleValue();
        String remark = (String) row.get("remark");

        Student student = findStudentByIdentifier(studentIdentifier);
        if (student == null) {
            throw new RuntimeException("学生不存在: " + studentIdentifier);
        }
        // 自动创建选课记录
        ensureEnrollment(student, exam.getCourse());
        ExamGrade grade = examGradeRepository.findByExamIdAndStudentId(exam.getId(), student.getId())
            .orElse(null);

        boolean isNew = (grade == null);
        if (isNew) {
            grade = new ExamGrade();
            grade.setExam(exam);
            grade.setStudent(student);
            grade.setCreatedAt(LocalDateTime.now());
        }

        grade.setScore(score.intValue());
        grade.setRemark(remark);

        ExamGrade saved = examGradeRepository.save(grade);
    
    // ========== 新增：同步考试参与活动记录 ==========
    try {
        activitySyncService.syncExamActivity(student, exam, score.intValue());
    } catch (Exception e) {
        log.error("同步考试活动记录失败", e);
    }

        return saved;
    }

       /**
     * 确保学生已选课（自动创建 Enrollment）
     */
    private void ensureEnrollment(Student student, Course course) {
        if (course == null) return;
        
        List<Enrollment> enrollments = enrollmentRepository.findByStudentAndCourse(student, course);
        if (enrollments.isEmpty()) {
            Enrollment newEnrollment = new Enrollment();
            newEnrollment.setStudent(student);
            newEnrollment.setCourse(course);
            newEnrollment.setStatus(CourseStatus.ONGOING);
            newEnrollment.setProgress(0);
            newEnrollment.setEnrolledAt(LocalDateTime.now());
            enrollmentRepository.save(newEnrollment);
            log.info("自动创建选课记录 - 学生：{}，课程：{}", 
                student.getUser().getName(), course.getName());
        }
    }

 /**
     * 通过姓名或学号查找学生
     */
    private Student findStudentByIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) return null;
        
        String trimmedIdentifier = identifier.trim();
        
        Optional<Student> studentByNo = studentRepository.findByStudentNo(trimmedIdentifier);
        if (studentByNo.isPresent()) {
            return studentByNo.get();
        }Optional<User> userByUsername = userRepository.findByUsername(trimmedIdentifier);
        if (userByUsername.isPresent()) {
            User user = userByUsername.get();
            if (user.getRole() == Role.STUDENT) {
                Optional<Student> studentByUser = studentRepository.findByUser(user);
                if (studentByUser.isPresent()) {
                    return studentByUser.get();
                }
            }
        }
        
        List<User> usersByName = userRepository.findByName(trimmedIdentifier);
        for (User user : usersByName) {
            if (user.getRole() == Role.STUDENT) {
                Optional<Student> student = studentRepository.findByUser(user);
                if (student.isPresent()) return student.get();
            }
        }
        
        throw new RuntimeException("学生不存在: " + trimmedIdentifier);
    }

     /**
     * 更新考试统计数据
     */
    private void updateExamStatistics(Exam exam) {
        List<ExamGrade> grades = examGradeRepository.findByExam(exam);
        if (grades.isEmpty()) return;

        double avg = grades.stream()
            .mapToDouble(g -> g.getScore().doubleValue())
            .average()
            .orElse(0);
        double highest = grades.stream()
            .mapToDouble(g -> g.getScore().doubleValue())
            .max()
            .orElse(0);
        double lowest = grades.stream()
            .mapToDouble(g -> g.getScore().doubleValue())
            .min()
            .orElse(0);

        exam.setClassAvgScore(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        exam.setHighestScore(BigDecimal.valueOf(highest).setScale(2, RoundingMode.HALF_UP));
        exam.setLowestScore(BigDecimal.valueOf(lowest).setScale(2, RoundingMode.HALF_UP));
        exam.setStatus(ExamStatus.COMPLETED);
        examRepository.save(exam);
    }

     /**
     * 更新学生知识点掌握度
     */
    private void updateStudentMastery(Student student, KnowledgePoint kp, double newScoreRate) {
        Optional<StudentKnowledgeMastery> existing = masteryRepository
            .findByStudentAndKnowledgePoint(student, kp);
        
        StudentKnowledgeMastery mastery;
        if (existing.isPresent()) {
            mastery = existing.get();
            // 加权平均：旧值70% + 新值30%
            double newLevel = mastery.getMasteryLevel() * 0.7 + newScoreRate * 0.3;
            mastery.setMasteryLevel(Math.min(newLevel, 100));
        } else {
            mastery = new StudentKnowledgeMastery();
            mastery.setStudent(student);
            mastery.setKnowledgePoint(kp);
            mastery.setMasteryLevel(newScoreRate);
        }
        mastery.setUpdatedAt(LocalDateTime.now());
        
        // 设置薄弱程度
        double level = mastery.getMasteryLevel();
        if (level < 50) mastery.setWeaknessLevel("SEVERE");
        else if (level < 60) mastery.setWeaknessLevel("MODERATE");
        else if (level < 70) mastery.setWeaknessLevel("MILD");
        else mastery.setWeaknessLevel("GOOD");
        
        masteryRepository.save(mastery);
    }

     /**
     * 保存知识点得分明细
     */
    private void saveKnowledgePointScoreDetail(Student student, KnowledgePoint kp, 
            String sourceType, Long sourceId, double scoreRate) {
        KnowledgePointScoreDetail detail = new KnowledgePointScoreDetail();
        detail.setStudent(student);
        detail.setKnowledgePoint(kp);
        detail.setSourceType(sourceType);
        detail.setSourceId(sourceId);
        detail.setScoreRate(BigDecimal.valueOf(scoreRate).setScale(2, RoundingMode.HALF_UP));
        detail.setMaxScore(BigDecimal.valueOf(100));
        detail.setActualScore(BigDecimal.valueOf(scoreRate).setScale(2, RoundingMode.HALF_UP));
        detail.setCreatedAt(LocalDateTime.now());
        
        kpScoreDetailRepository.save(detail);
    }
    
    /**
     * 获取考试成绩字段映射配置
     */
    public List<FieldMapping> getExamGradeFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();

        FieldMapping studentName = new FieldMapping();
        studentName.setTargetField("studentName");
        studentName.setFieldDescription("学生姓名或学号");
        studentName.setPossibleNames(Arrays.asList("学生", "学生姓名", "姓名", "学号", "Student", "Student Name", "StudentNo"));
        studentName.setRequired(true);
        studentName.setDataType("string");
        studentName.setNeedExist(true);
        mappings.add(studentName);

        FieldMapping score = new FieldMapping();
        score.setTargetField("score");
        score.setFieldDescription("考试成绩");
        score.setPossibleNames(Arrays.asList("成绩", "分数", "得分", "Score", "Grade"));
        score.setRequired(true);
        score.setDataType("number");
        mappings.add(score);

        FieldMapping remark = new FieldMapping();
        remark.setTargetField("remark");
        remark.setFieldDescription("备注");
        remark.setPossibleNames(Arrays.asList("备注", "评语", "Remark", "Comment", "说明"));
        remark.setRequired(false);
        remark.setDataType("string");
        mappings.add(remark);

        return mappings;
    }

     /**
     * 构建错误消息
     */
    public String buildErrorMessage(List<ValidationError> errors) {
        StringBuilder sb = new StringBuilder();
        for (ValidationError error : errors) {
            sb.append(error.getErrorMessage()).append("\n");
        }
        return sb.toString();
    }
}