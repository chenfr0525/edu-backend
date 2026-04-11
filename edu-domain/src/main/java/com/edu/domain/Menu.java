package com.edu.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import javax.persistence.*;

@Data
@Entity
@Table(name = "menu")
@NoArgsConstructor
@AllArgsConstructor
public class Menu {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String role;

  private String name;

  private String path;

  private String component;

  private String icon;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "children_id")
  private Menu menu;

  private String meta;
}
