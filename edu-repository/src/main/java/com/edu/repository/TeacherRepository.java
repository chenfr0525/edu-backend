package com.edu.repository;

import com.edu.domain.Teacher;
import com.edu.domain.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.query.Param;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {
   // 根据工号查询
    Optional<Teacher> findByTeacherNo(String teacherNo);
    
    // 根据user查询
    Optional<Teacher> findByUser(User user);
    
    // 根据用户姓名查询
    @Query("SELECT t FROM Teacher t WHERE t.user.name = :name")
    Optional<Teacher> findByUserName(@Param("name") String name);
    
    // 分页模糊查询
    @Query("SELECT t FROM Teacher t WHERE t.user.name LIKE %:name%")
    Page<Teacher> findByUserNameContaining(@Param("name") String name, Pageable pageable);
    
    // 检查工号是否存在
    boolean existsByTeacherNo(String teacherNo);
    
    // 查询最大的教师编号
    @Query("SELECT MAX(t.teacherNo) FROM Teacher t")
    String findMaxTeacherNo();
   
    /**
     * 关键词搜索（姓名、工号、用户名）
     */
    @Query("SELECT t FROM Teacher t WHERE " +
           "t.user.name LIKE %:keyword% OR " +
           "t.teacherNo LIKE %:keyword% OR " +
           "t.user.username LIKE %:keyword%")
    Page<Teacher> findByKeyword(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 按院系查询
     */
    Page<Teacher> findByDepartment(String department, Pageable pageable);
    
    /**
     * 按用户状态查询
     */
    @Query("SELECT t FROM Teacher t WHERE t.user.status = :status")
    Page<Teacher> findByUserStatus(@Param("status") String status, Pageable pageable);
    
    /**
     * 按院系统计教师数量
     */
    @Query("SELECT t.department, COUNT(t) FROM Teacher t WHERE t.department IS NOT NULL GROUP BY t.department")
    List<Object[]> countGroupByDepartment();
    
    /**
     * 按职称统计教师数量
     */
    @Query("SELECT t.title, COUNT(t) FROM Teacher t WHERE t.title IS NOT NULL GROUP BY t.title")
    List<Object[]> countGroupByTitle();
    
    /**
     * 获取所有教师（用于管理员统计）
     */
    @Query("SELECT t FROM Teacher t")
    List<Teacher> findAllTeachers();
}