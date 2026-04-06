package com.edu.service;

import com.edu.domain.*;
import com.edu.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeacherServiceTest {

    @Mock
    private ClassRepository classRepository;
    @Mock
    private ExamRepository examRepository;
    @Mock
    private GradeRepository gradeRepository;
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

    @Test
    void getHighFrequencyErrors_ShouldHandleNullQuestion() {
        // Arrange
        Long classId = 1L;
        Long examId = 1L;

        ErrorRecord errorWithNullQuestion = new ErrorRecord();
        errorWithNullQuestion.setQuestion(null);

        Question q1 = new Question();
        q1.setId(101L);
        ErrorRecord errorWithQuestion = new ErrorRecord();
        errorWithQuestion.setQuestion(q1);

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(errorRecordRepository.findByExam(exam)).thenReturn(Arrays.asList(errorWithNullQuestion, errorWithQuestion));

        // Act
        Map<String, Object> result = teacherService.getHighFrequencyErrors(classId, examId);

        // Assert
        assertNotNull(result);
        List<String> questions = (List<String>) result.get("questions");
        assertEquals(1, questions.size());
        assertEquals("第101题", questions.get(0));
    }
}
