package com.edu.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "teacher")
@NoArgsConstructor
@AllArgsConstructor
public class Teacher {
  @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "teacher_no", unique = true, nullable = false)
    private String teacherNo;

    private String department;

    private String title;

    private String office;

    @Column(name = "join_date")
      private LocalDateTime joinDate;

    private String grade;
}