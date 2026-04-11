package com.edu.domain;

import javax.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "exam")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Exam {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
     @Enumerated(EnumType.STRING)
    private ExamStatus type=ExamStatus.MOCK;

    @ManyToOne
    @JoinColumn(name = "class_id")
    private ClassInfo classInfo;

    @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course;

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
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
