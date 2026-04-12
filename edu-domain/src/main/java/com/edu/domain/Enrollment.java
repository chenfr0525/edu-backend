package com.edu.domain;

import java.time.LocalDateTime;

import javax.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "enrollment")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Enrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course;

      @ManyToOne
    @JoinColumn(name = "semester_id")
    private Semester semester;

    private Integer progress = 0; // 0-100

    private Double score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourseStatus status = CourseStatus.ONGOING;

    @Column(name = "enrolled_at")
    private LocalDateTime enrolledAt;
    @PrePersist
    protected void onCreate() {
        enrolledAt = LocalDateTime.now();
    }
}
