package com.edu.repository;

import com.edu.domain.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuRepository extends JpaRepository<Menu, Long> {
  List<Menu> findByRoleOrderByIdAsc(String role);
}
