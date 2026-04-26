package com.edu.repository;

import com.edu.domain.ClassInfo;
import com.edu.domain.Course;
import com.edu.domain.Student;
import com.edu.domain.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    
    // 根据userid查询
    Optional<Student> findByUserId(Long userId);

    Optional<Student> findByUser(User user);

      // 根据年级查询
    List<Student> findByGrade(String grade);

    @Query("SELECT s FROM Student s JOIN Enrollment e ON s.id = e.student.id WHERE e.course.id = :courseId")
    List<Student> findStudentsByCourseId(@Param("courseId") Long courseId);

     List<Student> findByClassInfo(ClassInfo classInfo);
    //根据id查询
     Optional<Student> findById(Long id);

      // 根据学号查询
    Optional<Student> findByStudentNo(String studentNo);

    // 查询班级学生数量
    long countByClassInfo(ClassInfo classInfo);
    
   
     Page<Student> findByUserNameContaining(String userName, Pageable pageable);

    Page<Student> findAll(Pageable pageable);
    
    // 根据班级查询
    Page<Student> findByClassName(String className, Pageable pageable);
    // 检查学号是否存在
    boolean existsByStudentNo(String studentNo);

    @Query("SELECT MAX(s.studentNo) FROM Student s")
    String findMaxStudentNo();

    // 查询低活跃度学生（自定义SQL）
    @Query("SELECT s FROM Student s WHERE s.id IN " +
           "(SELECT a.student.id FROM ActivityRecord a GROUP BY a.student.id HAVING COUNT(a) < :threshold)")
    List<Student> findLowActivityStudents(@Param("threshold") int threshold);

    /**
     * 获取选修某课程的所有学生
     */
    @Query("SELECT DISTINCT e.student FROM Enrollment e WHERE e.course = :course")
    List<Student> findByEnrollmentsCourse(@Param("course") Course course);
    
    /**
     * 统计选修某课程的学生数量
     */
    @Query("SELECT COUNT(DISTINCT e.student) FROM Enrollment e WHERE e.course = :course")
    long countByEnrollmentsCourse(@Param("course") Course course);
/**
 * 根据班级列表查询学生（分页）
 */
@Query("SELECT s FROM Student s WHERE s.classInfo.id IN :classIds")
Page<Student> findByClassIds(@Param("classIds") List<Long> classIds, Pageable pageable);

/**
 * 根据班级列表和关键词模糊查询
 */
@Query("SELECT s FROM Student s WHERE s.classInfo.id IN :classIds " +
       "AND (s.studentNo LIKE %:keyword% " +
       "OR s.user.name LIKE %:keyword% " +
       "OR s.user.username LIKE %:keyword%)")
Page<Student> findByClassIdsAndKeyword(@Param("classIds") List<Long> classIds, 
                                        @Param("keyword") String keyword, 
                                        Pageable pageable);

/**
 * 根据课程查询学生（通过选课关联）
 */
@Query("SELECT DISTINCT s FROM Student s " +
       "JOIN Enrollment e ON e.student = s " +
       "WHERE e.course.id = :courseId")
Page<Student> findByCourseId(@Param("courseId") Long courseId, Pageable pageable);

/**
 * 根据课程和关键词模糊查询
 */
@Query("SELECT DISTINCT s FROM Student s " +
       "JOIN Enrollment e ON e.student = s " +
       "WHERE e.course.id = :courseId " +
       "AND (s.studentNo LIKE %:keyword% " +
       "OR s.user.name LIKE %:keyword% " +
       "OR s.user.username LIKE %:keyword%)")
Page<Student> findByCourseIdAndKeyword(@Param("courseId") Long courseId,
                                        @Param("keyword") String keyword,
                                        Pageable pageable);

/**
 * 统计活跃学生数（近7天有活动记录）
 */
@Query("SELECT COUNT(DISTINCT a.student) FROM ActivityRecord a " +
       "WHERE a.activityDate >= :startDate")
Long countActiveStudents(@Param("startDate") LocalDate startDate);

/**
 * 统计低活跃度学生（活跃度得分低于阈值）
 */
@Query("SELECT COUNT(DISTINCT a.student) FROM ActivityRecord a " +
       "GROUP BY a.student " +
       "HAVING COALESCE(SUM(a.activityScore), 0) < :threshold")
Long countLowActivityStudents(@Param("threshold") Double threshold);

/**
 * 统计有薄弱知识点的学生数
 */
@Query("SELECT COUNT(DISTINCT skm.student) FROM StudentKnowledgeMastery skm " +
       "WHERE skm.masteryLevel < 60")
Long countStudentsWithWeakPoints();

/**
 * 统计男生/女生人数
 */
@Query("SELECT COUNT(s) FROM Student s WHERE s.user.gender = '男'")
Long countMaleStudents();

@Query("SELECT COUNT(s) FROM Student s WHERE s.user.gender = '女'")
Long countFemaleStudents();

/**
 * 获取所有学生（用于管理员统计）
 */
@Query("SELECT s FROM Student s")
List<Student> findAllStudents();

/**
 * 根据班级ID列表获取所有学生（用于统计）
 */
@Query("SELECT s FROM Student s WHERE s.classInfo.id IN :classIds")
List<Student> findAllByClassIds(@Param("classIds") List<Long> classIds);

/**
 * 根据课程获取选课学生
 */
@Query("SELECT DISTINCT e.student FROM Enrollment e WHERE e.course.id = :courseId")
List<Student> findByEnrollmentsCourse(@Param("courseId") Long courseId);
}