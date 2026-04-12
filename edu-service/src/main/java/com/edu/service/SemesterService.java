package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import org.springframework.stereotype.Service;

import com.edu.domain.Semester;
import com.edu.repository.SemesterRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class SemesterService {
  private final SemesterRepository semesterRepository;

  public List<Semester> findAll() {
    return semesterRepository.findAll();
  }
}
