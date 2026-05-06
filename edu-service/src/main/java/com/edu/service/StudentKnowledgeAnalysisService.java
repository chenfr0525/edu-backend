package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;
import org.springframework.stereotype.Service;
import com.edu.domain.Course;
import com.edu.domain.Enrollment;
import com.edu.domain.ErrorRecord;
import com.edu.domain.Exam;
import com.edu.domain.Homework;
import com.edu.domain.KnowledgePoint;
import com.edu.domain.KnowledgePointScoreDetail;
import com.edu.domain.Student;
import com.edu.domain.StudentKnowledgeMastery;
import com.edu.domain.dto.AiSuggestionDTO;
import com.edu.domain.dto.CourseMasteryDTO;
import com.edu.domain.dto.KnowledgePointDetailDTO;
import com.edu.domain.dto.KnowledgePointProgressDTO;
import com.edu.domain.dto.KnowledgePointRadarDTO;
import com.edu.domain.dto.KnowledgePointSourceDTO;
import com.edu.domain.dto.KnowledgePointStatisticsCardsDTO;
import com.edu.domain.dto.KnowledgePointTreeDTO;
import com.edu.domain.dto.KnowledgePointTrendDTO;
import com.edu.repository.EnrollmentRepository;
import com.edu.repository.ErrorRecordRepository;
import com.edu.repository.ExamRepository;
import com.edu.repository.HomeworkRepository;
import com.edu.repository.KnowledgePointScoreDetailRepository;
import com.edu.repository.StudentKnowledgeMasteryRepository;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentKnowledgeAnalysisService {

    private final KnowledgePointService knowledgePointService;
    private final KnowledgePointScoreDetailService kpScoreDetailService;
    private final KnowledgePointScoreDetailRepository kpScoreDetailRepository;
    private final StudentKnowledgeMasteryService masteryService;
    private final StudentKnowledgeMasteryRepository masteryRepository;
    private final StudentService studentService;
    private final CourseService courseService;
    private final EnrollmentRepository enrollmentRepository;
    private final HomeworkRepository homeworkRepository;
    private final ExamRepository examRepository;
    private final ErrorRecordRepository errorRecordRepository;
    private final UnifiedAiAnalysisService unifiedAiAnalysisService;

    /**
     * 1. 获取知识点树形结构（按课程分组)
     */
    public List<KnowledgePointTreeDTO> getKnowledgePointTree(Long studentId, Long courseId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        List<Course> courses;
        if (courseId != null && courseId > 0) {
            courses = Collections.singletonList(courseService.findById(courseId).orElse(null));
        } else {
            courses = courseService.findByStudentId(studentId);
        }
        
        List<KnowledgePointTreeDTO> result = new ArrayList<>();
        
        for (Course course : courses) {
            if (course == null) continue;
            KnowledgePointTreeDTO courseNode = buildCourseNode(student, course);
            result.add(courseNode);
        }
        
        return result;
    }
    
   /**
 * 2. 获取知识点统计卡片
 */
public KnowledgePointStatisticsCardsDTO getStatisticsCards(Long studentId, Long courseId) {
    studentService.findById(studentId)
        .orElseThrow(() -> new RuntimeException("学生不存在"));

    KnowledgePointStatisticsCardsDTO cards = new KnowledgePointStatisticsCardsDTO();

    List<KnowledgePoint> scopedKnowledgePoints = getScopedKnowledgePoints(studentId, courseId);
    if (scopedKnowledgePoints.isEmpty()) {
        cards.setOverallMasteryRate(BigDecimal.ZERO);
        cards.setTotalKnowledgePoints(0);
        cards.setWeakKnowledgePoints(0);
        cards.setStrongKnowledgePoints(0);
        cards.setBestCourse("暂无");
        cards.setWeakestCourse("暂无");
        return cards;
    }

    Map<Long, BigDecimal> studentRateMap = getStudentRates(studentId, scopedKnowledgePoints);
    double totalRate = 0D;
    int weakCount = 0;
    int strongCount = 0;
    for (KnowledgePoint kp : scopedKnowledgePoints) {
        double rate = studentRateMap.getOrDefault(kp.getId(), BigDecimal.ZERO).doubleValue();
        totalRate += rate;
        if (rate < 60) weakCount++;
        if (rate >= 80) strongCount++;
    }

    cards.setOverallMasteryRate(
        BigDecimal.valueOf(totalRate / scopedKnowledgePoints.size()).setScale(2, RoundingMode.HALF_UP));
    cards.setTotalKnowledgePoints(scopedKnowledgePoints.size());

    cards.setWeakKnowledgePoints(weakCount);
    cards.setStrongKnowledgePoints(strongCount);

    // 按课程分组统计平均掌握度（无分按0计）
    Map<Long, List<Double>> courseScoresMap = new HashMap<>();
    for (KnowledgePoint kp : scopedKnowledgePoints) {
        Long detailCourseId = kp.getCourse().getId();
        double scoreRate = studentRateMap.getOrDefault(kp.getId(), BigDecimal.ZERO).doubleValue();
        courseScoresMap.computeIfAbsent(detailCourseId, k -> new ArrayList<>()).add(scoreRate);
    }
    
    // 计算每个课程的平均掌握度
    Map<Long, Double> courseAvgMastery = new HashMap<>();
    for (Map.Entry<Long, List<Double>> entry : courseScoresMap.entrySet()) {
        double courseAvg = entry.getValue().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
        courseAvgMastery.put(entry.getKey(), courseAvg);
    }
    
    // ✅ 6. 找出最佳和最弱课程
    Optional<Map.Entry<Long, Double>> best = courseAvgMastery.entrySet().stream()
            .max(Map.Entry.comparingByValue());
    Optional<Map.Entry<Long, Double>> worst = courseAvgMastery.entrySet().stream()
            .min(Map.Entry.comparingByValue());

    if (best.isPresent()) {
        Course bestCourse = courseService.findById(best.get().getKey()).orElse(null);
        cards.setBestCourse(bestCourse != null ? bestCourse.getName() : "未知");
    } else cards.setBestCourse("暂无");
    if (worst.isPresent()) {
        Course worstCourse = courseService.findById(worst.get().getKey()).orElse(null);
        cards.setWeakestCourse(worstCourse != null ? worstCourse.getName() : "未知");
    } else cards.setWeakestCourse("暂无");

    return cards;
}
    /**
     * 3. 获取知识点掌握进度（环图数据）
     */
    public KnowledgePointProgressDTO getKnowledgePointProgress(Long studentId, Long courseId) {
       
        studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        KnowledgePointProgressDTO progress = new KnowledgePointProgressDTO();

        List<KnowledgePoint> scopedKnowledgePoints = getScopedKnowledgePoints(studentId, courseId);
        if (scopedKnowledgePoints.isEmpty()) {
            progress.setOverallMasteryRate(BigDecimal.ZERO);
            progress.setMasteredCount(0);
            progress.setLearningCount(0);
            progress.setWeakCount(0);
            progress.setCourseMasteryList(new ArrayList<>());
            return progress;
        }
        
        Map<Long, BigDecimal> studentRateMap = getStudentRates(studentId, scopedKnowledgePoints);
        double totalRate = 0D;
        
        int mastered = 0, learning = 0, weak = 0;
        for (KnowledgePoint kp : scopedKnowledgePoints) {
            double rate = studentRateMap.getOrDefault(kp.getId(), BigDecimal.ZERO).doubleValue();
            totalRate += rate;
            if (rate >= 80) mastered++;
            else if (rate >= 60) learning++;
            else weak++;
        }
        progress.setOverallMasteryRate(
            BigDecimal.valueOf(totalRate / scopedKnowledgePoints.size()).setScale(2, RoundingMode.HALF_UP));
        progress.setMasteredCount(mastered);
        progress.setLearningCount(learning);
        progress.setWeakCount(weak);

        List<CourseMasteryDTO> courseMasteryList = new ArrayList<>();
        Map<Long, List<KnowledgePoint>> courseMap = scopedKnowledgePoints.stream()
            .collect(Collectors.groupingBy(k -> k.getCourse().getId()));
        for (Map.Entry<Long, List<KnowledgePoint>> entry : courseMap.entrySet()) {
            Course course = courseService.findById(entry.getKey()).orElse(null);
            if (course == null) continue;
            double courseAvg = entry.getValue().stream()
                .map(k -> studentRateMap.getOrDefault(k.getId(), BigDecimal.ZERO))
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0D);
            CourseMasteryDTO dto = new CourseMasteryDTO();
            dto.setCourseId(course.getId());
            dto.setCourseName(course.getName());
            dto.setMasteryRate(BigDecimal.valueOf(courseAvg).setScale(2, RoundingMode.HALF_UP));
            dto.setKnowledgePointCount(entry.getValue().size());
            if (courseAvg >= 80) dto.setMasteryLevel("good");
            else if (courseAvg >= 60) dto.setMasteryLevel("warning");
            else dto.setMasteryLevel("poor");
            courseMasteryList.add(dto);
        }
        courseMasteryList.sort((a, b) -> b.getMasteryRate().compareTo(a.getMasteryRate()));
        progress.setCourseMasteryList(courseMasteryList);

        return progress;
    }
    
   /**
 * 4. 获取知识点雷达图数据
 */
