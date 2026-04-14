package com.edu.service;

import com.edu.domain.AiAnalysisReport;
import com.edu.domain.ClassInfo;
import com.edu.domain.Course;
import com.edu.domain.dto.ActivityMonitorDTO;
import com.edu.domain.dto.AiAnalysisReportDTO;
import com.edu.domain.dto.ClassScoreDistributionDTO;
import com.edu.domain.dto.DashboardStatsDTO;
import com.edu.domain.dto.TeachingDashboardDataDTO;
import com.edu.domain.dto.WeakKnowledgePointDTO;
import com.edu.domain.dto.WrongQuestionDTO;
import com.edu.repository.ActivityRecordRepository;
import com.edu.repository.AiAnalysisReportRepository;
import com.edu.repository.ClassRepository;
import com.edu.repository.CourseRepository;
import com.edu.repository.ExamGradeRepository;
import com.edu.repository.StudentKnowledgeMasteryRepository;
import com.edu.repository.StudentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiReportGenerationService {
   private final TeachingDashboardService dashboardService;
    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final CourseRepository courseRepository;
    private final ExamGradeRepository examGradeRepository;
    private final ActivityRecordRepository activityRecordRepository;
    private final StudentKnowledgeMasteryRepository masteryRepository;
    private final AiAnalysisReportService aiReportService;

    /**
     * 生成AI分析报告（班级或课程级别）
     */
    public AiAnalysisReportDTO generateReport(
            Long currentUserId, String userRole,
            Long classId, Long courseId, String reportType) {
        
        // 获取看板数据作为分析基础
        TeachingDashboardDataDTO dashboardData = dashboardService.getDashboardData(
            currentUserId, userRole, classId, courseId);
        
        // 生成分析摘要
        String summary = generateSummary(dashboardData, classId, courseId);
        
        // 生成建议
        String suggestions = generateSuggestions(dashboardData);
        
        // 关键发现
        List<String> keyFindings = extractKeyFindings(dashboardData);
        
        // 风险预警
        List<String> riskWarnings = extractRiskWarnings(dashboardData);
        
        // 行动建议
        List<String> actionItems = generateActionItems(dashboardData);
        
        // 获取目标名称
        String targetName = "";
        if (classId != null) {
            ClassInfo classInfo = classRepository.findById(classId).orElse(null);
            if (classInfo != null) targetName = classInfo.getName();
        } else if (courseId != null) {
            Course course = courseRepository.findById(courseId).orElse(null);
            if (course != null) targetName = course.getName();
        }
        
        // 保存到数据库
        AiAnalysisReport report = AiAnalysisReport.builder()
            .targetType(classId != null ? "CLASS" : "COURSE")
            .targetId(classId != null ? classId : courseId)
            .reportType(reportType)
            .analysisData(buildAnalysisData(dashboardData).toString())
            .suggestions(suggestions)
            .summary(summary)
            .createdAt(LocalDateTime.now())
            .build();
        
        aiReportService.save(report);
        
        return AiAnalysisReportDTO.builder()
            .reportId(report.getId())
            .targetType(report.getTargetType())
            .targetId(report.getTargetId())
            .targetName(targetName)
            .reportType(reportType)
            .summary(summary)
            .suggestions(suggestions)
            .keyFindings(keyFindings)
            .riskWarnings(riskWarnings)
            .actionItems(actionItems)
            .createdAt(report.getCreatedAt())
            .build();
    }

    /**
     * 生成分析摘要（模拟AI）
     */
    private String generateSummary(TeachingDashboardDataDTO data, Long classId, Long courseId) {
        DashboardStatsDTO stats = data.getStats();
        StringBuilder sb = new StringBuilder();
        
        if (classId != null) {
            sb.append("班级学情分析报告\n\n");
        } else if (courseId != null) {
            sb.append("课程学情分析报告\n\n");
        } else {
            sb.append("综合学情分析报告\n\n");
        }
        
        // 整体表现
        if (stats.getOverallAvgScore() >= 80) {
            sb.append("整体表现优秀，");
        } else if (stats.getOverallAvgScore() >= 70) {
            sb.append("整体表现良好，");
        } else if (stats.getOverallAvgScore() >= 60) {
            sb.append("整体表现中等，");
        } else {
            sb.append("整体表现有待提升，");
        }
        
        sb.append(String.format("平均分%.1f分，及格率%.1f%%。\n\n", 
            stats.getOverallAvgScore(), stats.getOverallPassRate()));
        
        // 成绩分布
        if (!data.getScoreDistributions().isEmpty()) {
            ClassScoreDistributionDTO dist = data.getScoreDistributions().get(0);
            sb.append("成绩分布：");
            sb.append(String.format("优秀%.1f%%，良好%.1f%%，中等%.1f%%，及格%.1f%%，不及格%.1f%%。\n\n",
                dist.getExcellentRate(), 
                (dist.getDistribution().getOrDefault("良好(80-89)", 0) * 100.0 / dist.getStudentCount()),
                (dist.getDistribution().getOrDefault("中等(70-79)", 0) * 100.0 / dist.getStudentCount()),
                dist.getPassRate(),
                100 - dist.getPassRate()
            ));
        }
        
        // 薄弱知识点
        if (!data.getWeakKnowledgePoints().isEmpty()) {
            sb.append("薄弱知识点分析：\n");
            for (int i = 0; i < Math.min(3, data.getWeakKnowledgePoints().size()); i++) {
                WeakKnowledgePointDTO weak = data.getWeakKnowledgePoints().get(i);
                sb.append(String.format("• %s：平均掌握度%.1f%%，影响%d名学生\n",
                    weak.getKnowledgePointName(), weak.getAvgMastery(), weak.getStudentCount()));
            }
            sb.append("\n");
        }
        
        // 活跃度
        ActivityMonitorDTO activity = data.getActivityMonitor();
        if (activity.getLowActivityCount() > 0) {
            sb.append(String.format("活跃度监控：%d名学生活跃度偏低，建议关注。\n",
                activity.getLowActivityCount()));
        } else {
            sb.append("活跃度监控：整体活跃度良好。\n");
        }
        
        return sb.toString();
    }

    /**
     * 生成建议
     */
    private String generateSuggestions(TeachingDashboardDataDTO data) {
        StringBuilder sb = new StringBuilder();
        
        // 基于薄弱知识点的建议
        if (!data.getWeakKnowledgePoints().isEmpty()) {
            sb.append("1. 薄弱知识点强化建议：\n");
            for (WeakKnowledgePointDTO weak : data.getWeakKnowledgePoints().stream()
                    .limit(3).collect(Collectors.toList())) {
                sb.append(String.format("   - %s：建议增加专项练习和讲解，针对%d名学生进行个别辅导\n",
                    weak.getKnowledgePointName(), weak.getWeakStudents().size()));
            }
            sb.append("\n");
        }
        
        // 基于成绩分布的建议
        if (!data.getScoreDistributions().isEmpty()) {
            ClassScoreDistributionDTO dist = data.getScoreDistributions().get(0);
            int failCount = dist.getDistribution().getOrDefault("不及格(<60)", 0);
            if (failCount > dist.getStudentCount() * 0.2) {
                sb.append("2. 后进生帮扶建议：\n");
                sb.append("   - 不及格人数比例超过20%，建议组织课后辅导班\n");
                sb.append("   - 建立学习小组，实行结对帮扶机制\n\n");
            }
        }
        
        // 基于活跃度的建议
        ActivityMonitorDTO activity = data.getActivityMonitor();
        if (activity.getLowActivityCount() > 0) {
            sb.append("3. 活跃度提升建议：\n");
            sb.append("   - 增加课堂互动环节，提高学生参与度\n");
            sb.append("   - 设置学习积分奖励机制\n");
            sb.append("   - 对低活跃度学生进行一对一沟通\n\n");
        }
        
        // 整体教学建议
        sb.append("4. 整体教学建议：\n");
        if (data.getStats().getOverallAvgScore() < 70) {
            sb.append("   - 建议放慢教学进度，加强基础知识的巩固\n");
            sb.append("   - 增加随堂测验，及时了解学生掌握情况\n");
        } else {
            sb.append("   - 可适当增加拓展内容，培养优等生\n");
            sb.append("   - 关注成绩波动较大的学生，及时干预\n");
        }
        
        return sb.toString();
    }

    /**
     * 提取关键发现
     */
    private List<String> extractKeyFindings(TeachingDashboardDataDTO data) {
        List<String> findings = new ArrayList<>();
        
        // 成绩分析
        if (!data.getScoreDistributions().isEmpty()) {
            ClassScoreDistributionDTO dist = data.getScoreDistributions().get(0);
            if (dist.getExcellentRate() > 30) {
                findings.add(String.format("优秀率%.1f%%，尖子生培养效果显著", dist.getExcellentRate()));
            }
            if (dist.getStandardDeviation() > 15) {
                findings.add("成绩标准差较大，班级两极分化明显");
            }
        }
        
        // 错题分析
        if (!data.getTopWrongQuestions().isEmpty()) {
            WrongQuestionDTO top = data.getTopWrongQuestions().get(0);
            findings.add(String.format("最高频错题：%s，错误率%.1f%%", 
                top.getKnowledgePointName(), top.getErrorRate()));
        }
        
        // 活跃度分析
        ActivityMonitorDTO activity = data.getActivityMonitor();
        if (activity.getActivityChange() > 5) {
            findings.add(String.format("本周活跃度较上周提升%.1f分，学习氛围改善", 
                activity.getActivityChange()));
        } else if (activity.getActivityChange() < -5) {
            findings.add(String.format("本周活跃度较上周下降%.1f分，需关注学习积极性", 
                Math.abs(activity.getActivityChange())));
        }
        
        return findings;
    }

    /**
     * 提取风险预警
     */
    private List<String> extractRiskWarnings(TeachingDashboardDataDTO data) {
        List<String> warnings = new ArrayList<>();
        
        // 成绩风险
        if (!data.getScoreDistributions().isEmpty()) {
            ClassScoreDistributionDTO dist = data.getScoreDistributions().get(0);
            int failCount = dist.getDistribution().getOrDefault("不及格(<60)", 0);
            if (failCount > 0) {
                warnings.add(String.format("⚠️ %d名学生不及格，需重点关注", failCount));
            }
        }
        
        // 薄弱知识点风险
        if (!data.getWeakKnowledgePoints().isEmpty()) {
            WeakKnowledgePointDTO weakest = data.getWeakKnowledgePoints().get(0);
            if (weakest.getAvgMastery() < 50) {
                warnings.add(String.format("⚠️ %s掌握度仅%.1f%%，全班普遍薄弱", 
                    weakest.getKnowledgePointName(), weakest.getAvgMastery()));
            }
        }
        
        // 活跃度风险
        ActivityMonitorDTO activity = data.getActivityMonitor();
        if (activity.getCriticalAlertCount() > 0) {
            warnings.add(String.format("⚠️ %d个严重预警，涉及学生可能存在学习动力不足", 
                activity.getCriticalAlertCount()));
        }
        if (activity.getLowActivityCount() > activity.getActiveStudentCount() * 0.3) {
            warnings.add("⚠️ 低活跃度学生比例超过30%，整体学习参与度偏低");
        }
        
        return warnings;
    }

    /**
     * 生成行动建议
     */
    private List<String> generateActionItems(TeachingDashboardDataDTO data) {
        List<String> actions = new ArrayList<>();
        
        // 基于薄弱知识点
        if (!data.getWeakKnowledgePoints().isEmpty()) {
            WeakKnowledgePointDTO weakest = data.getWeakKnowledgePoints().get(0);
            actions.add(String.format("本周重点讲解%s，安排专题练习", weakest.getKnowledgePointName()));
        }
        
        // 基于成绩
        if (!data.getScoreDistributions().isEmpty()) {
            ClassScoreDistributionDTO dist = data.getScoreDistributions().get(0);
            if (dist.getDistribution().getOrDefault("不及格(<60)", 0) > 0) {
                actions.add("组织一次后进生座谈会，了解学习困难");
                actions.add("安排课后辅导时间，每周至少2小时");
            }
        }
        
        // 基于活跃度
        ActivityMonitorDTO activity = data.getActivityMonitor();
        if (activity.getLowActivityCount() > 0) {
            actions.add("对低活跃度学生进行一对一沟通");
            actions.add("增加课堂互动环节，设置学习积分");
        }
        
        // 通用行动
        actions.add("准备下周单元测验，检验教学效果");
        
        return actions;
    }

    /**
     * 构建分析数据JSON
     */
    private Map<String, Object> buildAnalysisData(TeachingDashboardDataDTO data) {
        Map<String, Object> analysisData = new HashMap<>();
        analysisData.put("stats", data.getStats());
        analysisData.put("scoreDistributions", data.getScoreDistributions());
        analysisData.put("topWrongQuestions", data.getTopWrongQuestions());
        analysisData.put("weakKnowledgePoints", data.getWeakKnowledgePoints());
        analysisData.put("activityMonitor", data.getActivityMonitor());
        analysisData.put("generatedAt", LocalDateTime.now());
        return analysisData;
    }

    /**
     * 预留AI大模型调用接口
     * 后续可以接入大模型API生成更智能的分析报告
     */
    private String callAiModel(String prompt, TeachingDashboardDataDTO data) {
        // TODO: 接入AI大模型
        // 例如：OpenAI API、文心一言、通义千问等
        // 目前返回模拟数据
        log.info("调用AI大模型，prompt: {}", prompt);
        return generateMockAiResponse(data);
    }

    /**
     * 模拟AI响应（后续替换为真实API调用）
     */
    private String generateMockAiResponse(TeachingDashboardDataDTO data) {
        // 这里可以返回更丰富的分析内容
        return "基于数据分析，班级整体学习情况良好，建议关注薄弱知识点的强化教学。";
    }
}
