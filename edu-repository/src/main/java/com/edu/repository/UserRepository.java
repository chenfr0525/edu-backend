package com.edu.repository;

import com.edu.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    List<User> findByName(String name);

    Optional<User> findById(Long id);

    

    boolean existsByUsername(String username);
    boolean existsByName(String name);
   
}
