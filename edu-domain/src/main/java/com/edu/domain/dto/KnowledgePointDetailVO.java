package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class KnowledgePointDetailVO {
  private Long id;
    private String name;
    private String description;
    private Long courseId;
    private String courseName;
    private Long parentId;
    private String parentName;
    private Integer level;
    private Integer sortOrder;
    
    // 统计分析
    private KnowledgePointStatsVO stats;
    
    // 学生掌握度分布
    private List<StudentMasteryVO> studentMasteryList;
    
    // 历史趋势
    private List<MasteryTrendVO> masteryTrend;
    
    // 相关错题
    private List<RelatedWrongQuestionVO> relatedWrongQuestions;
    
    // 教学建议
    private String teachingSuggestion;
}
