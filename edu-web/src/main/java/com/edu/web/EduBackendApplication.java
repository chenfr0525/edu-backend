package com.edu.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.edu"})
@EntityScan(basePackages = {"com.edu.domain"})
@EnableJpaRepositories(basePackages = {"com.edu.repository"})
public class EduBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(EduBackendApplication.class, args);
    }
}
