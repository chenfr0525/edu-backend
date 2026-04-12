package com.edu.domain;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import javax.persistence.*;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;

@Entity
@Table(name = "activity_alert")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

     @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;
    

     @ManyToOne
    @JoinColumn(name = "class_id", nullable = false)
    private ClassInfo classInfo;

     @ManyToOne
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;
    
    /**
     * 预警类型: LOW_ACTIVITY/DECLINING_TREND/NO_SUBMISSION
     */
    @Column(name = "alert_type")
    private String alertType;
    
    /**
     * 预警级别: INFO/WARNING/CRITICAL
     */
    @Column(name = "alert_level")
    private String alertLevel;

    @Column(name = "activity_score")
    private BigDecimal activityScore;
    
    @Column(name = "threshold")
    private BigDecimal threshold;
    
    @Column(name = "details", columnDefinition = "JSON")
    private String details;

    @Column(name = "is_resolved")
    private Boolean isResolved = false;
    
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    
    @Column(name = "created_at")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
