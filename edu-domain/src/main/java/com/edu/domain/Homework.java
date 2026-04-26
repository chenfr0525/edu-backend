package com.edu.domain;

import com.vladmihalcea.hibernate.type.json.JsonType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "homework")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
// 定义 JSON 类型转换器
@org.hibernate.annotations.TypeDef(name = "json", typeClass = JsonType.class)
public class Homework {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // ========== JSON 字段：使用 Hibernate 的 @Type 注解 ==========
    @Type(type = "json")
    @Column(columnDefinition = "json")
    private List<Long> knowledgePointIds;

    // ========== 关联关系 ==========
    @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course;

    // ========== 普通字段 ==========
    @Column(name = "question_count")
    private Integer questionCount;

    @Column(name = "total_score")
    private Integer totalScore;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private HomeworkStatus status = HomeworkStatus.ONGOING;

    @Column(name = "deadline")
    private LocalDateTime deadline;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // ========== JSON 字段（AI 相关） ==========
    @Type(type = "json")
    @Column(columnDefinition = "json")
    private Object aiParsedData;  // 用 Object 或 Map<String, Object>

    @Type(type = "json")
    @Column(columnDefinition = "json")
    private Object knowledgePointsMapping;

    // ========== 普通统计字段 ==========
    @Column(name = "avg_score")
    private BigDecimal avgScore;

    @Column(name = "pass_rate")
    private BigDecimal passRate;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}