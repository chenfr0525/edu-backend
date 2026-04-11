package com.edu.domain;

import java.util.List;

import javax.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "knowledge_point")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgePoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

     @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course;

      @ManyToOne
    @JoinColumn(name = "parent_id")
    private KnowledgePoint parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<KnowledgePoint> children;

    private Integer level;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
