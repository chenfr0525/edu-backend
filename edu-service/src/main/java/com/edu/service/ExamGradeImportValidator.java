package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.edu.domain.Course;
import com.edu.domain.CourseStatus;
import com.edu.domain.Enrollment;
import com.edu.domain.Exam;
import com.edu.domain.ExamGrade;
import com.edu.domain.ExamStatus;
import com.edu.domain.KnowledgePoint;
import com.edu.domain.KnowledgePointScoreDetail;
import com.edu.domain.Role;
import com.edu.domain.Student;
import com.edu.domain.StudentKnowledgeMastery;
import com.edu.domain.User;
import com.edu.domain.dto.AiSuggestionDTO;
import com.edu.domain.dto.ExamGradeImportResult;
import com.edu.domain.dto.FieldMapping;
import com.edu.domain.dto.ParseResult;
import com.edu.domain.dto.ValidationError;
import com.edu.repository.ExamGradeRepository;
import com.edu.repository.ExamRepository;
import com.edu.repository.StudentRepository;
import com.edu.repository.UserRepository;
import com.edu.repository.KnowledgePointRepository;
import com.edu.repository.KnowledgePointScoreDetailRepository;
import com.edu.repository.StudentKnowledgeMasteryRepository;
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
    private final UserRepository userRepository;
    private final KnowledgePointScoreDetailRepository kpScoreDetailRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ActivitySyncService activitySyncService;
    private final UnifiedAiAnalysisService unifiedAiAnalysisService;

 /**
     * 获取考试成绩解析阶段字段映射（用于AI解析Excel文件）
     * 字段名：用户友好的名称（学生姓名）
     */
    public List<FieldMapping> getExamGradeParseFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();
        
        // 学生（必填，可用学号或姓名）
        FieldMapping studentName = new FieldMapping();
        studentName.setTargetField("studentName");
        studentName.setFieldDescription("学生姓名或学号");
        studentName.setPossibleNames(Arrays.asList("学生", "学生姓名", "姓名", "学号", "Student", "Student Name", "StudentNo"));
        studentName.setRequired(true);
        studentName.setDataType("string");
        studentName.setNeedExist(true);
        mappings.add(studentName);
        
        // 成绩（必填）
        FieldMapping score = new FieldMapping();
        score.setTargetField("score");
        score.setFieldDescription("考试得分");
        score.setPossibleNames(Arrays.asList("成绩", "分数", "得分", "Score"));
        score.setRequired(true);
        score.setDataType("number");
        mappings.add(score);
        
        // 备注（可选）
        FieldMapping remark = new FieldMapping();
        remark.setTargetField("remark");
        remark.setFieldDescription("备注");
        remark.setPossibleNames(Arrays.asList("备注", "评语", "Remark", "Comment"));
        remark.setRequired(false);
        remark.setDataType("string");
        mappings.add(remark);
        
        return mappings;
    }

    /**
     * 获取考试成绩导入阶段字段映射（用于验证前端传来的数据）
     * 字段名：代码中使用的字段名
     */
    public List<FieldMapping> getExamGradeImportFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();
        
        // 学生（必填）
        FieldMapping studentName = new FieldMapping();
        studentName.setTargetField("studentName");
        studentName.setFieldDescription("学生姓名或学号");
        studentName.setPossibleNames(Arrays.asList("studentName", "学生"));
        studentName.setRequired(true);
        studentName.setDataType("string");
        studentName.setNeedExist(true);
        mappings.add(studentName);
        
        // 成绩（必填）
        FieldMapping score = new FieldMapping();
        score.setTargetField("score");
        score.setFieldDescription("考试得分");
        score.setPossibleNames(Arrays.asList("score", "成绩"));
        score.setRequired(true);
        score.setDataType("number");
        mappings.add(score);
        
        // 备注（可选）
        FieldMapping remark = new FieldMapping();
        remark.setTargetField("remark");
        remark.setFieldDescription("备注");
        remark.setPossibleNames(Arrays.asList("remark", "备注"));
        remark.setRequired(false);
        remark.setDataType("string");
        mappings.add(remark);
        
        return mappings;
    }
    
    /**
     * AI解析考试成绩文件后，自动将学生名称转换为ID
     */
    public ParseResult parseAndConvertExamGradeFile(String fileContent, String fileName) {
        // 1. 获取解析阶段的字段映射
        List<FieldMapping> mappings = getExamGradeParseFieldMappings();
        
        // 2. AI解析文件
        ParseResult result = deepSeekService.parseFileData(fileContent, fileName, "exam_grade", mappings);
        
        // 3. 解析成功后，自动转换学生名称到ID
        if (result.isSuccess() && result.getData() != null && !result.getData().isEmpty()) {
            convertExamGradeParseResultToIds(result);
        }
        
        return result;
    }

    /**
     * 将考试成绩解析结果中的学生名称转换为ID
     */
    private void convertExamGradeParseResultToIds(ParseResult result) {
        List<Map<String, Object>> data = result.getData();
        
        for (Map<String, Object> row : data) {
            String studentIdentifier = (String) row.get("studentName");
            if (studentIdentifier != null && !studentIdentifier.isEmpty()) {
                Student student = findStudentByIdentifier(studentIdentifier);
                if (student != null) {
                    row.put("studentId", student.getId());
                    row.put("studentNo", student.getStudentNo());
                    // 获取学生姓名用于显示
                    if (student.getUser() != null) {
                        row.put("studentDisplayName", student.getUser().getName());
                    }
                } else {
                    row.put("studentId", null);
                    row.put("_error_studentName", "学生不存在: " + studentIdentifier);
                }
            }
        }
    }
    
    
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

        List<FieldMapping> mappings = getExamGradeImportFieldMappings();

        // 1. 验证数据
        List<ValidationError> errors = deepSeekService.validateData(data, mappings);
        if (!errors.isEmpty()) {
            log.error("数据验证失败：{}", errors);
            return ExamGradeImportResult.builder()
                .success(false)
                .errorMessage(buildErrorMessage(errors))
                .errors(errors)
                .build();
        }

        // 2. 批量导入成绩
        int successCount = 0;
        int failCount = 0;
        int updateCount = 0;
        List<ExamGrade> savedGrades = new ArrayList<>();
        StringBuilder resultMsg = new StringBuilder();

         for (int i = 0; i < data.size(); i++) {
            Map<String, Object> row = data.get(i);
            try {
                ExamGrade grade = insertOrUpdateExamGrade(exam, row);
                savedGrades.add(grade);
                successCount++;
                
                if (examGradeRepository.findByExamIdAndStudentId(examId, grade.getStudent().getId()).isPresent()) {
                    updateCount++;
                }
                
                log.info("成功导入成绩 - 第{}行，学生：{}，成绩：{}", 
                    i + 1, row.get("studentName"), row.get("score"));
            } catch (Exception e) {
                failCount++;
                String errorMsg = String.format("第%d行 - 学生：%s，原因：%s",
                    i + 1, row.get("studentName"), e.getMessage());
                log.error(errorMsg);
                resultMsg.append(errorMsg).append("\n");
                throw new RuntimeException("成绩导入失败，已回滚所有数据：" + errorMsg);
            }
        }
          // 3. 更新考试统计数据
        recalculateExamCourseRankingAndTrend(exam);
        updateExamStatistics(exam);

        // 4. 处理知识点掌握度并触发AI分析
        boolean aiCompleted = processExamGradesAndUpdateMastery(exam, savedGrades);

        boolean allSuccess = failCount == 0;
        String summary = String.format("导入完成！成功：%d条（新增%d条，更新%d条），失败：%d条", 
            successCount, successCount - updateCount, updateCount, failCount);
        
        if (failCount > 0) {
            summary = summary + "\n" + resultMsg.toString();
        }
        log.info(summary);

       return ExamGradeImportResult.builder()
            .success(allSuccess)
            .successCount(successCount)
            .failCount(failCount)
            .updateCount(updateCount)
            .message(summary)
            .errorMessage(resultMsg.toString())
            .aiAnalysisCompleted(aiCompleted)
            .build();
    }

   /**
     * 处理考试成绩并更新知识点掌握度
     */
    private boolean processExamGradesAndUpdateMastery(Exam exam, List<ExamGrade> grades) {
        try {
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

            for (ExamGrade grade : grades) {
                Student student = grade.getStudent();
                Double scoreRate = (grade.getScore() != null && exam.getFullScore() != null && exam.getFullScore() > 0)
                    ? grade.getScore().doubleValue() / exam.getFullScore() * 100
                    : (grade.getScore() != null ? grade.getScore().doubleValue() : 0);
                
                ensureEnrollment(student, exam.getCourse());
                
                for (KnowledgePoint kp : kps) {
                    saveKnowledgePointScoreDetail(student, kp, "EXAM", exam.getId(), scoreRate);
                    updateStudentMastery(student, kp, scoreRate);
                }
            }
            
            // 使用统一服务生成AI分析报告
            AiSuggestionDTO aiResult = unifiedAiAnalysisService.getOrCreateAnalysis(
                "EXAM",
                exam.getId(),
                "EXAM_ANALYSIS",
                true
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
        
        ensureEnrollment(student, exam.getCourse());
        
        ExamGrade grade = examGradeRepository.findByExamIdAndStudentId(exam.getId(), student.getId())
            .orElse(null);

        if (grade == null) {
            grade = new ExamGrade();
            grade.setExam(exam);
            grade.setStudent(student);
            grade.setCreatedAt(LocalDateTime.now());
        }

        grade.setScore(score.intValue());
        grade.setRemark(remark);

        ExamGrade saved = examGradeRepository.save(grade);
    
        try {
            activitySyncService.syncExamActivity(student, exam, score.intValue());
        } catch (Exception e) {
            log.error("同步考试活动记录失败", e);
        }

        return saved;
    }

    /**
     * 基于当前考试对应课程的已选课学生成绩，统一重算排名和进退步趋势
     */
    private void recalculateExamCourseRankingAndTrend(Exam exam) {
        List<ExamGrade> allGrades = examGradeRepository.findByExam(exam).stream()
            .filter(g -> g.getScore() != null)
            .filter(g -> !enrollmentRepository.findByStudentAndCourse(g.getStudent(), exam.getCourse()).isEmpty())
            .sorted(Comparator.comparing(ExamGrade::getScore).reversed())
            .collect(java.util.stream.Collectors.toList());

        for (int i = 0; i < allGrades.size(); i++) {
            ExamGrade grade = allGrades.get(i);
            int currentRank = i + 1;
            grade.setClassRank(currentRank);

            Integer previousRank = getPreviousExamRank(grade.getStudent(), exam);
            if (previousRank == null) {
                grade.setScoreTrend("STABLE");
            } else if (currentRank < previousRank) {
                grade.setScoreTrend("UP");
            } else if (currentRank > previousRank) {
                grade.setScoreTrend("DOWN");
            } else {
                grade.setScoreTrend("STABLE");
            }
        }
        examGradeRepository.saveAll(allGrades);
    }
       /**
     * 获取学生上次同类型考试的成绩
     */
    private Integer getPreviousExamRank(Student student, Exam currentExam) {
        // 查找同课程、同类型且考试日期在当前之前的考试
        List<Exam> previousExams = examRepository.findByCourseAndExamDateBefore(
            currentExam.getCourse(), 
            currentExam.getExamDate()
        );
        
        if (previousExams.isEmpty()) {
            return null;
        }
        
        // 取最近的一次考试
        Exam previousExam = previousExams.get(0);
        Optional<ExamGrade> previousGrade = examGradeRepository
            .findByExamIdAndStudentId(previousExam.getId(), student.getId());
        
        return previousGrade.map(ExamGrade::getClassRank).orElse(null);
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
        List<ExamGrade> grades = examGradeRepository.findByExam(exam).stream()
            .filter(g -> g.getScore() != null)
            .filter(g -> !enrollmentRepository.findByStudentAndCourse(g.getStudent(), exam.getCourse()).isEmpty())
            .collect(java.util.stream.Collectors.toList());
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
        } else {
            mastery = new StudentKnowledgeMastery();
            mastery.setStudent(student);
            mastery.setKnowledgePoint(kp);
        }

        List<KnowledgePointScoreDetail> allDetails = kpScoreDetailRepository.findByStudentAndKnowledgePoint(student, kp);
        double avgActualScore = allDetails.stream()
            .filter(Objects::nonNull)
            .map(KnowledgePointScoreDetail::getActualScore)
            .filter(Objects::nonNull)
            .mapToDouble(BigDecimal::doubleValue)
            .average()
            .orElse(newScoreRate);

        double normalized = Math.min(Math.max(avgActualScore, 0D), 100D);
        mastery.setMasteryLevel(normalized);
        mastery.setScore(normalized);
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
        KnowledgePointScoreDetail detail = kpScoreDetailRepository
            .findFirstByStudentAndKnowledgePointAndSourceTypeAndSourceIdOrderByCreatedAtDesc(
                student, kp, sourceType, sourceId)
            .orElseGet(() -> {
                KnowledgePointScoreDetail created = new KnowledgePointScoreDetail();
                created.setStudent(student);
                created.setKnowledgePoint(kp);
                created.setSourceType(sourceType);
                created.setSourceId(sourceId);
                created.setCreatedAt(LocalDateTime.now());
                return created;
            });

        BigDecimal normalized = BigDecimal.valueOf(scoreRate).setScale(2, RoundingMode.HALF_UP);
        detail.setScoreRate(normalized);
        detail.setMaxScore(BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP));
        detail.setActualScore(normalized);
        
        kpScoreDetailRepository.save(detail);
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
