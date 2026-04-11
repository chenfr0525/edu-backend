package com.edu.domain.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;

import com.edu.domain.Menu;

import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuDTO {
  private Long id;
  private String name;
  private String path;
  private String component;
  private String icon;
  private String meta;
  private MenuDTO children;
  private String role;

  public MenuDTO(Menu menu) {
    this.id = menu.getId();
    this.name = menu.getName();
    this.path = menu.getPath();
    this.component = menu.getComponent();
    this.icon = menu.getIcon();
    this.meta = menu.getMeta();
    this.role = menu.getRole();
    if (menu.getMenu() != null) {
      this.children = new MenuDTO(menu.getMenu());
    }
  }
}
