package com.edu.service;

import com.alibaba.fastjson.JSON;
import com.edu.domain.*;
import com.edu.domain.dto.*;
import com.edu.domain.dto.CourseChartDataVO.ComparisonItem;
import com.edu.domain.dto.CourseChartDataVO.RadarChartData;
import com.edu.domain.dto.CourseChartDataVO.ScoreDistributionPie;
import com.edu.domain.dto.CourseChartDataVO.ScoreTrendItem;
import com.edu.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseAnalysisService {

    private final CourseRepository courseRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ExamGradeRepository examGradeRepository;
    private final HomeworkRepository homeworkRepository;
    private final SubmissionRepository submissionRepository;
    private final StudentKnowledgeMasteryRepository masteryRepository;
    private final KnowledgePointScoreDetailRepository kpScoreDetailRepository;
    private final AiAnalysisReportRepository aiReportRepository;
    private final DeepSeekService deepSeekService;
    private final ObjectMapper objectMapper;
    private final ExamRepository examRepository;
    private final UnifiedAiAnalysisService unifiedAiAnalysisService;

    // ==================== 权限辅助方法 ====================

    private List<Long> getVisibleCourseIds(Long userId, String userRole, Long requestCourseId) {
        if ("ADMIN".equals(userRole)) {
            if (requestCourseId != null) return Arrays.asList(requestCourseId);
            return courseRepository.findAll().stream()
                .map(Course::getId)
                .collect(Collectors.toList());
        } else {
            Teacher teacher = teacherRepository.findByUser(userRepository.findById(userId).orElse(null))
                .orElse(null);
            if (teacher == null) return new ArrayList<>();
            List<Course> teacherCourses = courseRepository.findByTeacher(teacher);
            if (requestCourseId != null) {
                boolean hasAccess = teacherCourses.stream().anyMatch(c -> c.getId().equals(requestCourseId));
                return hasAccess ? Arrays.asList(requestCourseId) : new ArrayList<>();
            }
            return teacherCourses.stream().map(Course::getId).collect(Collectors.toList());
        }
    }

    // ==================== 1. 课程列表 ====================

    @Transactional(readOnly = true)
    public List<CourseListVO> getCourseList(Long currentUserId, String userRole) {
        List<Long> visibleCourseIds = getVisibleCourseIds(currentUserId, userRole, null);
        if (visibleCourseIds.isEmpty()) return new ArrayList<>();
        
        List<Course> courses = courseRepository.findAllById(visibleCourseIds);
        
        return courses.stream()
            .map(this::convertToCourseListVO)
            .collect(Collectors.toList());
    }

    private CourseListVO convertToCourseListVO(Course course) {
        Teacher teacher = course.getTeacher();
        User teacherUser = teacher != null ? teacher.getUser() : null;
        
        int studentCount = (int) enrollmentRepository.countByCourse(course);
        int knowledgePointCount = knowledgePointRepository.findByCourse(course).size();
        
        return CourseListVO.builder()
            .id(course.getId())
            .name(course.getName())
            .description(course.getDescription())
            .icon(course.getIcon())
            .teacherName(teacherUser != null ? teacherUser.getName() : "")
            .teacherId(teacher != null ? teacher.getId() : null)
            .credit(course.getCredit())
            .status(course.getStatus().toString())
            .statusText(getCourseStatusText(course.getStatus()))
            .studentCount(studentCount)
            .knowledgePointCount(knowledgePointCount)
            .createdAt(course.getCreatedAt())
            .build();
    }

    // ==================== 2. 课程统计卡片 ====================

    @Transactional(readOnly = true)
    public CourseStatisticsVO getCourseStatistics(Long courseId, Long currentUserId, String userRole) {
        List<Long> visibleCourseIds = getVisibleCourseIds(currentUserId, userRole, courseId);
        if (visibleCourseIds.isEmpty()) {
            throw new RuntimeException("无权限访问该课程");
        }
        
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        
        // 选课学生数
        int studentCount = (int) enrollmentRepository.countByCourse(course);
        
        // 平均成绩（从考试成绩计算）
        Double avgScore = examGradeRepository.getStudentAvgScoreByCourse(courseId);
        if (avgScore == null) avgScore = 0.0;
        
        // 及格率
        Double passRate = calculateCoursePassRate(courseId);
        
        // 知识点数量
        int knowledgePointCount = knowledgePointRepository.findByCourse(course).size();
        
        return CourseStatisticsVO.builder()
            .studentCount(studentCount)
            .avgScore(BigDecimal.valueOf(avgScore).setScale(2, RoundingMode.HALF_UP))
            .passRate(BigDecimal.valueOf(passRate).setScale(2, RoundingMode.HALF_UP))
            .knowledgePointCount(knowledgePointCount)
            .build();
    }

    private Double calculateCoursePassRate(Long courseId) {
        List<ExamGrade> grades = examGradeRepository.findByCourseId(courseId);
        if (grades.isEmpty()) return 0.0;
        
        // 获取该课程的所有考试，取及格分（简化：默认60分及格）
        long passCount = grades.stream()
            .filter(g -> g.getScore() != null && g.getScore() >= 60)
            .count();
        
        return passCount * 100.0 / grades.size();
    }

    // ==================== 3. 课程详情 ====================

    @Transactional(readOnly = true)
    public CourseDetailVO getCourseDetail(Long courseId, Long currentUserId, String userRole) {
        List<Long> visibleCourseIds = getVisibleCourseIds(currentUserId, userRole, courseId);
        if (visibleCourseIds.isEmpty()) {
            throw new RuntimeException("无权限访问该课程");
        }
        
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        
        Teacher teacher = course.getTeacher();
        User teacherUser = teacher != null ? teacher.getUser() : null;
        
        CourseStatisticsVO statistics = getCourseStatistics(courseId, currentUserId, userRole);
        
        return CourseDetailVO.builder()
            .id(course.getId())
            .name(course.getName())
            .description(course.getDescription())
            .icon(course.getIcon())
            .teacherName(teacherUser != null ? teacherUser.getName() : "")
            .teacherId(teacher != null ? teacher.getId() : null)
            .credit(course.getCredit())
            .status(course.getStatus().toString())
            .createdAt(course.getCreatedAt())
            .statistics(statistics)
            .build();
    }

    // ==================== 4. 课程管理（管理员） ====================

    @Transactional
    public Course createCourse(CourseCreateRequest request) {
        Teacher teacher = teacherRepository.findById(request.getTeacherId())
            .orElseThrow(() -> new RuntimeException("教师不存在"));
        
        Course course = new Course();
        course.setName(request.getName());
        course.setDescription(request.getDescription());
        course.setIcon(request.getIcon());
        course.setTeacher(teacher);
        course.setCredit(request.getCredit() != null ? request.getCredit() : 2);
        course.setStatus(CourseStatus.valueOf(request.getStatus()));
        
        return courseRepository.save(course);
    }

    @Transactional
    public Course updateCourse(Long courseId, CourseUpdateRequest request) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        
        if (request.getName() != null) course.setName(request.getName());
        if (request.getDescription() != null) course.setDescription(request.getDescription());
        if (request.getIcon() != null) course.setIcon(request.getIcon());
        if (request.getTeacherId() != null) {
            Teacher teacher = teacherRepository.findById(request.getTeacherId())
                .orElseThrow(() -> new RuntimeException("教师不存在"));
            course.setTeacher(teacher);
        }
        if (request.getCredit() != null) course.setCredit(request.getCredit());
        if (request.getStatus() != null) course.setStatus(CourseStatus.valueOf(request.getStatus()));
        
        return courseRepository.save(course);
    }

    @Transactional
    public void deleteCourse(Long courseId) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        
        // 先删除关联的知识点
        List<KnowledgePoint> kps = knowledgePointRepository.findByCourse(course);
        knowledgePointRepository.deleteAll(kps);
        
        courseRepository.delete(course);
    }

    // ==================== 5. 知识点管理 ====================

    @Transactional(readOnly = true)
    public List<KnowledgePointVO> getKnowledgePoints(Long courseId, Long currentUserId, String userRole) {
        List<Long> visibleCourseIds = getVisibleCourseIds(currentUserId, userRole, courseId);
        if (visibleCourseIds.isEmpty()) {
            throw new RuntimeException("无权限访问该课程");
        }
        
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        
        List<KnowledgePoint> allKps = knowledgePointRepository.findByCourseOrderBySortOrderAsc(course);
        
        // 构建树形结构
        List<KnowledgePointVO> rootList = new ArrayList<>();
        Map<Long, KnowledgePointVO> kpMap = new HashMap<>();
        
        for (KnowledgePoint kp : allKps) {
            KnowledgePointVO vo = convertToKnowledgePointVO(kp, courseId);
            kpMap.put(kp.getId(), vo);
        }
        
        for (KnowledgePoint kp : allKps) {
            KnowledgePointVO vo = kpMap.get(kp.getId());
            if (kp.getParent() == null) {
                rootList.add(vo);
            } else {
                KnowledgePointVO parent = kpMap.get(kp.getParent().getId());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(vo);
                }
            }
        }
        
        return rootList;
    }

    private KnowledgePointVO convertToKnowledgePointVO(KnowledgePoint kp, Long courseId) {
    // 从 knowledge_point_score_detail 表获取班级平均掌握度
    // 需要班级ID，这里如果无法获取则返回0
    Double avgMastery = 0.0;
    
    // 尝试获取该课程下所有学生的班级（简化：取第一个班级）
    List<Student> students = studentRepository.findByEnrollmentsCourse(courseId != null ? 
        courseRepository.findById(courseId).orElse(null) : null);
    if (students != null && !students.isEmpty() && students.get(0).getClassInfo() != null) {
        Long classId = students.get(0).getClassInfo().getId();
        BigDecimal classAvg = kpScoreDetailRepository.getClassAvgScoreRate(kp.getId(), classId);
        if (classAvg != null) {
            avgMastery = classAvg.doubleValue();
        }
    }
         // 统计子知识点数量
    int childCount = knowledgePointRepository.findByParentId(kp.getId()).size();
    
    // 判断薄弱程度
    String weaknessLevel = getWeaknessLevel(avgMastery);
    
    return KnowledgePointVO.builder()
        .id(kp.getId())
        .name(kp.getName())
        .description(kp.getDescription())
        .parentId(kp.getParent() != null ? kp.getParent().getId() : null)
        .parentName(kp.getParent() != null ? kp.getParent().getName() : null)
        .level(kp.getLevel())
        .sortOrder(kp.getSortOrder())
        .childCount(childCount)
        .classAvgMastery(BigDecimal.valueOf(avgMastery).setScale(2, RoundingMode.HALF_UP))
        .weaknessLevel(weaknessLevel)
        .children(new ArrayList<>())
        .build();
}

    @Transactional
    public KnowledgePoint createKnowledgePoint(KnowledgePointCreateRequest request) {
        Course course = courseRepository.findById(request.getCourseId())
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        
        KnowledgePoint parent = null;
        if (request.getParentId() != null) {
            parent = knowledgePointRepository.findById(request.getParentId())
                .orElse(null);
        }
        
        KnowledgePoint kp = new KnowledgePoint();
        kp.setName(request.getName());
        kp.setDescription(request.getDescription());
        kp.setCourse(course);
        kp.setParent(parent);
        kp.setLevel(request.getLevel() != null ? request.getLevel() : 0);
        kp.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        
        return knowledgePointRepository.save(kp);
    }

    @Transactional
    public KnowledgePoint updateKnowledgePoint(Long kpId, KnowledgePointUpdateRequest request) {
        KnowledgePoint kp = knowledgePointRepository.findById(kpId)
            .orElseThrow(() -> new RuntimeException("知识点不存在"));
        
        if (request.getName() != null) kp.setName(request.getName());
        if (request.getDescription() != null) kp.setDescription(request.getDescription());
        if (request.getParentId() != null) {
            KnowledgePoint parent = knowledgePointRepository.findById(request.getParentId())
                .orElse(null);
            kp.setParent(parent);
        }
        if (request.getLevel() != null) kp.setLevel(request.getLevel());
        if (request.getSortOrder() != null) kp.setSortOrder(request.getSortOrder());
        
        return knowledgePointRepository.save(kp);
    }

    @Transactional
    public void deleteKnowledgePoint(Long kpId) {
        KnowledgePoint kp = knowledgePointRepository.findById(kpId)
            .orElseThrow(() -> new RuntimeException("知识点不存在"));
        
        // 递归删除子知识点
        List<KnowledgePoint> children = knowledgePointRepository.findByParentId(kpId);
        knowledgePointRepository.deleteAll(children);
        
        knowledgePointRepository.delete(kp);
    }

    // ==================== 6. AI解析知识点文件 ====================

    public ParseResult parseKnowledgePointFile(String fileContent, String fileName, Long courseId) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        
        List<FieldMapping> mappings = getKnowledgePointFieldMappings();
        
        ParseResult result = deepSeekService.parseFileData(fileContent, fileName, "知识点", mappings);
        
        // 补充课程信息
        if (result.getData() != null) {
            for (Map<String, Object> row : result.getData()) {
                row.put("courseId", courseId);
                row.put("courseName", course.getName());
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

    private List<FieldMapping> getKnowledgePointFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();
        
        FieldMapping name = new FieldMapping();
        name.setTargetField("name");
        name.setFieldDescription("知识点名称");
        name.setPossibleNames(Arrays.asList("知识点名称", "名称", "知识点名", "Name"));
        name.setRequired(true);
        name.setDataType("string");
        mappings.add(name);
        
        FieldMapping description = new FieldMapping();
        description.setTargetField("description");
        description.setFieldDescription("知识点描述");
        description.setPossibleNames(Arrays.asList("描述", "说明", "Description"));
        description.setRequired(false);
        description.setDataType("string");
        mappings.add(description);
        
        FieldMapping parentName = new FieldMapping();
        parentName.setTargetField("parentName");
        parentName.setFieldDescription("父知识点名称");
        parentName.setPossibleNames(Arrays.asList("父知识点", "所属知识点", "父级", "Parent"));
        parentName.setRequired(false);
        parentName.setDataType("string");
        mappings.add(parentName);
        
        FieldMapping level = new FieldMapping();
        level.setTargetField("level");
        level.setFieldDescription("层级");
        level.setPossibleNames(Arrays.asList("层级", "级别", "Level"));
        level.setRequired(false);
        level.setDataType("number");
        mappings.add(level);
        
        FieldMapping sortOrder = new FieldMapping();
        sortOrder.setTargetField("sortOrder");
        sortOrder.setFieldDescription("排序");
        sortOrder.setPossibleNames(Arrays.asList("排序", "顺序", "Sort Order"));
        sortOrder.setRequired(false);
        sortOrder.setDataType("number");
        mappings.add(sortOrder);
        
        return mappings;
    }

    @Transactional
    public KnowledgePointImportResultVO confirmKnowledgePointImport(Long courseId, List<Map<String, Object>> data) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();
        
        // 建立名称到知识点的映射（用于查找父知识点）
        Map<String, KnowledgePoint> nameToKpMap = new HashMap<>();
        List<KnowledgePoint> existingKps = knowledgePointRepository.findByCourse(course);
        for (KnowledgePoint kp : existingKps) {
            nameToKpMap.put(kp.getName(), kp);
        }
        
        for (Map<String, Object> row : data) {
            try {
                insertSingleKnowledgePoint(course, row, nameToKpMap);
                successCount++;
            } catch (Exception e) {
                failCount++;
                String errorMsg = String.format("导入失败 - 知识点：%s，原因：%s",
                    row.get("name"), e.getMessage());
                log.error(errorMsg);
                errors.add(errorMsg);
            }
        }
        
        return KnowledgePointImportResultVO.builder()
            .totalImported(data.size())
            .successCount(successCount)
            .failCount(failCount)
            .errors(errors)
            .success(failCount == 0)
            .message(String.format("导入完成！成功：%d条，失败：%d条", successCount, failCount))
            .build();
    }

    private void insertSingleKnowledgePoint(Course course, Map<String, Object> row, 
                                              Map<String, KnowledgePoint> nameToKpMap) {
        String name = (String) row.get("name");
        String description = (String) row.get("description");
        String parentName = (String) row.get("parentName");
        Integer level = row.get("level") != null ? ((Number) row.get("level")).intValue() : 0;
        Integer sortOrder = row.get("sortOrder") != null ? ((Number) row.get("sortOrder")).intValue() : 0;
        
        // 查找父知识点
        KnowledgePoint parent = null;
        if (parentName != null && !parentName.trim().isEmpty()) {
            parent = nameToKpMap.get(parentName);
            if (parent == null) {
                // 尝试创建父知识点
                parent = new KnowledgePoint();
                parent.setName(parentName);
                parent.setCourse(course);
                parent.setLevel(level - 1);
                parent.setSortOrder(0);
                parent = knowledgePointRepository.save(parent);
                nameToKpMap.put(parentName, parent);
            }
        }
        
        KnowledgePoint kp = new KnowledgePoint();
        kp.setName(name);
        kp.setDescription(description);
        kp.setCourse(course);
        kp.setParent(parent);
        kp.setLevel(level);
        kp.setSortOrder(sortOrder);
        
        knowledgePointRepository.save(kp);
        nameToKpMap.put(name, kp);
    }

    // ==================== 7. 知识点详情分析 ====================

    @Transactional(readOnly = true)
    public KnowledgePointDetailVO getKnowledgePointDetail(Long courseId, Long kpId, 
                                                           Long currentUserId, String userRole) {
        List<Long> visibleCourseIds = getVisibleCourseIds(currentUserId, userRole, courseId);
        if (visibleCourseIds.isEmpty()) {
            throw new RuntimeException("无权限访问该课程");
        }
        
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        
        KnowledgePoint kp = knowledgePointRepository.findById(kpId)
            .orElseThrow(() -> new RuntimeException("知识点不存在"));
        
        // 获取该课程的所有学生
        List<Student> students = studentRepository.findByEnrollmentsCourse(course);
        
        // 统计掌握度
        KnowledgePointStatsVO stats = calculateKnowledgePointStats(kp, students);
        
        // 学生掌握度列表
        List<StudentMasteryVO> studentMasteryList = getStudentMasteryList(kp, students);
        
        // 历史趋势
        List<MasteryTrendVO> masteryTrend = getMasteryTrend(kp);
        
        // 教学建议
        String teachingSuggestion = generateTeachingSuggestion(stats);
        
        return KnowledgePointDetailVO.builder()
            .id(kp.getId())
            .name(kp.getName())
            .description(kp.getDescription())
            .courseId(courseId)
            .courseName(course.getName())
            .parentId(kp.getParent() != null ? kp.getParent().getId() : null)
            .parentName(kp.getParent() != null ? kp.getParent().getName() : null)
            .level(kp.getLevel())
            .sortOrder(kp.getSortOrder())
            .stats(stats)
            .studentMasteryList(studentMasteryList)
            .masteryTrend(masteryTrend)
            .teachingSuggestion(teachingSuggestion)
            .build();
    }

   private KnowledgePointStatsVO calculateKnowledgePointStats(KnowledgePoint kp, List<Student> students) {
    if (students == null || students.isEmpty()) {
        return KnowledgePointStatsVO.builder()
            .classAvgMastery(BigDecimal.ZERO)
            .highestMastery(BigDecimal.ZERO)
            .lowestMastery(BigDecimal.ZERO)
            .masteredCount(0)
            .weakCount(0)
            .totalStudents(0)
            .build();
    }
        
       Long classId = students.get(0).getClassInfo() != null ? students.get(0).getClassInfo().getId() : null;
    BigDecimal classAvgMastery = BigDecimal.ZERO;
    if (classId != null) {
        classAvgMastery = kpScoreDetailRepository.getClassAvgScoreRate(kp.getId(), classId);
        if (classAvgMastery == null) classAvgMastery = BigDecimal.ZERO;
    }
    
    // 获取学生的掌握度列表
    List<Double> masteries = new ArrayList<>();
    int masteredCount = 0;
    int weakCount = 0;

     for (Student student : students) {
        // 从 student_knowledge_mastery 表获取
        Double mastery = masteryRepository.getStudentKnowledgeMastery(student.getId(), kp.getId());
        if (mastery != null) {
            masteries.add(mastery);
            if (mastery >= 70) masteredCount++;
            if (mastery < 50) weakCount++;
        }
    }

    double highest = masteries.stream().mapToDouble(Double::doubleValue).max().orElse(0);
    double lowest = masteries.stream().mapToDouble(Double::doubleValue).min().orElse(0);
    
    return KnowledgePointStatsVO.builder()
        .classAvgMastery(classAvgMastery)
        .highestMastery(BigDecimal.valueOf(highest).setScale(2, RoundingMode.HALF_UP))
        .lowestMastery(BigDecimal.valueOf(lowest).setScale(2, RoundingMode.HALF_UP))
        .masteredCount(masteredCount)
        .weakCount(weakCount)
        .totalStudents(students.size())
        .build();
}

    private List<StudentMasteryVO> getStudentMasteryList(KnowledgePoint kp, List<Student> students) {
        List<StudentMasteryVO> result = new ArrayList<>();
        
        for (Student student : students) {
            Double mastery = masteryRepository.getStudentKnowledgeMastery(student.getId(), kp.getId());
            User user = student.getUser();
            
            result.add(StudentMasteryVO.builder()
                .studentId(student.getId())
                .studentNo(student.getStudentNo())
                .studentName(user != null ? user.getName() : "")
                .masteryLevel(mastery != null ? BigDecimal.valueOf(mastery) : BigDecimal.ZERO)
                .weaknessLevel(getWeaknessLevel(mastery != null ? mastery : 0))
                .lastUpdateTime(LocalDateTime.now())
                .build());
        }
        
        // 按掌握度排序（从高到低）
        result.sort((a, b) -> b.getMasteryLevel().compareTo(a.getMasteryLevel()));
        
        return result;
    }

    private List<MasteryTrendVO> getMasteryTrend(KnowledgePoint kp) {
        List<MasteryTrendVO> trend = new ArrayList<>();
        
        // 从知识点得分明细表获取历史数据
        List<KnowledgePointScoreDetail> details = kpScoreDetailRepository.findAll();
        
        // 按创建时间分组
        Map<String, List<KnowledgePointScoreDetail>> groupedByDate = new LinkedHashMap<>();
        
        for (KnowledgePointScoreDetail detail : details) {
            if (detail.getKnowledgePoint().getId().equals(kp.getId())) {
                String dateKey = detail.getCreatedAt().toLocalDate().toString();
                groupedByDate.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(detail);
            }
        }
        
        for (Map.Entry<String, List<KnowledgePointScoreDetail>> entry : groupedByDate.entrySet()) {
            double avgRate = entry.getValue().stream()
                .mapToDouble(d -> d.getScoreRate().doubleValue())
                .average()
                .orElse(0);
            
            trend.add(MasteryTrendVO.builder()
                .date(entry.getKey())
                .masteryLevel(BigDecimal.valueOf(avgRate).setScale(2, RoundingMode.HALF_UP))
                .sourceType(entry.getValue().get(0).getSourceType())
                .build());
        }
        
        return trend;
    }

    private String generateTeachingSuggestion(KnowledgePointStatsVO stats) {
        double avgMastery = stats.getClassAvgMastery().doubleValue();
        int weakCount = stats.getWeakCount();
        
        if (avgMastery >= 80) {
            return "✅ 该知识点掌握良好，可适当进行拓展教学，培养优等生。";
        } else if (avgMastery >= 60) {
            return "🟡 该知识点掌握中等，建议加强练习，重点关注" + weakCount + "名薄弱学生。";
        } else {
            return "🔴 该知识点掌握薄弱，需要安排专项复习课，对" + weakCount + "名学生进行个别辅导。";
        }
    }

    private String getWeaknessLevel(double mastery) {
    if (mastery >= 70) return "GOOD";
    if (mastery >= 60) return "MILD";
    if (mastery >= 50) return "MODERATE";
    return "SEVERE";
}

    // ==================== 8. ECharts图表数据 ====================

    @Transactional(readOnly = true)
    public CourseChartDataVO getChartData(Long courseId, Long currentUserId, String userRole) {
        List<Long> visibleCourseIds = getVisibleCourseIds(currentUserId, userRole, courseId);
        if (visibleCourseIds.isEmpty()) {
            throw new RuntimeException("无权限访问该课程");
        }
        
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        
        List<Student> students = studentRepository.findByEnrollmentsCourse(course);
        
        return CourseChartDataVO.builder()
            .scoreTrend(getScoreTrend(course))
            .radarChart(getRadarChartData(course, students))
            .scoreDistribution(getScoreDistributionPie(course))
            .homeworkExamComparison(getHomeworkExamComparison(course))
            .build();
    }

    private List<ScoreTrendItem> getScoreTrend(Course course) {
        List<ScoreTrendItem> trend = new ArrayList<>();
        
        // 获取该课程的所有考试（按日期排序）
        List<Exam> exams = examRepository.findByCourseId(course.getId());
        
        for (Exam exam : exams) {
            Double avgScore = exam.getClassAvgScore() != null ? exam.getClassAvgScore().doubleValue() : 0;
            trend.add(ScoreTrendItem.builder()
                .name(exam.getName())
                .score(BigDecimal.valueOf(avgScore).setScale(2, RoundingMode.HALF_UP))
                .type("考试")
                .date(exam.getExamDate().toLocalDate().toString())
                .build());
        }
        
        // 获取作业数据
        List<Homework> homeworks = homeworkRepository.findByCourse(course);
        for (Homework homework : homeworks) {
            Double avgScore = homework.getAvgScore() != null ? homework.getAvgScore().doubleValue() : 0;
            trend.add(ScoreTrendItem.builder()
                .name(homework.getName())
                .score(BigDecimal.valueOf(avgScore).setScale(2, RoundingMode.HALF_UP))
                .type("作业")
                .date(homework.getDeadline().toLocalDate().toString())
                .build());
        }
        
        // 按日期排序
        trend.sort((a, b) -> a.getDate().compareTo(b.getDate()));
        
        return trend;
    }

    private RadarChartData getRadarChartData(Course course, List<Student> students) {
    List<KnowledgePoint> kps = knowledgePointRepository.findByCourseOrderBySortOrderAsc(course);
    
    List<String> indicators = new ArrayList<>();
    List<BigDecimal> classAvgValues = new ArrayList<>();
    
    // 获取班级ID（从第一个学生获取）
    Long classId = null;
    if (students != null && !students.isEmpty() && students.get(0).getClassInfo() != null) {
        classId = students.get(0).getClassInfo().getId();
    }
    for (KnowledgePoint kp : kps) {
        indicators.add(kp.getName());
        
        // 从 knowledge_point_score_detail 表获取班级平均掌握度
        BigDecimal avgMastery = BigDecimal.ZERO;
        if (classId != null) {
            avgMastery = kpScoreDetailRepository.getClassAvgScoreRate(kp.getId(), classId);
            if (avgMastery == null) avgMastery = BigDecimal.ZERO;
        }
        classAvgValues.add(avgMastery);
    }
    
    return RadarChartData.builder()
        .indicators(indicators)
        .classAvg(classAvgValues)
        .build();
}

    private ScoreDistributionPie getScoreDistributionPie(Course course) {
        List<ExamGrade> grades = examGradeRepository.findByCourseId(course.getId());
        
        int excellent = 0; // >=90
        int good = 0;      // 80-89
        int medium = 0;    // 70-79
        int pass = 0;      // 60-69
        int fail = 0;      // <60
        
        for (ExamGrade grade : grades) {
            if (grade.getScore() == null) continue;
            int score = grade.getScore().intValue();
            if (score >= 90) excellent++;
            else if (score >= 80) good++;
            else if (score >= 70) medium++;
            else if (score >= 60) pass++;
            else fail++;
        }
        
        return ScoreDistributionPie.builder()
            .excellent(excellent)
            .good(good)
            .medium(medium)
            .pass(pass)
            .fail(fail)
            .build();
    }

    private List<ComparisonItem> getHomeworkExamComparison(Course course) {
        List<ComparisonItem> comparison = new ArrayList<>();
        
        // 作业平均分
        Double homeworkAvg = homeworkRepository.getAvgScoreByCourse(course.getId());
        
        // 考试平均分
        Double examAvg = examGradeRepository.getStudentAvgScoreByCourse(course.getId());
        
        comparison.add(ComparisonItem.builder()
            .name("作业")
            .score(homeworkAvg != null ? BigDecimal.valueOf(homeworkAvg) : BigDecimal.ZERO)
            .build());
        
        comparison.add(ComparisonItem.builder()
            .name("考试")
            .score(examAvg != null ? BigDecimal.valueOf(examAvg) : BigDecimal.ZERO)
            .build());
        
        return comparison;
    }

    /**
     * AI整体分析报告（迁移到统一服务）
     */
    @Transactional(readOnly = false)
    public CourseAiAnalysisVO getAiAnalysis(Long courseId, Long currentUserId, String userRole) {
        List<Long> visibleCourseIds = getVisibleCourseIds(currentUserId, userRole, courseId);
        if (visibleCourseIds.isEmpty()) {
            throw new RuntimeException("无权限访问该课程");
        }
        
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        
        // 使用统一服务
        AiSuggestionDTO suggestion = unifiedAiAnalysisService.getOrCreateAnalysis(
            "COURSE",         // targetType: 课程类型
            courseId,         // targetId: 课程ID
            "COURSE_ANALYSIS", // reportType: 课程分析
            false
        );
        
        // 转换为 CourseAiAnalysisVO
        return convertToCourseAiAnalysisVO(course, suggestion);
    }

    /**
     * 转换为 CourseAiAnalysisVO
     */
    private CourseAiAnalysisVO convertToCourseAiAnalysisVO(Course course, AiSuggestionDTO suggestion) {
        return CourseAiAnalysisVO.builder()
            .summary(suggestion.getSummary())
            .strengths(suggestion.getStrengths())
            .weaknesses(suggestion.getWeaknesses())
            .suggestions(suggestion.getSuggestions())
            .analysisData(new HashMap<>())
            .chartsConfig(new HashMap<>())
            .createdAt(LocalDateTime.now())
            .build();
    }

    private String getCourseStatusText(CourseStatus status) {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("ONGOING", "进行中");
        statusMap.put("COMPLETED", "已结课");
        statusMap.put("DROPPED", "已停开");
        return statusMap.getOrDefault(status.toString(), status.toString());
    }
}