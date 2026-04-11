package com.edu.repository;

import com.edu.domain.Student;
import com.edu.domain.Exam;
import com.edu.domain.ExamGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExamGradeRepository extends JpaRepository<ExamGrade, Long> {
  
  List<ExamGrade> findByExamAndStudent(Exam exam, Student student);

  List<ExamGrade> findByExamInAndStudent(List<Exam> exams, Student student);

  List<ExamGrade> findByExamInAndStudentIn(List<Exam> exams, List<Student> students);
}
