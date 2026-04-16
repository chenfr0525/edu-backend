// ExamGradeImportValidator.java
package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.var;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu.domain.Course;
import com.edu.domain.CourseStatus;
import com.edu.domain.Enrollment;
import com.edu.domain.Exam;
import com.edu.domain.ExamGrade;
import com.edu.domain.Role;
import com.edu.domain.Semester;
import com.edu.domain.Student;
import com.edu.domain.User;
import com.edu.domain.dto.ExamGradeImportResult;
import com.edu.domain.dto.FieldMapping;
import com.edu.domain.dto.ValidationError;
import com.edu.repository.ExamGradeRepository;
import com.edu.repository.ExamRepository;
import com.edu.repository.StudentRepository;
import com.edu.repository.UserRepository;
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
    private final SemesterRepository semesterRepository;
    private final EnrollmentRepository enrollmentRepository;
    

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

        // 调用AI分析并存储结果
        boolean aiCompleted = callAiAndSaveAnalysis(exam, savedGrades);

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
     * 插入或更新单条考试成绩
     */
    private ExamGrade insertOrUpdateExamGrade(Exam exam, Map<String, Object> row) {
        String studentIdentifier = (String) row.get("studentName");
        Double score = ((Number) row.get("score")).doubleValue();
        String remark = (String) row.get("remark");

        // 查找学生（支持姓名或学号）
        Student student = findStudentByIdentifier(studentIdentifier);
        if (student == null) {
            throw new RuntimeException("学生不存在: " + studentIdentifier);
        }

       Course course = exam.getCourse();
    if (course == null) {
        throw new RuntimeException("考试未关联课程，无法处理选课信息");
    }

    // 查询是否已有选课记录
    List<Enrollment> enrollments = enrollmentRepository.findByStudentAndCourse(student, course);
    
    if (enrollments.isEmpty()) {
        // 自动创建选课记录
        Enrollment newEnrollment = new Enrollment();
        newEnrollment.setStudent(student);
        newEnrollment.setCourse(course);
        newEnrollment.setSemester(getCurrentSemester());
        newEnrollment.setStatus(CourseStatus.ONGOING);
        newEnrollment.setProgress(0);
        newEnrollment.setEnrolledAt(LocalDateTime.now());
        
        enrollmentRepository.save(newEnrollment);
        log.info("自动创建选课记录 - 学生：{}，课程：{}", 
            student.getUser().getName(), course.getName());
    }

        // 检查是否已存在成绩
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

        // 计算班级排名
        List<ExamGrade> allGrades = examGradeRepository.findByExam(exam);
        if (!isNew) {
            allGrades.removeIf(g -> g.getStudent().getId().equals(student.getId()));
        }
        allGrades.add(grade);
        allGrades.sort((a, b) -> b.getScore().compareTo(a.getScore()));
        for (int i = 0; i < allGrades.size(); i++) {
            if (allGrades.get(i).getStudent().getId().equals(student.getId())) {
                grade.setClassRank(i + 1);
                break;
            }
        }

        return examGradeRepository.save(grade);
    }

/**
 * 通过姓名或学号查找学生
 */
