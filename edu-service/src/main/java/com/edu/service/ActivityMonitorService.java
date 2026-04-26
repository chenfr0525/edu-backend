package com.edu.service;

import com.alibaba.fastjson.JSON;
import com.edu.domain.*;
import com.edu.domain.dto.*;
import com.edu.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityMonitorService {

    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final ActivityRecordRepository activityRecordRepository;
    private final SubmissionRepository submissionRepository;
    private final ExamGradeRepository examGradeRepository;
    private final DeepSeekService deepSeekService;
    private final ObjectMapper objectMapper;

    // ==================== 权限辅助方法 ====================

    private List<Long> getVisibleClassIds(Long userId, String userRole, Long requestClassId) {
        if ("ADMIN".equals(userRole)) {
            if (requestClassId != null) return Arrays.asList(requestClassId);
            return classRepository.findAll().stream()
                .map(ClassInfo::getId)
                .collect(Collectors.toList());
        } else {
            Teacher teacher = teacherRepository.findByUser(userRepository.findById(userId).orElse(null))
                .orElse(null);
            if (teacher == null) return new ArrayList<>();
            List<ClassInfo> teacherClasses = classRepository.findByTeacher(teacher);
            if (requestClassId != null) {
                boolean hasAccess = teacherClasses.stream().anyMatch(c -> c.getId().equals(requestClassId));
                return hasAccess ? Arrays.asList(requestClassId) : new ArrayList<>();
            }
            return teacherClasses.stream().map(ClassInfo::getId).collect(Collectors.toList());
        }
    }

    // ==================== 1. 获取学生活跃度列表 ====================

    @Transactional(readOnly = true)
    public ActivityListResponseVO getStudentActivityList(ActivityStudentListRequest request, 
                                                          Long currentUserId, String userRole) {
        List<Long> visibleClassIds = getVisibleClassIds(currentUserId, userRole, request.getClassId());
        if (visibleClassIds.isEmpty()) {
            return buildEmptyResponse();
        }
        
        Pageable pageable = PageRequest.of(
            request.getPage() != null ? request.getPage() : 0,
            request.getSize() != null ? request.getSize() : 10
        );
        
        // 查询学生列表
        Page<Student> studentPage;
        if (request.getKeyword() != null && !request.getKeyword().isEmpty()) {
            studentPage = studentRepository.findByClassIdsAndKeyword(visibleClassIds, request.getKeyword(), pageable);
        } else {
            studentPage = studentRepository.findByClassIds(visibleClassIds, pageable);
        }
        
        // 构建VO列表
        List<StudentActivityVO> records = studentPage.getContent().stream()
            .map(this::buildStudentActivityVO)
            .collect(Collectors.toList());
        
        // 统计总体数据
        ActivityOverallStatisticsVO overallStats = calculateOverallStats(visibleClassIds);
        
        return ActivityListResponseVO.builder()
            .records(records)
            .total(studentPage.getTotalElements())
            .current(request.getPage())
            .size(request.getSize())
            .pages(studentPage.getTotalPages())
            .overallStats(overallStats)
            .build();
    }

    private StudentActivityVO buildStudentActivityVO(Student student) {
        User user = student.getUser();
        Long studentId = student.getId();
        
        // 1. 登录次数（从users表的last_login_time统计，这里简化：统计最近30天）
        Integer loginCount = countStudentLogins(studentId);
        
        // 2. 作业提交次数
        Integer homeworkCount = (int) submissionRepository.countCompletedByStudent(studentId);
        
        // 3. 考试参与次数
        Integer examCount = examGradeRepository.findByStudent(student).size();
        
        // 4. 学习时长（从activity_record表统计）
        Integer studyDuration = getStudentStudyDuration(studentId);
        
        // 5. 资源访问次数（从activity_record表统计）
        Integer resourceAccessCount = getStudentResourceAccessCount(studentId);
        
        // 计算活跃度得分
        BigDecimal activityScore = calculateActivityScore(loginCount, homeworkCount, examCount, studyDuration, resourceAccessCount);
        String activityLevel = getActivityLevel(activityScore);
        
        return StudentActivityVO.builder()
            .studentId(student.getId())
            .studentNo(student.getStudentNo())
            .studentName(user != null ? user.getName() : "")
            .className(student.getClassInfo() != null ? student.getClassInfo().getName() : "")
            .classId(student.getClassInfo() != null ? student.getClassInfo().getId() : null)
            .loginCount(loginCount)
            .lastLoginTime(user != null ? user.getLastLoginTime() : null)
            .homeworkCount(homeworkCount)
            .examCount(examCount)
            .studyDuration(studyDuration)
            .resourceAccessCount(resourceAccessCount)
            .activityScore(activityScore)
            .activityLevel(activityLevel)
            .build();
    }

    private Integer countStudentLogins(Long studentId) {
        // 统计最近30天的登录次数
        // 简化：从users表的last_login_time判断是否登录过
        Optional<Student> studentOpt = studentRepository.findById(studentId);
        if (studentOpt.isPresent() && studentOpt.get().getUser() != null) {
            LocalDateTime lastLogin = studentOpt.get().getUser().getLastLoginTime();
            if (lastLogin != null && lastLogin.isAfter(LocalDateTime.now().minusDays(30))) {
                return 1; // 简化：有登录记录算1次
            }
        }
        return 0;
    }

    private Integer getStudentStudyDuration(Long studentId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<ActivityRecord> records = activityRecordRepository
            .findByStudentAndActivityDateBetween(
                studentRepository.findById(studentId).orElse(null),
                thirtyDaysAgo,
                LocalDateTime.now()
            );
        return records.stream().mapToInt(ActivityRecord::getStudyDuration).sum();
    }

    private Integer getStudentResourceAccessCount(Long studentId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<ActivityRecord> records = activityRecordRepository
            .findByStudentAndActivityDateBetween(
                studentRepository.findById(studentId).orElse(null),
                thirtyDaysAgo,
                LocalDateTime.now()
            );
        return records.stream().mapToInt(ActivityRecord::getResourceAccessCount).sum();
    }

    private BigDecimal calculateActivityScore(Integer loginCount, Integer homeworkCount, 
                                               Integer examCount, Integer studyDuration, 
                                               Integer resourceAccessCount) {
        // 各项满分
        int maxLogin = 10;
        int maxHomework = 20;
        int maxExam = 10;
        int maxDuration = 300; // 300分钟
        int maxResource = 50;
        
        // 计算得分
        double loginScore = Math.min(loginCount * 10.0, maxLogin);
        double homeworkScore = Math.min(homeworkCount * 5.0, maxHomework);
        double examScore = Math.min(examCount * 10.0, maxExam);
        double durationScore = Math.min(studyDuration * 1.0, maxDuration);
        double resourceScore = Math.min(resourceAccessCount * 2.0, maxResource);
        
        double total = loginScore + homeworkScore + examScore + durationScore + resourceScore;
        double maxTotal = maxLogin + maxHomework + maxExam + maxDuration + maxResource;
        
        return BigDecimal.valueOf(total / maxTotal * 100).setScale(2, RoundingMode.HALF_UP);
    }

    private String getActivityLevel(BigDecimal score) {
        double s = score.doubleValue();
        if (s >= 70) return "HIGH";
        if (s >= 50) return "MEDIUM";
        if (s >= 30) return "LOW";
        return "CRITICAL";
    }

    private ActivityOverallStatisticsVO calculateOverallStats(List<Long> classIds) {
        List<Student> students = studentRepository.findAllByClassIds(classIds);
        
        int totalStudents = students.size();
        int highCount = 0;
        int lowCount = 0;
        int criticalCount = 0;
        double totalScore = 0;
        
        for (Student student : students) {
            StudentActivityVO vo = buildStudentActivityVO(student);
            double score = vo.getActivityScore().doubleValue();
            totalScore += score;
            
            if (score >= 70) highCount++;
            else if (score < 50) lowCount++;
            if (score < 30) criticalCount++;
        }
        
        double avgScore = totalStudents > 0 ? totalScore / totalStudents : 0;
        
        return ActivityOverallStatisticsVO.builder()
            .totalStudents(totalStudents)
            .avgActivityScore(BigDecimal.valueOf(avgScore).setScale(2, RoundingMode.HALF_UP))
            .highActivityCount(highCount)
            .lowActivityCount(lowCount)
            .criticalCount(criticalCount)
            .build();
    }

    private ActivityListResponseVO buildEmptyResponse() {
        return ActivityListResponseVO.builder()
            .records(new ArrayList<>())
            .total(0L)
            .current(0)
            .size(10)
            .pages(0)
            .overallStats(ActivityOverallStatisticsVO.builder()
                .totalStudents(0)
                .avgActivityScore(BigDecimal.ZERO)
                .highActivityCount(0)
                .lowActivityCount(0)
                .criticalCount(0)
                .build())
            .build();
    }

    // ==================== 2. 获取学生活跃度详情 ====================

    @Transactional(readOnly = true)
    public StudentActivityDetailVO getStudentActivityDetail(Long studentId, StudentActivityDetailRequest request) {
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        User user = student.getUser();
        ClassInfo classInfo = student.getClassInfo();
        
        // 获取该班级所有学生（用于排名）
        List<Student> classStudents = classInfo != null ? 
            studentRepository.findByClassInfo(classInfo) : new ArrayList<>();
        
        // 统计卡片
        ActivityStatisticsVO statistics = buildStatisticsVO(student);
        
        // 趋势数据（近30天）
        List<ActivityTrendVO> trendData = getActivityTrend(student);
        
        // 活动类型分布
        List<ActivityTypeDistributionVO> typeDistribution = getActivityTypeDistribution(student);
        
        // 班级排名
        List<StudentActivityRankVO> classRanking = getClassRanking(student, classStudents);
        
        // 分析建议
        ActivitySuggestionVO suggestion = generateSuggestion(student, statistics, classRanking);
        
        return StudentActivityDetailVO.builder()
            .studentId(student.getId())
            .studentNo(student.getStudentNo())
            .studentName(user != null ? user.getName() : "")
            .className(classInfo != null ? classInfo.getName() : "")
            .classId(classInfo != null ? classInfo.getId() : null)
            .statistics(statistics)
            .trendData(trendData)
            .typeDistribution(typeDistribution)
            .classRanking(classRanking)
            .suggestion(suggestion)
            .build();
    }

    private ActivityStatisticsVO buildStatisticsVO(Student student) {
        Long studentId = student.getId();
        
        Integer loginCount = countStudentLogins(studentId);
        Integer homeworkCount = (int) submissionRepository.countCompletedByStudent(studentId);
        Integer examCount = examGradeRepository.findByStudent(student).size();
        Integer studyDuration = getStudentStudyDuration(studentId);
        Integer resourceCount = getStudentResourceAccessCount(studentId);
        BigDecimal activityScore = calculateActivityScore(loginCount, homeworkCount, examCount, studyDuration, resourceCount);
        
        // 计算班级平均分
        ClassInfo classInfo = student.getClassInfo();
        BigDecimal classAvg = BigDecimal.ZERO;
        if (classInfo != null) {
            List<Student> classStudents = studentRepository.findByClassInfo(classInfo);
            double total = 0;
            for (Student s : classStudents) {
                StudentActivityVO vo = buildStudentActivityVO(s);
                total += vo.getActivityScore().doubleValue();
            }
            classAvg = classStudents.isEmpty() ? BigDecimal.ZERO : 
                BigDecimal.valueOf(total / classStudents.size()).setScale(2, RoundingMode.HALF_UP);
        }
        
        String compareToClass = activityScore.compareTo(classAvg) >= 0 ?
            "高于班级平均" + activityScore.subtract(classAvg).setScale(2, RoundingMode.HALF_UP) + "分" :
            "低于班级平均" + classAvg.subtract(activityScore).setScale(2, RoundingMode.HALF_UP) + "分";
        
        return ActivityStatisticsVO.builder()
            .totalLoginCount(loginCount)
            .totalHomeworkCount(homeworkCount)
            .totalExamCount(examCount)
            .totalStudyDuration(studyDuration)
            .totalResourceCount(resourceCount)
            .avgActivityScore(activityScore)
            .activityLevel(getActivityLevel(activityScore))
            .compareToClass(compareToClass)
            .build();
    }

    private List<ActivityTrendVO> getActivityTrend(Student student) {
        List<ActivityTrendVO> trend = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        for (int i = 29; i >= 0; i--) {
            LocalDate date = now.minusDays(i).toLocalDate();
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(23, 59, 59);
            
            List<ActivityRecord> records = activityRecordRepository
                .findByStudentAndActivityDateBetween(student, start, end);
            
            int loginCount = 0;
            int homeworkCount = 0;
            int studyDuration = 0;
            int resourceCount = 0;
            BigDecimal activityScore = BigDecimal.ZERO;
            
            for (ActivityRecord record : records) {
                if ("LOGIN".equals(record.getType().toString())) loginCount++;
                if ("HOMEWORK".equals(record.getType().toString())) homeworkCount++;
                studyDuration += record.getStudyDuration() != null ? record.getStudyDuration() : 0;
                resourceCount += record.getResourceAccessCount() != null ? record.getResourceAccessCount() : 0;
            }
            
            // 计算当日活跃度
            activityScore = calculateActivityScore(loginCount, homeworkCount, 0, studyDuration, resourceCount);
            
            trend.add(ActivityTrendVO.builder()
                .date(date.toString())
                .loginCount(loginCount)
                .homeworkCount(homeworkCount)
                .studyDuration(studyDuration)
                .resourceCount(resourceCount)
                .activityScore(activityScore)
                .build());
        }
        
        return trend;
    }

    private List<ActivityTypeDistributionVO> getActivityTypeDistribution(Student student) {
        List<ActivityTypeDistributionVO> distribution = new ArrayList<>();
        
        Long studentId = student.getId();
        
        int loginCount = countStudentLogins(studentId);
        int homeworkCount = (int) submissionRepository.countCompletedByStudent(studentId);
        int examCount = examGradeRepository.findByStudent(student).size();
        int studyDuration = getStudentStudyDuration(studentId);
        int resourceCount = getStudentResourceAccessCount(studentId);
        
        int total = loginCount + homeworkCount + examCount + studyDuration + resourceCount;
        if (total == 0) total = 1;
        
        distribution.add(ActivityTypeDistributionVO.builder()
            .type("LOGIN")
            .typeName("登录")
            .count(loginCount)
            .percentage(BigDecimal.valueOf(loginCount * 100.0 / total).setScale(1, RoundingMode.HALF_UP))
            .build());
        
        distribution.add(ActivityTypeDistributionVO.builder()
            .type("HOMEWORK")
            .typeName("作业提交")
            .count(homeworkCount)
            .percentage(BigDecimal.valueOf(homeworkCount * 100.0 / total).setScale(1, RoundingMode.HALF_UP))
            .build());
        
        distribution.add(ActivityTypeDistributionVO.builder()
            .type("EXAM")
            .typeName("考试参与")
            .count(examCount)
            .percentage(BigDecimal.valueOf(examCount * 100.0 / total).setScale(1, RoundingMode.HALF_UP))
            .build());
        
        distribution.add(ActivityTypeDistributionVO.builder()
            .type("STUDY")
            .typeName("学习时长")
            .count(studyDuration)
            .percentage(BigDecimal.valueOf(studyDuration * 100.0 / total).setScale(1, RoundingMode.HALF_UP))
            .build());
        
        distribution.add(ActivityTypeDistributionVO.builder()
            .type("RESOURCE")
            .typeName("资源访问")
            .count(resourceCount)
            .percentage(BigDecimal.valueOf(resourceCount * 100.0 / total).setScale(1, RoundingMode.HALF_UP))
            .build());
        
        // 按数量排序
        distribution.sort((a, b) -> b.getCount().compareTo(a.getCount()));
        
        return distribution;
    }

    private List<StudentActivityRankVO> getClassRanking(Student student, List<Student> classStudents) {
        List<StudentActivityRankVO> ranking = new ArrayList<>();
        
        List<StudentActivityVO> activityList = new ArrayList<>();
        for (Student s : classStudents) {
            activityList.add(buildStudentActivityVO(s));
        }
        
        activityList.sort((a, b) -> b.getActivityScore().compareTo(a.getActivityScore()));
        
        int rank = 1;
        for (StudentActivityVO vo : activityList) {
            ranking.add(StudentActivityRankVO.builder()
                .rank(rank)
                .studentId(vo.getStudentId())
                .studentName(vo.getStudentName())
                .activityScore(vo.getActivityScore())
                .isCurrentStudent(vo.getStudentId().equals(student.getId()))
                .build());
            rank++;
        }
        
        return ranking;
    }

    private ActivitySuggestionVO generateSuggestion(Student student, ActivityStatisticsVO stats, 
                                                      List<StudentActivityRankVO> ranking) {
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        
        // 找出最高和最低
        String highestStudent = "";
        String lowestStudent = "";
        if (!ranking.isEmpty()) {
            highestStudent = ranking.get(0).getStudentName();
            lowestStudent = ranking.get(ranking.size() - 1).getStudentName();
        }
        
        // 分析优势
        if (stats.getTotalHomeworkCount() >= 10) {
            strengths.add("作业提交积极，已完成" + stats.getTotalHomeworkCount() + "次作业");
        }
        if (stats.getTotalLoginCount() >= 5) {
            strengths.add("登录频繁，学习主动性较强");
        }
        if (stats.getTotalStudyDuration() >= 200) {
            strengths.add("学习时长充足，累计" + stats.getTotalStudyDuration() + "分钟");
        }
        
        // 分析不足
        if (stats.getTotalHomeworkCount() < 5) {
            weaknesses.add("作业提交次数较少，仅" + stats.getTotalHomeworkCount() + "次");
            suggestions.add("建议督促按时完成作业");
        }
        if (stats.getTotalLoginCount() < 3) {
            weaknesses.add("登录频率低");
            suggestions.add("建议增加登录学习频率");
        }
        if (stats.getTotalStudyDuration() < 100) {
            weaknesses.add("学习时长不足");
            suggestions.add("建议每天安排固定学习时间");
        }
        if (stats.getTotalResourceCount() < 10) {
            weaknesses.add("资源访问次数少");
            suggestions.add("鼓励访问教学资源");
        }
        
        String summary = String.format("%s同学的活跃度得分为%.1f分，班级排名第%d。%s",
            student.getUser().getName(),
            stats.getAvgActivityScore().doubleValue(),
            ranking.stream().filter(r -> r.getIsCurrentStudent()).findFirst().map(StudentActivityRankVO::getRank).orElse(0),
            stats.getAvgActivityScore().doubleValue() >= 60 ? "整体表现良好，继续保持。" : "活跃度偏低，需要加强关注。");
        
        return ActivitySuggestionVO.builder()
            .summary(summary)
            .strengths(strengths.isEmpty() ? Arrays.asList("暂无突出表现") : strengths)
            .weaknesses(weaknesses.isEmpty() ? Arrays.asList("无明显不足") : weaknesses)
            .suggestions(suggestions.isEmpty() ? Arrays.asList("保持当前学习状态") : suggestions)
            .highestStudent(highestStudent)
            .lowestStudent(lowestStudent)
            .build();
    }

    // ==================== 3. 导入活跃度数据（AI解析） ====================

    public ParseResult parseActivityFile(String fileContent, String fileName, String activityType) {
        List<FieldMapping> mappings = getActivityFieldMappings(activityType);
        
        ParseResult result = deepSeekService.parseFileData(fileContent, fileName, "活跃度数据", mappings);
        
        // 补充活动类型
        if (result.getData() != null) {
            for (Map<String, Object> row : result.getData()) {
                row.put("activityType", activityType);
                // 计算活跃度得分
                row.put("activityScore", calculateActivityScoreFromRow(row, activityType));
            }
        }
        
        return ParseResult.builder()
            .success(result.isSuccess())
            .summary(result.getSummary())
            .data(result.getData())
            .columnMapping(result.getColumnMapping())
            .errors(result.getErrors())
            .build();
    }

    private List<FieldMapping> getActivityFieldMappings(String activityType) {
        List<FieldMapping> mappings = new ArrayList<>();
        
        // 学生字段（通用）
        FieldMapping studentField = new FieldMapping();
        studentField.setTargetField("studentName");
        studentField.setFieldDescription("学生姓名或学号");
        studentField.setPossibleNames(Arrays.asList("学生", "学生姓名", "姓名", "学号", "Student"));
        studentField.setRequired(true);
        studentField.setDataType("string");
        studentField.setNeedExist(true);
        mappings.add(studentField);
        
        // 活动日期
        FieldMapping dateField = new FieldMapping();
        dateField.setTargetField("activityDate");
        dateField.setFieldDescription("活动日期");
        dateField.setPossibleNames(Arrays.asList("日期", "活动日期", "Date", "日期"));
        dateField.setRequired(true);
        dateField.setDataType("string");
        mappings.add(dateField);
        
        // 根据不同类型添加特定字段
        if ("STUDY_DURATION".equals(activityType)) {
            FieldMapping durationField = new FieldMapping();
            durationField.setTargetField("studyDuration");
            durationField.setFieldDescription("学习时长（分钟）");
            durationField.setPossibleNames(Arrays.asList("学习时长", "时长", "分钟", "Duration"));
            durationField.setRequired(true);
            durationField.setDataType("number");
            mappings.add(durationField);
        } 
        else if ("RESOURCE".equals(activityType)) {
            FieldMapping resourceField = new FieldMapping();
            resourceField.setTargetField("resourceAccessCount");
            resourceField.setFieldDescription("资源访问次数");
            resourceField.setPossibleNames(Arrays.asList("访问次数", "资源访问", "次数", "Count"));
            resourceField.setRequired(true);
            resourceField.setDataType("number");
            mappings.add(resourceField);
        }
        
        // 描述（可选）
        FieldMapping descField = new FieldMapping();
        descField.setTargetField("description");
        descField.setFieldDescription("描述");
        descField.setPossibleNames(Arrays.asList("描述", "备注", "Description"));
        descField.setRequired(false);
        descField.setDataType("string");
        mappings.add(descField);
        
        return mappings;
    }

    private BigDecimal calculateActivityScoreFromRow(Map<String, Object> row, String activityType) {
        // 根据活动类型计算贡献分
        if ("STUDY_DURATION".equals(activityType)) {
            Integer duration = row.get("studyDuration") != null ? ((Number) row.get("studyDuration")).intValue() : 0;
            return BigDecimal.valueOf(Math.min(duration, 100)).setScale(2, RoundingMode.HALF_UP);
        } 
        else if ("RESOURCE".equals(activityType)) {
            Integer count = row.get("resourceAccessCount") != null ? ((Number) row.get("resourceAccessCount")).intValue() : 0;
            return BigDecimal.valueOf(Math.min(count * 2, 100)).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(50);
    }

    @Transactional
    public ActivityImportResultVO confirmActivityImport(List<Map<String, Object>> data) {
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();
        
        for (Map<String, Object> row : data) {
            try {
                insertActivityRecord(row);
                successCount++;
            } catch (Exception e) {
                failCount++;
                String errorMsg = String.format("导入失败 - 学生：%s，原因：%s",
                    row.get("studentName"), e.getMessage());
                log.error(errorMsg);
                errors.add(errorMsg);
            }
        }
        
        return ActivityImportResultVO.builder()
            .totalImported(data.size())
            .successCount(successCount)
            .failCount(failCount)
            .errors(errors)
            .success(failCount == 0)
            .message(String.format("导入完成！成功：%d条，失败：%d条", successCount, failCount))
            .build();
    }

    private void insertActivityRecord(Map<String, Object> row) {
        String studentName = (String) row.get("studentName");
        String activityDateStr = (String) row.get("activityDate");
        String activityType = (String) row.get("activityType");
        String description = (String) row.get("description");
        BigDecimal activityScore = (BigDecimal) row.get("activityScore");
        
        // 查找学生
        Student student = findStudentByIdentifier(studentName);
        if (student == null) {
            throw new RuntimeException("学生不存在: " + studentName);
        }
        
        LocalDateTime activityDate = parseDateTime(activityDateStr);
        
        ActivityRecord record = new ActivityRecord();
        record.setStudent(student);
        record.setType(ActivityStatus.valueOf(activityType));
        record.setDescription(description);
        record.setActivityDate(activityDate);
        record.setActivityScore(activityScore);
        
        if ("STUDY_DURATION".equals(activityType)) {
            Integer duration = row.get("studyDuration") != null ? ((Number) row.get("studyDuration")).intValue() : 0;
            record.setStudyDuration(duration);
        } else if ("RESOURCE".equals(activityType)) {
            Integer count = row.get("resourceAccessCount") != null ? ((Number) row.get("resourceAccessCount")).intValue() : 0;
            record.setResourceAccessCount(count);
        }
        
        activityRecordRepository.save(record);
    }

    // ==================== 4. 统计卡片 ====================

    @Transactional(readOnly = true)
    public ActivityOverallStatisticsVO getActivityStatistics(Long classId, Long currentUserId, String userRole) {
        List<Long> visibleClassIds = getVisibleClassIds(currentUserId, userRole, classId);
        if (visibleClassIds.isEmpty()) {
            return ActivityOverallStatisticsVO.builder()
                .totalStudents(0)
                .avgActivityScore(BigDecimal.ZERO)
                .highActivityCount(0)
                .lowActivityCount(0)
                .criticalCount(0)
                .build();
        }
        
        return calculateOverallStats(visibleClassIds);
    }

    // ==================== 5. ECharts图表数据 ====================

    @Transactional(readOnly = true)
    public ActivityChartDataVO getChartData(Long classId, Long currentUserId, String userRole) {
        List<Long> visibleClassIds = getVisibleClassIds(currentUserId, userRole, classId);
        if (visibleClassIds.isEmpty()) {
            return ActivityChartDataVO.builder()
                .activityRanking(new ArrayList<>())
                .lowActivityWarnings(new ArrayList<>())
                .classComparison(new ArrayList<>())
                .trendData(new ArrayList<>())
                .build();
        }
        
        List<Student> students = studentRepository.findAllByClassIds(visibleClassIds);
        
        // 活跃度排行榜（Top10）
        List<RankingItem> activityRanking = getActivityRanking(students);
        
        // 低活跃度预警
        List<WarningItem> lowActivityWarnings = getLowActivityWarnings(students);
        
        // 各班级对比
        List<ClassActivityVO> classComparison = getClassComparison(visibleClassIds);
        
        // 趋势数据
        List<TrendItem> trendData = getOverallTrend(visibleClassIds);
        
        return ActivityChartDataVO.builder()
            .activityRanking(activityRanking)
            .lowActivityWarnings(lowActivityWarnings)
            .classComparison(classComparison)
            .trendData(trendData)
            .build();
    }

    private List<RankingItem> getActivityRanking(List<Student> students) {
        List<StudentActivityVO> activityList = new ArrayList<>();
        for (Student student : students) {
            activityList.add(buildStudentActivityVO(student));
        }
        
        activityList.sort((a, b) -> b.getActivityScore().compareTo(a.getActivityScore()));
        
        List<RankingItem> ranking = new ArrayList<>();
        int rank = 1;
        for (int i = 0; i < Math.min(10, activityList.size()); i++) {
            StudentActivityVO vo = activityList.get(i);
            ranking.add(RankingItem.builder()
                .rank(rank)
                .studentId(vo.getStudentId())
                .studentName(vo.getStudentName())
                .className(vo.getClassName())
                .activityScore(vo.getActivityScore())
                .loginCount(vo.getLoginCount())
                .studyDuration(vo.getStudyDuration())
                .build());
            rank++;
        }
        
        return ranking;
    }

    private List<WarningItem> getLowActivityWarnings(List<Student> students) {
        List<WarningItem> warnings = new ArrayList<>();
        
        for (Student student : students) {
            StudentActivityVO vo = buildStudentActivityVO(student);
            double score = vo.getActivityScore().doubleValue();
            
            if (score < 50) {
                String warningLevel = score < 30 ? "CRITICAL" : "WARNING";
                String warningReason = score < 30 ? 
                    "活跃度极低，超过30天无有效学习活动" :
                    "活跃度偏低，学习参与度不足";
                String suggestion = score < 30 ?
                    "建议立即与学生沟通，了解学习状况" :
                    "建议增加学习提醒和督促";
                
                warnings.add(WarningItem.builder()
                    .studentId(vo.getStudentId())
                    .studentName(vo.getStudentName())
                    .className(vo.getClassName())
                    .activityScore(vo.getActivityScore())
                    .warningLevel(warningLevel)
                    .warningReason(warningReason)
                    .suggestion(suggestion)
                    .build());
            }
        }
        
        // 按得分升序排序（最不活跃的在前）
        warnings.sort((a, b) -> a.getActivityScore().compareTo(b.getActivityScore()));
        
        return warnings;
    }

    private List<ClassActivityVO> getClassComparison(List<Long> classIds) {
        List<ClassActivityVO> comparison = new ArrayList<>();
        
        for (Long classId : classIds) {
            ClassInfo classInfo = classRepository.findById(classId).orElse(null);
            if (classInfo == null) continue;
            
            List<Student> students = studentRepository.findByClassInfo(classInfo);
            if (students.isEmpty()) continue;
            
            double totalScore = 0;
            int highCount = 0;
            int lowCount = 0;
            
            for (Student student : students) {
                StudentActivityVO vo = buildStudentActivityVO(student);
                double score = vo.getActivityScore().doubleValue();
                totalScore += score;
                if (score >= 70) highCount++;
                if (score < 50) lowCount++;
            }
            
            double avgScore = totalScore / students.size();
            
            comparison.add(ClassActivityVO.builder()
                .classId(classId)
                .className(classInfo.getName())
                .avgActivityScore(BigDecimal.valueOf(avgScore).setScale(2, RoundingMode.HALF_UP))
                .studentCount(students.size())
                .highActivityCount(highCount)
                .lowActivityCount(lowCount)
                .build());
        }
        
        // 按平均活跃度降序排序
        comparison.sort((a, b) -> b.getAvgActivityScore().compareTo(a.getAvgActivityScore()));
        
        return comparison;
    }

    private List<TrendItem> getOverallTrend(List<Long> classIds) {
        List<TrendItem> trend = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        for (int i = 6; i >= 0; i--) {
            LocalDate date = now.minusDays(i).toLocalDate();
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(23, 59, 59);
            
            // 查询该日期所有班级的活动记录
            List<ActivityRecord> records = new ArrayList<>();
            for (Long classId : classIds) {
                
                 List<ActivityRecord> classRecords = activityRecordRepository
            .findByClassIdAndDateBetween(classId, start, end);
                // List<ActivityRecord> classRecords = activityRecordRepository.findByClassIdAndDate(classId, date);
                records.addAll(classRecords);
            }
            
            int totalLogin = 0;
            int totalStudyDuration = 0;
            double totalScore = 0;
            int studentCount = 0;
            
            // 按学生分组统计
            Map<Long, List<ActivityRecord>> studentRecords = records.stream()
                .collect(Collectors.groupingBy(r -> r.getStudent().getId()));
            
            for (Map.Entry<Long, List<ActivityRecord>> entry : studentRecords.entrySet()) {
                int loginCount = 0;
                int studyDuration = 0;
                for (ActivityRecord r : entry.getValue()) {
                    if ("LOGIN".equals(r.getType().toString())) loginCount++;
                    studyDuration += r.getStudyDuration() != null ? r.getStudyDuration() : 0;
                }
                totalLogin += loginCount;
                totalStudyDuration += studyDuration;
                
                // 计算当日活跃度得分
                BigDecimal score = calculateActivityScore(loginCount, 0, 0, studyDuration, 0);
                totalScore += score.doubleValue();
                studentCount++;
            }
            
            double avgScore = studentCount > 0 ? totalScore / studentCount : 0;
            
            trend.add(TrendItem.builder()
                .date(date.toString())
                .avgActivityScore(BigDecimal.valueOf(avgScore).setScale(2, RoundingMode.HALF_UP))
                .totalLoginCount(totalLogin)
                .totalStudyDuration(totalStudyDuration)
                .build());
        }
        
        return trend;
    }

    // ==================== 辅助方法 ====================

    private Student findStudentByIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) return null;
        
        String trimmedIdentifier = identifier.trim();
        
        Optional<Student> studentByNo = studentRepository.findByStudentNo(trimmedIdentifier);
        if (studentByNo.isPresent()) return studentByNo.get();
        
        Optional<User> userByUsername = userRepository.findByUsername(trimmedIdentifier);
        if (userByUsername.isPresent()) {
            User user = userByUsername.get();
            if (user.getRole() == Role.STUDENT) {
                return studentRepository.findByUser(user).orElse(null);
            }
        }
        
        List<User> usersByName = userRepository.findByName(trimmedIdentifier);
        for (User user : usersByName) {
            if (user.getRole() == Role.STUDENT) {
                Optional<Student> student = studentRepository.findByUser(user);
                if (student.isPresent()) return student.get();
            }
        }
        
        return null;
    }

    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null) throw new RuntimeException("日期不能为空");
        try {
            return LocalDate.parse(dateStr).atStartOfDay();
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(dateStr);
            } catch (Exception e2) {
                throw new RuntimeException("日期格式无效: " + dateStr);
            }
        }
    }
}