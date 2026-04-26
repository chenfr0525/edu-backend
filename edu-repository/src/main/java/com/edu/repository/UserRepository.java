package com.edu.repository;

import com.edu.domain.Role;
import com.edu.domain.User;
import com.edu.domain.UserStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    List<User> findByName(String name);

    Optional<User> findById(Long id);

    

    boolean existsByUsername(String username);
    boolean existsByName(String name);
   
     /**
     * 统计某角色且某状态的人数
     */
    long countByRoleAndStatus(Role role, UserStatus status);
    
    /**
     * 统计某角色的人数
     */
    long countByRole(Role role);
}
