package com.edu.repository;

import com.edu.domain.Homework;
import com.edu.domain.HomeworkStatus;
import com.edu.domain.KnowledgePoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.edu.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface HomeworkRepository extends JpaRepository<Homework, Long> {
    // 根据单个课程查询
    List<Homework> findByCourse(Course course);
    
    // 根据多个课程查询（这就是你需要的）
    List<Homework> findByCourseIn(List<Course> courses);
    
    // 根据课程ID列表查询（更高效，避免查询Course对象）
    List<Homework> findByCourseIdIn(List<Long> courseIds);
    
    // 根据知识点查询
    List<Homework> findByKnowledgePoint(KnowledgePoint knowledgePoint);
    
    // 根据状态查询
    List<Homework> findByStatus(HomeworkStatus status);
    
    // 根据课程和状态查询
    List<Homework> findByCourseAndStatus(Course course, HomeworkStatus status);
    
    // 根据截止日期范围查询
    List<Homework> findByDeadlineBefore(LocalDateTime deadline);
    
    // 分页查询某课程下的作业
    Page<Homework> findByCourse(Course course, Pageable pageable);
}
