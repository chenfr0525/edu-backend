package com.edu.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import javax.persistence.*;
import java.time.LocalDateTime;

@Builder
@Data
@Entity
@Table(name = "student")
@NoArgsConstructor
@AllArgsConstructor
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

     @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "student_no", unique = true, nullable = false)
    private String studentNo;

     @OneToOne
    @JoinColumn(name = "class_id")
    private ClassInfo classInfo;


    private String grade;
}