package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONObject;
import com.edu.domain.AiAnalysisReport;
import com.edu.domain.Course;
import com.edu.domain.ErrorRecord;
import com.edu.domain.KnowledgePoint;
import com.edu.domain.KnowledgePointScoreDetail;
import com.edu.domain.Semester;
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
import com.edu.repository.ErrorRecordRepository;
import com.edu.repository.KnowledgePointScoreDetailRepository;
import com.edu.repository.StudentKnowledgeMasteryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final ClassService classService;
    private final AiAnalysisReportService aiReportService;
    private final SemesterService semesterService;
    private final ErrorRecordRepository errorRecordRepository;
    private final DeepSeekService deepSeekService;  // 新增注入

    /**
     * 1. 获取知识点树形结构（按课程分组）- 保持不变
     */
    public List<KnowledgePointTreeDTO> getKnowledgePointTree(Long studentId, Long courseId) {
        // ... 保持不变 ...
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
     * 2. 获取知识点统计卡片 - 保持不变
     */
    public KnowledgePointStatisticsCardsDTO getStatisticsCards(Long studentId) {
        // ... 保持不变 ...
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        KnowledgePointStatisticsCardsDTO cards = new KnowledgePointStatisticsCardsDTO();
        
        List<StudentKnowledgeMastery> masteries = masteryRepository.findAllByStudentId(studentId);
        
        if (masteries.isEmpty()) {
            cards.setOverallMasteryRate(BigDecimal.ZERO);
            cards.setTotalKnowledgePoints(0);
            cards.setWeakKnowledgePoints(0);
            cards.setStrongKnowledgePoints(0);
            return cards;
        }
        
        double avgMastery = masteries.stream()
                .mapToDouble(StudentKnowledgeMastery::getMasteryLevel)
                .average()
                .orElse(0);
        cards.setOverallMasteryRate(BigDecimal.valueOf(avgMastery).setScale(2, RoundingMode.HALF_UP));
        
        cards.setTotalKnowledgePoints(masteries.size());
        
        long weakCount = masteries.stream()
                .filter(m -> m.getMasteryLevel() < 60)
                .count();
        cards.setWeakKnowledgePoints((int) weakCount);
        
        long strongCount = masteries.stream()
                .filter(m -> m.getMasteryLevel() >= 80)
                .count();
        cards.setStrongKnowledgePoints((int) strongCount);
        
        Map<Long, Double> courseAvgMastery = new HashMap<>();
        for (StudentKnowledgeMastery m : masteries) {
            Long courseId = m.getKnowledgePoint().getCourse().getId();
            courseAvgMastery.merge(courseId, m.getMasteryLevel(), Double::sum);
        }
        
        Map<Long, Double> courseFinalAvg = new HashMap<>();
        for (Map.Entry<Long, Double> entry : courseAvgMastery.entrySet()) {
            Long courseId = entry.getKey();
            int count = (int) masteries.stream()
                    .filter(m -> m.getKnowledgePoint().getCourse().getId().equals(courseId))
                    .count();
            courseFinalAvg.put(courseId, entry.getValue() / count);
        }
        
        Optional<Map.Entry<Long, Double>> best = courseFinalAvg.entrySet().stream()
                .max(Map.Entry.comparingByValue());
        Optional<Map.Entry<Long, Double>> worst = courseFinalAvg.entrySet().stream()
                .min(Map.Entry.comparingByValue());
        
        if (best.isPresent()) {
            Course bestCourse = courseService.findById(best.get().getKey()).orElse(null);
            cards.setBestCourse(bestCourse != null ? bestCourse.getName() : "未知");
        }
        if (worst.isPresent()) {
            Course worstCourse = courseService.findById(worst.get().getKey()).orElse(null);
            cards.setWeakestCourse(worstCourse != null ? worstCourse.getName() : "未知");
        }
        
        return cards;
    }
    
    /**
     * 3. 获取知识点掌握进度（环图数据）- 保持不变
     */
    public KnowledgePointProgressDTO getKnowledgePointProgress(Long studentId, Long courseId) {
        // ... 保持不变 ...
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        KnowledgePointProgressDTO progress = new KnowledgePointProgressDTO();
        
        List<StudentKnowledgeMastery> masteries;
        if (courseId != null && courseId > 0) {
            masteries = masteryRepository.findByStudentIdAndCourseId(studentId, courseId);
        } else {
            masteries = masteryRepository.findAllByStudentId(studentId);
        }
        
        if (masteries.isEmpty()) {
            progress.setOverallMasteryRate(BigDecimal.ZERO);
            progress.setMasteredCount(0);
            progress.setLearningCount(0);
            progress.setWeakCount(0);
            progress.setCourseMasteryList(new ArrayList<>());
            return progress;
        }
        
        double avgMastery = masteries.stream()
                .mapToDouble(StudentKnowledgeMastery::getMasteryLevel)
                .average()
                .orElse(0);
        progress.setOverallMasteryRate(BigDecimal.valueOf(avgMastery).setScale(2, RoundingMode.HALF_UP));
        
        int mastered = 0, learning = 0, weak = 0;
        for (StudentKnowledgeMastery m : masteries) {
            if (m.getMasteryLevel() >= 80) mastered++;
            else if (m.getMasteryLevel() >= 60) learning++;
            else weak++;
        }
        progress.setMasteredCount(mastered);
        progress.setLearningCount(learning);
        progress.setWeakCount(weak);
        
        if (courseId == null || courseId == 0) {
            List<CourseMasteryDTO> courseMasteryList = new ArrayList<>();
            Map<Long, List<StudentKnowledgeMastery>> courseMap = masteries.stream()
                    .collect(Collectors.groupingBy(m -> m.getKnowledgePoint().getCourse().getId()));
            
            for (Map.Entry<Long, List<StudentKnowledgeMastery>> entry : courseMap.entrySet()) {
                Course course = courseService.findById(entry.getKey()).orElse(null);
                if (course == null) continue;
                
                double courseAvg = entry.getValue().stream()
                        .mapToDouble(StudentKnowledgeMastery::getMasteryLevel)
                        .average()
                        .orElse(0);
                
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
        }
        
        return progress;
    }
    
    /**
     * 4. 获取知识点雷达图数据 - 保持不变
     */
    public KnowledgePointRadarDTO getKnowledgePointRadar(Long studentId, Long courseId) {
        // ... 保持不变 ...
        Student student = studentService.findById(studentId)
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
        
        Long classId = student.getClassInfo() != null ? student.getClassInfo().getId() : null;
        
        for (KnowledgePoint kp : knowledgePoints) {
            indicators.add(kp.getName());
            
            Optional<StudentKnowledgeMastery> mastery = masteryService.findByStudentAndKnowledgePoint(student, kp);
            if (mastery.isPresent()) {
                myValues.add(BigDecimal.valueOf(mastery.get().getMasteryLevel()).setScale(2, RoundingMode.HALF_UP));
            } else {
                myValues.add(BigDecimal.ZERO);
            }
            
            if (classId != null) {
                BigDecimal classAvg = kpScoreDetailRepository.getClassAvgScoreRate(kp.getId(), classId);
                classAvgValues.add(classAvg != null ? classAvg.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            } else {
                classAvgValues.add(BigDecimal.ZERO);
            }
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
     * 5. 获取知识点详情（包含趋势图）- 保持不变
     */
    @Transactional
    public KnowledgePointDetailDTO getKnowledgePointDetail(Long studentId, Long knowledgePointId) {
        // ... 保持不变 ...
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
        
        Optional<StudentKnowledgeMastery> mastery = masteryService.findByStudentAndKnowledgePoint(student, knowledgePoint);
        if (mastery.isPresent()) {
            double level = mastery.get().getMasteryLevel();
            detail.setMasteryRate(BigDecimal.valueOf(level).setScale(2, RoundingMode.HALF_UP));
            
            if (level >= 80) detail.setMasteryLevel("good");
            else if (level >= 60) detail.setMasteryLevel("warning");
            else detail.setMasteryLevel("poor");
        } else {
            detail.setMasteryRate(BigDecimal.ZERO);
            detail.setMasteryLevel("poor");
        }
        
        Long classId = student.getClassInfo() != null ? student.getClassInfo().getId() : null;
        if (classId != null) {
            BigDecimal classAvg = kpScoreDetailRepository.getClassAvgScoreRate(knowledgePointId, classId);
            detail.setClassAvgMasteryRate(classAvg != null ? classAvg.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        } else {
            detail.setClassAvgMasteryRate(BigDecimal.ZERO);
        }
        
        detail.setWeakPoints(getWeakPointsForKnowledgePoint(student, knowledgePoint));
        detail.setTrendData(getKnowledgePointTrendData(studentId, knowledgePointId));
        detail.setSourceDetails(getKnowledgePointSourceDetails(studentId, knowledgePointId));
        
        return detail;
    }
    
    /**
     * 6. 获取知识点AI分析（按课程）- 修改：增加AI调用
     */
    public AiSuggestionDTO getKnowledgePointAiAnalysis(Long studentId, Long courseId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        String cacheKey = courseId != null ? "KNOWLEDGE_COURSE_" + courseId : "KNOWLEDGE_OVERALL";
        
        // 尝试从缓存获取（7天内有效）
        AiAnalysisReport cachedReport = aiReportService.findLatestReport("STUDENT", studentId, cacheKey);
        
        if (cachedReport != null && cachedReport.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7))) {
            log.info("使用缓存的知识点AI分析，学生ID: {}, cacheKey: {}", studentId, cacheKey);
            return parseKnowledgeAiAnalysisFromReport(cachedReport);
        }
        
        // 收集数据
        List<StudentKnowledgeMastery> masteries;
        if (courseId != null && courseId > 0) {
            masteries = masteryRepository.findByStudentIdAndCourseId(studentId, courseId);
        } else {
            masteries = masteryRepository.findAllByStudentId(studentId);
        }
        
        if (masteries.isEmpty()) {
            return createEmptyKnowledgeAiResponse();
        }
        
        log.info("调用 AI 生成知识点分析，学生ID: {}, 课程ID: {}", studentId, courseId);
        
        try {
            JSONObject dataJson = buildKnowledgeAiData(student, courseId, masteries);
            AiSuggestionDTO aiResponse = deepSeekService.analyzeData(dataJson.toJSONString(), "学生知识点掌握分析");

            if (aiResponse != null && aiResponse.getSummary() != null) {
                // 保存到数据库
                saveKnowledgeAiReport(student, courseId, masteries, aiResponse, cacheKey);
                return aiResponse;
            }
        } catch (Exception e) {
            log.error("调用 AI 生成知识点分析失败", e);
        }
        
        // 降级方案
        return generateFallbackKnowledgeAiAnalysis(student, courseId, masteries);
    }
    
    /**
     * 7. 手动刷新 AI 分析报告（新增）
     */
    public AiSuggestionDTO refreshKnowledgeAiAnalysis(Long studentId, Long courseId) {
        Student student = studentService.findById(studentId)
                .orElseThrow(() -> new RuntimeException("学生不存在"));
        
        String cacheKey = courseId != null ? "KNOWLEDGE_COURSE_" + courseId : "KNOWLEDGE_OVERALL";
        
        // 删除旧的缓存报告
        List<AiAnalysisReport> oldReports = aiReportService.findByTarget("STUDENT", studentId);
        for (AiAnalysisReport report : oldReports) {
            if (cacheKey.equals(report.getReportType())) {
                aiReportService.deleteById(report.getId());
                log.info("删除旧的知识点 AI 报告，ID: {}", report.getId());
            }
        }
        
        // 强制重新生成
        return getKnowledgePointAiAnalysis(studentId, courseId);
    }
    
    // ==================== AI 相关核心方法 ====================
    
    /**
     * 构建知识点 AI 请求数据
     */
    private JSONObject buildKnowledgeAiData(Student student, Long courseId, List<StudentKnowledgeMastery> masteries) {
        JSONObject data = new JSONObject();
        data.put("studentName", student.getUser().getName());
        
        if (courseId != null && courseId > 0) {
            Course course = courseService.findById(courseId).orElse(null);
            data.put("courseName", course != null ? course.getName() : "未知课程");
        } else {
            data.put("scope", "全部课程");
        }
        
        List<JSONObject> kpList = new ArrayList<>();
        for (StudentKnowledgeMastery m : masteries) {
            JSONObject kp = new JSONObject();
            kp.put("name", m.getKnowledgePoint().getName());
            kp.put("masteryLevel", m.getMasteryLevel());
            kp.put("weaknessLevel", m.getWeaknessLevel());
            kpList.add(kp);
        }
        data.put("knowledgePoints", kpList);
        
        double avgMastery = masteries.stream()
                .mapToDouble(StudentKnowledgeMastery::getMasteryLevel)
                .average()
                .orElse(0);
        data.put("avgMastery", avgMastery);
        
        long weakCount = masteries.stream().filter(m -> m.getMasteryLevel() < 60).count();
        long strongCount = masteries.stream().filter(m -> m.getMasteryLevel() >= 80).count();
        data.put("weakCount", weakCount);
        data.put("strongCount", strongCount);
        
        return data;
    }
    
    /**
     * 保存知识点 AI 报告到数据库
     */
    private void saveKnowledgeAiReport(Student student, Long courseId, List<StudentKnowledgeMastery> masteries,
                                        AiSuggestionDTO aiResponse, String cacheKey) {
        try {
            Semester currentSemester = getCurrentSemester();
            
            JSONObject analysisData = new JSONObject();
            analysisData.put("courseId", courseId);
            analysisData.put("totalKnowledgePoints", masteries.size());
            analysisData.put("avgMastery", masteries.stream().mapToDouble(StudentKnowledgeMastery::getMasteryLevel).average().orElse(0));
            analysisData.put("aiResponse", aiResponse);
            analysisData.put("generatedAt", LocalDateTime.now().toString());
            
            AiAnalysisReport report = AiAnalysisReport.builder()
                .targetType("STUDENT")
                .targetId(student.getId())
                .semester(currentSemester)
                .reportType(cacheKey)
                .analysisData(analysisData.toJSONString())
                .summary(aiResponse.getSummary())
                .suggestions(String.join("\n", aiResponse.getSuggestions()))
                .createdAt(LocalDateTime.now())
                .build();
            
            aiReportService.save(report);
            log.info("保存知识点 AI 报告成功，学生ID: {}, cacheKey: {}", student.getId(), cacheKey);
        } catch (Exception e) {
            log.error("保存知识点 AI 报告失败", e);
        }
    }
    
    /**
     * 从缓存报告解析 AI 分析
     */
    private AiSuggestionDTO parseKnowledgeAiAnalysisFromReport(AiAnalysisReport report) {
        AiSuggestionDTO analysis = new AiSuggestionDTO();
        analysis.setSummary(report.getSummary());
        
        List<String> suggestions = new ArrayList<>();
        if (report.getSuggestions() != null) {
            suggestions = Arrays.asList(report.getSuggestions().split("\n"));
        }
        analysis.setSuggestions(suggestions);
        
        // 从 summary 中提取强弱项
        String summary = report.getSummary();
        List<String> strengths = extractStrengthsFromSummary(summary);
        List<String> weaknesses = extractWeaknessesFromSummary(summary);
        
        analysis.setStrengths(strengths.isEmpty() ? Arrays.asList("待补充") : strengths);
        analysis.setWeaknesses(weaknesses.isEmpty() ? Arrays.asList("待补充") : weaknesses);
        
        return analysis;
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
    
    private AiSuggestionDTO createEmptyKnowledgeAiResponse() {
        AiSuggestionDTO analysis = new AiSuggestionDTO();
        analysis.setSummary("暂无知识点掌握数据，无法生成分析报告");
        analysis.setStrengths(new ArrayList<>());
        analysis.setWeaknesses(new ArrayList<>());
        analysis.setSuggestions(Arrays.asList("请先完成作业和考试"));
        return analysis;
    }
    
    private List<String> extractStrengthsFromSummary(String summary) {
        List<String> strengths = new ArrayList<>();
        if (summary == null) {
            strengths.add("暂无数据");
            return strengths;
        }
        if (summary.contains("优秀") || summary.contains("良好") || summary.contains("扎实")) {
            strengths.add("基础知识掌握较好");
            strengths.add("学习态度认真");
        } else if (summary.contains("进步")) {
            strengths.add("学习有进步");
        } else {
            strengths.add("有提升空间");
        }
        return strengths;
    }
    
    private List<String> extractWeaknessesFromSummary(String summary) {
        List<String> weaknesses = new ArrayList<>();
        if (summary == null) {
            weaknesses.add("暂无数据");
            return weaknesses;
        }
        if (summary.contains("薄弱") || summary.contains("不足") || summary.contains("需要加强")) {
            weaknesses.add("部分知识点掌握不牢固");
        } else {
            weaknesses.add("无明显薄弱点");
        }
        return weaknesses;
    }
    
    // ==================== 原有的私有辅助方法（保持不变） ====================
    
    private KnowledgePointTreeDTO buildCourseNode(Student student, Course course) {
        // ... 保持不变 ...
        KnowledgePointTreeDTO courseNode = new KnowledgePointTreeDTO();
        courseNode.setId(String.valueOf(course.getId()));
        courseNode.setLabel(getCourseIcon(course.getName()) + " " + course.getName());
        courseNode.setCourseId(course.getId());
        courseNode.setCourseName(course.getName());
        
        List<KnowledgePoint> knowledgePoints = knowledgePointService.findByCourse(course);
        
        List<KnowledgePointTreeDTO> children = new ArrayList<>();
        double totalMastery = 0;
        
        for (KnowledgePoint kp : knowledgePoints) {
            KnowledgePointTreeDTO child = buildKnowledgePointNode(student, kp);
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
    
    private KnowledgePointTreeDTO buildKnowledgePointNode(Student student, KnowledgePoint kp) {
        // ... 保持不变 ...
        KnowledgePointTreeDTO node = new KnowledgePointTreeDTO();
        node.setId(kp.getId().toString());
        node.setLabel(kp.getName());
        
        Optional<StudentKnowledgeMastery> mastery = masteryService.findByStudentAndKnowledgePoint(student, kp);
        BigDecimal masteryRate = BigDecimal.ZERO;
        
        if (mastery.isPresent()) {
            masteryRate = BigDecimal.valueOf(mastery.get().getMasteryLevel()).setScale(2, RoundingMode.HALF_UP);
        }
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
                trend.setSourceName("作业");
            } else if ("EXAM".equals(detail.getSourceType())) {
                trend.setSourceType("EXAM");
                trend.setSourceName("考试");
            }
            
            trendData.add(trend);
        }
        
        return trendData;
    }
    
    private List<KnowledgePointSourceDTO> getKnowledgePointSourceDetails(Long studentId, Long knowledgePointId) {
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
                source.setSourceName("作业");
            } else if ("EXAM".equals(detail.getSourceType())) {
                source.setSourceName("考试");
            }
            
            source.setClassAvgScoreRate(detail.getScoreRate());
            sourceDetails.add(source);
        }
        
        return sourceDetails;
    }
    
    private String getCourseIcon(String courseName) {
        if (courseName.contains("数学")) return "📐";
        if (courseName.contains("Java") || courseName.contains("编程")) return "☕";
        if (courseName.contains("数据库")) return "🗄️";
        if (courseName.contains("Web") || courseName.contains("前端")) return "🌐";
        if (courseName.contains("数据结构")) return "🌲";
        return "📚";
    }
    
    private Semester getCurrentSemester() {
        List<Semester> semesters = semesterService.findAll();
        return semesters.stream()
                .filter(Semester::getIsCurrent)
                .findFirst()
                .orElse(null);
    }
}