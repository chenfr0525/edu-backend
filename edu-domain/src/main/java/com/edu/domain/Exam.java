package com.edu.domain;

import javax.persistence.*;

import com.vladmihalcea.hibernate.type.json.JsonType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "exam")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@org.hibernate.annotations.TypeDef(name = "json", typeClass = JsonType.class)
public class Exam {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ExamStatus type = ExamStatus.MOCK;

    @ManyToOne
    @JoinColumn(name = "class_id", nullable = true)
    private ClassInfo classInfo;

    @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course;

    // ========== 修改：JSON 字段：知识点ID列表 ==========
    @Type(type = "json")
    @Column(columnDefinition = "json")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> knowledgePointIds;

    @Column(name = "exam_date")
    private LocalDateTime examDate;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    private Integer duration;

    @Column(name = "full_score")
    private Integer fullScore;

    @Column(name = "pass_score")
    private Integer passScore;

    private String location;

    @Enumerated(EnumType.STRING)
    private ExamStatus status = ExamStatus.UPCOMING;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // ========== 修改：AI 解析数据 JSON 字段 ==========
    @Type(type = "json")
    @Column(columnDefinition = "json")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object aiParsedData;  // 改为 Object 或 Map<String, Object>

    // ========== 修改：知识点分布 JSON 字段 ==========
    @Type(type = "json")
    @Column(columnDefinition = "json")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object knowledgePointsDistribution;  // 改为 Object 或 Map<String, Object>

    @Column(name = "class_avg_score")
    private BigDecimal classAvgScore;

    @Column(name = "highest_score")
    private BigDecimal highestScore;

    @Column(name = "lowest_score")
    private BigDecimal lowestScore;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}