private Student findStudentByIdentifier(String identifier) {
    if (identifier == null || identifier.trim().isEmpty()) return null;
    
    String trimmedIdentifier = identifier.trim();
    
    // 1. 尝试按学号查找
    Optional<Student> studentByNo = studentRepository.findByStudentNo(trimmedIdentifier);
    if (studentByNo.isPresent()) {
        return studentByNo.get();
    }
    
    // 2. 尝试按用户名查找（username是唯一值）
    Optional<User> userByUsername = userRepository.findByUsername(trimmedIdentifier);
    if (userByUsername.isPresent()) {
        User user = userByUsername.get();
        if (user.getRole() == Role.STUDENT) {
            Optional<Student> studentByUser = studentRepository.findByUser(user);
            if (studentByUser.isPresent()) {
                return studentByUser.get();
            }
        }
    }
    
    // 3. 尝试按姓名查找（姓名可能重复，需要处理）
    List<User> usersByName = userRepository.findByName(trimmedIdentifier);
    List<Student> matchedStudents = new ArrayList<>();
    
    for (User user : usersByName) {
        if (user.getRole() == Role.STUDENT) {
            Optional<Student> student = studentRepository.findByUser(user);
            student.ifPresent(matchedStudents::add);
        }
    }
    
    if (matchedStudents.size() == 1) {
        return matchedStudents.get(0);
    } else if (matchedStudents.size() > 1) {
        throw new RuntimeException("存在多个同名学生: " + trimmedIdentifier + "，请使用学号或用户名");
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

        // 修复：指定舍入模式为 HALF_UP
    exam.setClassAvgScore(BigDecimal.valueOf(avg).setScale(2, BigDecimal.ROUND_HALF_UP));
    exam.setHighestScore(BigDecimal.valueOf(highest).setScale(2, BigDecimal.ROUND_HALF_UP));
    exam.setLowestScore(BigDecimal.valueOf(lowest).setScale(2, BigDecimal.ROUND_HALF_UP));
    exam.setStatus(com.edu.domain.ExamStatus.COMPLETED);
    examRepository.save(exam);
        examRepository.save(exam);
    }

    /**
     * 调用AI分析并存储结果
     */
    private boolean callAiAndSaveAnalysis(Exam exam, List<ExamGrade> grades) {
        try {
            // 获取课程知识点
            List<com.edu.domain.KnowledgePoint> kps = knowledgePointRepository.findByCourse(exam.getCourse());

            // 计算各知识点得分率
            Map<Long, KpStats> kpStatsMap = new HashMap<>();
            for (com.edu.domain.KnowledgePoint kp : kps) {
                kpStatsMap.put(kp.getId(), new KpStats());
            }

            // 分析成绩分布
            List<Double> scores = grades.stream()
                .map(g -> g.getScore().doubleValue())
                .sorted()
                .collect(Collectors.toList());

            double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            long passCount = scores.stream().filter(s -> s >= exam.getPassScore()).count();
            long excellentCount = scores.stream().filter(s -> s >= 80).count();
            long failCount = scores.size() - passCount;

            // 构建AI分析数据
            Map<String, Object> aiAnalysisData = new HashMap<>();
            List<Map<String, Object>> kpAnalysis = new ArrayList<>();
            List<String> strengths = new ArrayList<>();
            List<String> weaknesses = new ArrayList<>();

            for (com.edu.domain.KnowledgePoint kp : kps) {
                KpStats stats = kpStatsMap.get(kp.getId());
                double avgRate = stats.count > 0 ? stats.totalScore / stats.count : 0;

                Map<String, Object> kpResult = new HashMap<>();
                kpResult.put("knowledgePointId", kp.getId());
                kpResult.put("knowledgePointName", kp.getName());
                kpResult.put("avgScoreRate", Math.round(avgRate * 100) / 100.0);
                kpResult.put("level", avgRate >= 70 ? "GOOD" : (avgRate >= 50 ? "MODERATE" : "WEAK"));
                kpAnalysis.add(kpResult);

                if (avgRate >= 70) strengths.add(kp.getName());
                else if (avgRate < 50) weaknesses.add(kp.getName());
            }

            String summary = generateExamSummary(exam, scores, avg, passCount, failCount);
            String suggestions = generateExamSuggestions(kpAnalysis, avg, passCount);

               Map<String, Object> score = new HashMap<>();
        score.put( "avgScore", avg);
        score.put( "highestScore", scores.isEmpty() ? 0 : scores.get(scores.size() - 1));
        score.put(  "lowestScore", scores.isEmpty() ? 0 : scores.get(0));
        score.put(  "passRate", grades.size() > 0 ? passCount * 100.0 / grades.size() : 0);
        score.put(   "passCount", passCount);
        score.put(  "excellentRate", grades.size() > 0 ? excellentCount * 100.0 / grades.size() : 0);
        score.put(   "excellentCount", excellentCount);
        score.put(   "failCount", failCount);

            aiAnalysisData.put("summary", summary);
            aiAnalysisData.put("strengths", strengths);
            aiAnalysisData.put("weaknesses", weaknesses);
            aiAnalysisData.put("suggestions", suggestions);
            aiAnalysisData.put("knowledgePointAnalysis", kpAnalysis);
            aiAnalysisData.put("scoreDistribution",score);

            // 存储到exam.ai_parsed_data
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            exam.setAiParsedData(objectMapper.writeValueAsString(aiAnalysisData));
            examRepository.save(exam);

            // 同步更新student_knowledge_mastery表
            updateStudentKnowledgeMastery(exam, grades, kps);

            // 存储到ai_analysis_report表
            saveAiAnalysisReport(exam, summary, suggestions, aiAnalysisData);

            return true;
        } catch (Exception e) {
            log.error("AI分析失败", e);
            return false;
        }
    }

    /**
     * 生成考试摘要
     */
    private String generateExamSummary(Exam exam, List<Double> scores, double avg, long passCount, long failCount) {
        StringBuilder sb = new StringBuilder();
        sb.append(exam.getName()).append("考试成绩分析：\n");
        sb.append("班级平均分").append(String.format("%.1f", avg)).append("分，");
        sb.append("及格率").append(String.format("%.1f", passCount * 100.0 / scores.size())).append("%。\n");
        
        if (failCount > scores.size() * 0.2) {
            sb.append("不及格人数较多，需要重点关注后进生。");
        } else if (avg >= 80) {
            sb.append("整体成绩优秀，教学效果良好。");
        } else if (avg >= 70) {
            sb.append("整体成绩良好，继续保持。");
        } else {
            sb.append("整体成绩有待提升，建议加强教学。");
        }
        
        return sb.toString();
    }

    /**
     * 生成考试建议
     */
    private String generateExamSuggestions(List<Map<String, Object>> kpAnalysis, double avg, long passCount) {
        List<String> weakKps = kpAnalysis.stream()
            .filter(k -> "WEAK".equals(k.get("level")))
            .map(k -> (String) k.get("knowledgePointName"))
            .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        if (!weakKps.isEmpty()) {
            sb.append("1. 针对薄弱知识点进行专项复习：").append(String.join("、", weakKps)).append("\n");
        }
        
        if (avg < 70) {
            sb.append("2. 建议放慢教学进度，加强基础知识巩固\n");
        }
        
        if (passCount * 100.0 / (kpAnalysis.size() > 0 ? 100 : 1) < 80) {
            sb.append("3. 组织试卷讲评课，分析典型错题\n");
        }
        
        sb.append("4. 对成绩波动较大的学生进行个别辅导");
        return sb.toString();
    }

    /**
     * 更新学生知识点掌握度
     */
    private void updateStudentKnowledgeMastery(Exam exam, List<ExamGrade> grades, 
                                                List<com.edu.domain.KnowledgePoint> kps) {
        // 这里可以根据考试题目与知识点的映射关系更新
        // 目前简化处理
        for (ExamGrade grade : grades) {
            Student student = grade.getStudent();
            for (com.edu.domain.KnowledgePoint kp : kps) {
                // 简化：根据成绩估算知识点掌握度
                Double scoreRate = grade.getScore().doubleValue() / exam.getFullScore() * 100;
                
                var existing = masteryRepository.findByStudentAndKnowledgePoint(student, kp);
                
                com.edu.domain.StudentKnowledgeMastery mastery;
                if (existing.isPresent()) {
                    mastery = existing.get();
                    double newLevel = mastery.getMasteryLevel().doubleValue() * 0.7 + scoreRate * 0.3;
                    mastery.setMasteryLevel(Math.min(newLevel, 100));
                } else {
                    mastery = new com.edu.domain.StudentKnowledgeMastery();
                    mastery.setStudent(student);
                    mastery.setKnowledgePoint(kp);
                    mastery.setMasteryLevel(scoreRate);
                }
                mastery.setLastExamScoreRate(BigDecimal.valueOf(scoreRate));
                mastery.setUpdatedAt(LocalDateTime.now());

                if (scoreRate < 50) mastery.setWeaknessLevel("SEVERE");
                else if (scoreRate < 60) mastery.setWeaknessLevel("MODERATE");
                else if (scoreRate < 70) mastery.setWeaknessLevel("MILD");
                else mastery.setWeaknessLevel("GOOD");

                masteryRepository.save(mastery);
            }
        }
    }

    /**
     * 保存AI分析报告
     */
    private void saveAiAnalysisReport(Exam exam, String summary, String suggestions, 
                                       Map<String, Object> analysisData) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
              
        // 获取当前学期
        Semester currentSemester = getCurrentSemester();
            com.edu.domain.AiAnalysisReport report = com.edu.domain.AiAnalysisReport.builder()
                .targetType("EXAM")
                .targetId(exam.getId())
                 .semester(currentSemester)  // 设置学期
                .reportType("EXAM_OVERALL")
                .analysisData(objectMapper.writeValueAsString(analysisData))
                .suggestions(suggestions)
                .summary(summary)
                .createdAt(LocalDateTime.now())
                .build();
            
            aiReportRepository.save(report);
        } catch (Exception e) {
            log.error("保存AI分析报告失败", e);
        }
    }
