package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.stereotype.Service;

import com.edu.domain.ClassInfo;
import com.edu.repository.ClassRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClassService {
  private final ClassRepository classRepository;
  
  /**
   * 获取所有班级
   */
  public List<ClassInfo> getAllClasses() {
    return classRepository.findAll();
  }

  /**
   * 获取指定班级
   */
  public ClassInfo getClassByName(String name) {
    return classRepository.findByName(name).orElse(null);
  }

  /**
   * 获取指定年级的班级
   */
  public List<ClassInfo> getClassesByGrade(String grade) {
    return classRepository.findAllByGrade(grade);
  }

}