public KnowledgePointRadarDTO getKnowledgePointRadar(Long studentId, Long courseId) {
    studentService.findById(studentId)
            .orElseThrow(() -> new RuntimeException("学生不存在"));

    if (courseId == null || courseId == 0) {
        KnowledgePointRadarDTO empty = new KnowledgePointRadarDTO();
        empty.setIndicators(new ArrayList<>());
        empty.setMyValues(new ArrayList<>());
        empty.setClassAvgValues(new ArrayList<>());
        return empty;
    }
    
    Course course = courseService.findById(courseId)
            .orElseThrow(() -> new RuntimeException("课程不存在"));
    
    List<KnowledgePoint> knowledgePoints = knowledgePointService.findByCourse(course);
    
    List<String> indicators = new ArrayList<>();
    List<BigDecimal> myValues = new ArrayList<>();
    List<BigDecimal> classAvgValues = new ArrayList<>();
    
    for (KnowledgePoint kp : knowledgePoints) {
        indicators.add(kp.getName());
        
        // ✅ 从 knowledge_point_score_detail 获取学生平均掌握度
        BigDecimal myAvg = kpScoreDetailRepository.getStudentAvgScoreRate(studentId, kp.getId());
        myValues.add(myAvg != null ? myAvg : BigDecimal.ZERO);
        
        // 课程选修学生群体平均掌握度（不使用行政班）
        BigDecimal classAvg = getCourseCohortAvgRate(course.getId(), kp.getId(), null, null);
        classAvgValues.add(classAvg);
    }
    
    KnowledgePointRadarDTO radar = new KnowledgePointRadarDTO();
    radar.setIndicators(indicators);
    radar.setMyValues(myValues);
    radar.setClassAvgValues(classAvgValues);
    radar.setCourseId(courseId);
    radar.setCourseName(course.getName());
    
    return radar;
    }
    
    /**
     * 5. 获取知识点详情（包含趋势图)
     */
    @Transactional
    public KnowledgePointDetailDTO getKnowledgePointDetail(Long studentId, Long knowledgePointId) {
        
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        KnowledgePoint knowledgePoint = knowledgePointService.findById(knowledgePointId)
                .orElseThrow(() -> new RuntimeException("知识点不存在"));
        
        KnowledgePointDetailDTO detail = new KnowledgePointDetailDTO();
        detail.setKnowledgePointId(knowledgePointId);
        detail.setKnowledgePointName(knowledgePoint.getName());
        detail.setDescription(knowledgePoint.getDescription());
        detail.setCourseId(knowledgePoint.getCourse().getId());
        detail.setCourseName(knowledgePoint.getCourse().getName());
        
        BigDecimal myRate = kpScoreDetailRepository.getStudentAvgScoreRate(studentId, knowledgePointId);
        if (myRate == null) myRate = BigDecimal.ZERO;
        myRate = myRate.setScale(2, RoundingMode.HALF_UP);
        detail.setMasteryRate(myRate);
        double level = myRate.doubleValue();
        if (level >= 80) detail.setMasteryLevel("good");
        else if (level >= 60) detail.setMasteryLevel("warning");
        else detail.setMasteryLevel("poor");

        BigDecimal classAvg = getCourseCohortAvgRate(knowledgePoint.getCourse().getId(), knowledgePointId, null, null);
        detail.setClassAvgMasteryRate(classAvg);
        
        detail.setWeakPoints(getWeakPointsForKnowledgePoint(student, knowledgePoint));
        detail.setTrendData(getKnowledgePointTrendData(studentId, knowledgePointId));
        detail.setSourceDetails(getKnowledgePointSourceDetails(studentId, knowledgePointId, knowledgePoint.getCourse().getId()));
        
        return detail;
    }
    
    /**
     * 获取知识点AI分析（迁移到统一服务）
     * @param studentId 学生ID
     * @param courseId 课程ID（可为null，表示所有课程）
     */
    public AiSuggestionDTO getKnowledgePointAiAnalysis(Long studentId, Long courseId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        // 根据是否有courseId区分报告类型
        String reportType = (courseId != null) 
            ? "KNOWLEDGE_ANALYSIS_COURSE_" + courseId 
            : "KNOWLEDGE_ANALYSIS";
        
        return unifiedAiAnalysisService.getOrCreateAnalysis(
            "STUDENT",
            student.getId(),
            reportType,
            false
        );
    }
   /**
     * 手动刷新知识点AI分析
     */
    public AiSuggestionDTO refreshKnowledgeAiAnalysis(Long studentId, Long courseId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        String reportType = (courseId != null) 
            ? "KNOWLEDGE_ANALYSIS_COURSE_" + courseId 
            : "KNOWLEDGE_ANALYSIS";
        
        return unifiedAiAnalysisService.refreshAnalysis(
            "STUDENT",
            student.getId(),
            reportType
        );
    }
     /**
     * 降级方案：当 AI 调用失败时使用
     */
    private AiSuggestionDTO generateFallbackKnowledgeAiAnalysis(Student student, Long courseId, List<StudentKnowledgeMastery> masteries) {
        AiSuggestionDTO analysis = new AiSuggestionDTO();
        
        if (masteries.isEmpty()) {
            analysis.setSummary("暂无知识点掌握数据，请先完成作业和考试");
            analysis.setStrengths(new ArrayList<>());
            analysis.setWeaknesses(new ArrayList<>());
            analysis.setSuggestions(Arrays.asList("请先完成学习任务"));
            return analysis;
        }
        
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        
        for (StudentKnowledgeMastery m : masteries) {
            String kpName = m.getKnowledgePoint().getName();
            if (m.getMasteryLevel() >= 80) {
                strengths.add(kpName);
            } else if (m.getMasteryLevel() < 60) {
                weaknesses.add(kpName);
            }
        }
        
        double avgMastery = masteries.stream()
                .mapToDouble(StudentKnowledgeMastery::getMasteryLevel)
                .average()
                .orElse(0);
        
        String summary;
        if (avgMastery >= 80) {
            summary = "你的知识点掌握情况优秀！大部分知识点都已熟练掌握。";
        } else if (avgMastery >= 70) {
            summary = "你的知识点掌握情况良好，有几个知识点需要加强练习。";
        } else if (avgMastery >= 60) {
            summary = "你的知识点掌握情况中等，建议针对薄弱知识点进行专项训练。";
        } else {
            summary = "你的知识点掌握情况有待提升，建议重新复习基础知识。";
        }
        
        if (courseId != null && courseId > 0) {
            Course course = courseService.findById(courseId).orElse(null);
            if (course != null) {
                summary = "在《" + course.getName() + "》课程中，" + summary;
            }
        }
        
        analysis.setSummary(summary);
        analysis.setStrengths(strengths.isEmpty() ? Arrays.asList("暂无突出优势") : strengths);
        analysis.setWeaknesses(weaknesses.isEmpty() ? Arrays.asList("暂无明显薄弱点") : weaknesses);
        analysis.setSuggestions(Arrays.asList(
            "针对薄弱知识点进行专项练习",
            "定期回顾错题，巩固记忆",
            "多与同学讨论，加深理解"
        ));
        
        return analysis;
    }
    
    //空数据时调用
    private AiSuggestionDTO createEmptyKnowledgeAiResponse() {
        AiSuggestionDTO analysis = new AiSuggestionDTO();
        analysis.setSummary("暂无知识点掌握数据，无法生成分析报告");
        analysis.setStrengths(new ArrayList<>());
        analysis.setWeaknesses(new ArrayList<>());
        analysis.setSuggestions(Arrays.asList("请先完成作业和考试"));
        return analysis;
    }
    
    private KnowledgePointTreeDTO buildCourseNode(Student student, Course course) {
        KnowledgePointTreeDTO courseNode = new KnowledgePointTreeDTO();
        courseNode.setId(String.valueOf(course.getId()));
        courseNode.setLabel(getCourseIcon(course.getName()) + " " + course.getName());
        courseNode.setCourseId(course.getId());
        courseNode.setCourseName(course.getName());
        
        List<KnowledgePoint> knowledgePoints = knowledgePointService.findByCourse(course);
        
        List<KnowledgePointTreeDTO> children = new ArrayList<>();
        double totalMastery = 0;
        
        for (KnowledgePoint kp : knowledgePoints) {
            KnowledgePointTreeDTO child = buildKnowledgePointNode(student.getId(), student, kp);
            children.add(child);
            totalMastery += child.getMasteryRate().doubleValue();
        }
        
        BigDecimal courseMastery = knowledgePoints.isEmpty() ? 
                BigDecimal.ZERO : 
                BigDecimal.valueOf(totalMastery / knowledgePoints.size()).setScale(2, RoundingMode.HALF_UP);
        courseNode.setMasteryRate(courseMastery);
        
        if (courseMastery.doubleValue() >= 80) courseNode.setMasteryLevel("good");
        else if (courseMastery.doubleValue() >= 60) courseNode.setMasteryLevel("warning");
        else courseNode.setMasteryLevel("poor");
        
        courseNode.setChildren(children);
        
        return courseNode;
    }
    
    private KnowledgePointTreeDTO buildKnowledgePointNode(Long studentId, Student student, KnowledgePoint kp) {
        KnowledgePointTreeDTO node = new KnowledgePointTreeDTO();
        node.setId(kp.getId().toString());
        node.setLabel(kp.getName());
        
        BigDecimal masteryRate = kpScoreDetailRepository.getStudentAvgScoreRate(studentId, kp.getId());
        if (masteryRate == null) masteryRate = BigDecimal.ZERO;
        masteryRate = masteryRate.setScale(2, RoundingMode.HALF_UP);
        node.setMasteryRate(masteryRate);
        
        double rate = masteryRate.doubleValue();
        if (rate >= 80) {
            node.setMasteryLevel("good");
        } else if (rate >= 60) {
            node.setMasteryLevel("warning");
        } else {
            node.setMasteryLevel("poor");
        }
        
        node.setWeakPoints(getWeakPointsForKnowledgePoint(student, kp));
        
        return node;
    }

    /**
     * 获取学生按课程过滤后的最新知识点明细（每个知识点一条）
     */
    private List<KnowledgePointScoreDetail> getLatestDetailsByCourse(Long studentId, Long courseId) {
        List<KnowledgePointScoreDetail> raw = (courseId != null && courseId > 0)
            ? kpScoreDetailRepository.findLatestByStudentIdAndCourseId(studentId, courseId)
            : kpScoreDetailRepository.findAllLatestByStudentId(studentId);

        // 防御性去重：同一知识点只保留最新时间的一条
        Map<Long, KnowledgePointScoreDetail> latestMap = new LinkedHashMap<>();
        for (KnowledgePointScoreDetail d : raw) {
            if (d == null || d.getKnowledgePoint() == null || d.getKnowledgePoint().getId() == null) continue;
            Long kpId = d.getKnowledgePoint().getId();
            KnowledgePointScoreDetail existed = latestMap.get(kpId);
            if (existed == null || (d.getCreatedAt() != null && existed.getCreatedAt() != null && d.getCreatedAt().isAfter(existed.getCreatedAt()))) {
                latestMap.put(kpId, d);
            } else if (existed == null) {
                latestMap.put(kpId, d);
            }
        }
        return new ArrayList<>(latestMap.values());
    }
    
    private List<String> getWeakPointsForKnowledgePoint(Student student, KnowledgePoint kp) {
        List<ErrorRecord> errorRecords = errorRecordRepository.findByStudentAndKnowledgePoint(student, kp);
        return errorRecords.stream()
                .map(ErrorRecord::getName)
                .collect(Collectors.toList());
    }
    
    private List<KnowledgePointTrendDTO> getKnowledgePointTrendData(Long studentId, Long knowledgePointId) {
        List<KnowledgePointScoreDetail> details = kpScoreDetailRepository
                .findHistoryByStudentIdAndKpId(studentId, knowledgePointId);
        
        List<KnowledgePointTrendDTO> trendData = new ArrayList<>();
        
        for (KnowledgePointScoreDetail detail : details) {
            KnowledgePointTrendDTO trend = new KnowledgePointTrendDTO();
            trend.setScoreRate(detail.getScoreRate());
            trend.setDate(detail.getCreatedAt());
            
            if ("HOMEWORK".equals(detail.getSourceType())) {
                trend.setSourceType("HOMEWORK");
                trend.setSourceName(resolveSourceName("HOMEWORK", detail.getSourceId()));
            } else if ("EXAM".equals(detail.getSourceType())) {
                trend.setSourceType("EXAM");
                trend.setSourceName(resolveSourceName("EXAM", detail.getSourceId()));
            }
            
            trendData.add(trend);
        }
        
        return trendData;
    }
    
    private List<KnowledgePointSourceDTO> getKnowledgePointSourceDetails(Long studentId, Long knowledgePointId, Long courseId) {
        List<KnowledgePointScoreDetail> details = kpScoreDetailRepository
                .findAllByStudentIdAndKpId(studentId, knowledgePointId);
        
        List<KnowledgePointSourceDTO> sourceDetails = new ArrayList<>();
        
        for (KnowledgePointScoreDetail detail : details) {
            KnowledgePointSourceDTO source = new KnowledgePointSourceDTO();
            source.setSourceType(detail.getSourceType());
            source.setSourceId(detail.getSourceId());
            source.setMyScoreRate(detail.getScoreRate());
            source.setMyScore(detail.getActualScore() != null ? detail.getActualScore().intValue() : null);
            source.setFullScore(detail.getMaxScore() != null ? detail.getMaxScore().intValue() : null);
            
            if ("HOMEWORK".equals(detail.getSourceType())) {
                source.setSourceName(resolveSourceName("HOMEWORK", detail.getSourceId()));
            } else if ("EXAM".equals(detail.getSourceType())) {
                source.setSourceName(resolveSourceName("EXAM", detail.getSourceId()));
            }
            
            source.setClassAvgScoreRate(
                getCourseCohortAvgRate(courseId, knowledgePointId, detail.getSourceType(), detail.getSourceId()));
            sourceDetails.add(source);
        }
        sourceDetails.sort(Comparator.comparing(KnowledgePointSourceDTO::getSourceId, Comparator.nullsLast(Long::compareTo)).reversed());
        return sourceDetails;
    }

    private List<KnowledgePoint> getScopedKnowledgePoints(Long studentId, Long courseId) {
        if (courseId != null && courseId > 0) {
            Course course = courseService.findById(courseId).orElse(null);
            return course == null ? new ArrayList<>() : knowledgePointService.findByCourse(course);
        }
        List<Course> courses = courseService.findByStudentId(studentId);
        Map<Long, KnowledgePoint> kpMap = new LinkedHashMap<>();
        for (Course course : courses) {
            if (course == null) continue;
            for (KnowledgePoint kp : knowledgePointService.findByCourse(course)) {
                kpMap.put(kp.getId(), kp);
            }
        }
        return new ArrayList<>(kpMap.values());
    }

    private Map<Long, BigDecimal> getStudentRates(Long studentId, List<KnowledgePoint> knowledgePoints) {
        Map<Long, BigDecimal> rateMap = new HashMap<>();
        for (KnowledgePoint kp : knowledgePoints) {
            BigDecimal rate = kpScoreDetailRepository.getStudentAvgScoreRate(studentId, kp.getId());
            if (rate == null) rate = BigDecimal.ZERO;
            rateMap.put(kp.getId(), rate.setScale(2, RoundingMode.HALF_UP));
        }
        return rateMap;
    }

    /**
     * 课程选修学生群体平均掌握度
     * sourceType/sourceId 为空时按知识点总体平均；不为空时按指定来源平均
     */
    private BigDecimal getCourseCohortAvgRate(Long courseId, Long knowledgePointId, String sourceType, Long sourceId) {
        Course course = courseService.findById(courseId).orElse(null);
        if (course == null) return BigDecimal.ZERO;
        List<Enrollment> enrollments = enrollmentRepository.findByCourse(course);
        if (enrollments == null || enrollments.isEmpty()) return BigDecimal.ZERO;

        Map<Long, Long> studentIds = new LinkedHashMap<>();
        for (Enrollment e : enrollments) {
            if (e != null && e.getStudent() != null && e.getStudent().getId() != null) {
                studentIds.put(e.getStudent().getId(), e.getStudent().getId());
            }
        }
        if (studentIds.isEmpty()) return BigDecimal.ZERO;

        double sum = 0D;
        for (Long sid : studentIds.keySet()) {
            double rate;
            if (sourceType != null && sourceId != null) {
                List<KnowledgePointScoreDetail> sourceDetails =
                    kpScoreDetailRepository.findBySourceTypeAndSourceIdAndStudentId(sourceType, sourceId, sid);
                KnowledgePointScoreDetail matched = sourceDetails.stream()
                    .filter(d -> d.getKnowledgePoint() != null && knowledgePointId.equals(d.getKnowledgePoint().getId()))
                    .max(Comparator.comparing(KnowledgePointScoreDetail::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);
                rate = (matched != null && matched.getScoreRate() != null) ? matched.getScoreRate().doubleValue() : 0D;
            } else {
                BigDecimal studentRate = kpScoreDetailRepository.getStudentAvgScoreRate(sid, knowledgePointId);
                rate = studentRate != null ? studentRate.doubleValue() : 0D;
            }
            sum += rate;
        }
        return BigDecimal.valueOf(sum / studentIds.size()).setScale(2, RoundingMode.HALF_UP);
    }

    private String resolveSourceName(String sourceType, Long sourceId) {
        if ("HOMEWORK".equals(sourceType)) {
            Homework hw = homeworkRepository.findById(sourceId).orElse(null);
            return hw != null ? "作业：" + hw.getName() : "作业#" + sourceId;
        }
        if ("EXAM".equals(sourceType)) {
            Exam exam = examRepository.findById(sourceId).orElse(null);
            return exam != null ? "考试：" + exam.getName() : "考试#" + sourceId;
        }
        return sourceType + "#" + sourceId;
    }
    
    private String getCourseIcon(String courseName) {
        if (courseName.contains("数学")) return "📐";
        if (courseName.contains("Java") || courseName.contains("编程")) return "☕";
        if (courseName.contains("数据库")) return "🗄️";
        if (courseName.contains("Web") || courseName.contains("前端")) return "🌐";
        if (courseName.contains("数据结构")) return "🌲";
        return "📚";
    }
}
