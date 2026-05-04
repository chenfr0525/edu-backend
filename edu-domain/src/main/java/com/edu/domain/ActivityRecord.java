package com.edu.domain;

import javax.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(name = "type")
      @Enumerated(EnumType.STRING)
    private ActivityStatus type = ActivityStatus.LOGIN;

      @Column(columnDefinition = "TEXT")
    private String description;

      @Column(name = "activity_date")
    private LocalDateTime activityDate;

    @Column(name = "study_duration")
    private Integer studyDuration;

    @Column(name = "activity_score")
    private BigDecimal activityScore;

    @Column(name = "interaction_count")
    private Integer interactionCount;

    @Column(name = "resource_access_count")
    private Integer resourceAccessCount;

     @PrePersist
    protected void onCreate() {
        activityDate = LocalDateTime.now();
    }
}