/**
 * 获取当前学期
 */
private Semester getCurrentSemester() {
    try {
        // 假设有一个 SemesterRepository
        return semesterRepository.findByIsCurrentTrue()
            .orElseGet(() -> {
                // 如果没有当前学期，创建一个
                Semester defaultSemester = new Semester();
                defaultSemester.setId(1L);
                defaultSemester.setName("2025-2026学年第2学期");
                return defaultSemester;
            });
    } catch (Exception e) {
        log.warn("获取当前学期失败，使用默认值");
        // 返回一个临时学期对象，只设置ID
        Semester tempSemester = new Semester();
        tempSemester.setId(1L);
        return tempSemester;
    }
}

    /**
     * 获取考试成绩字段映射配置
     */
    public List<FieldMapping> getExamGradeFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();

        // 学生（姓名或学号）
        FieldMapping studentName = new FieldMapping();
        studentName.setTargetField("studentName");
        studentName.setFieldDescription("学生姓名或学号");
        studentName.setPossibleNames(Arrays.asList("学生", "学生姓名", "姓名", "学号", "Student", "Student Name", "StudentNo"));
        studentName.setRequired(true);
        studentName.setDataType("string");
        studentName.setNeedExist(true);
        mappings.add(studentName);

        // 成绩
        FieldMapping score = new FieldMapping();
        score.setTargetField("score");
        score.setFieldDescription("考试成绩");
        score.setPossibleNames(Arrays.asList("成绩", "分数", "得分", "Score", "Grade"));
        score.setRequired(true);
        score.setDataType("number");
        mappings.add(score);

        // 备注
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
    private String buildErrorMessage(List<ValidationError> errors) {
        StringBuilder sb = new StringBuilder();
        for (ValidationError error : errors) {
            sb.append(error.getErrorMessage()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 知识点统计内部类
     */
    private static class KpStats {
        double totalScore = 0;
        int count = 0;
    }
}