package com.edu.service;

import com.edu.domain.*;
import com.edu.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class TeacherServiceTest {

    @Mock
    private ClassRepository classRepository;
    @Mock
    private ExamRepository examRepository;
    @Mock
    private ErrorRecordRepository errorRecordRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TeacherService teacherService;

    private Exam exam;

    @BeforeEach
    void setUp() {
        exam = new Exam();
        exam.setId(1L);
        exam.setName("Test Exam");
    }
}
