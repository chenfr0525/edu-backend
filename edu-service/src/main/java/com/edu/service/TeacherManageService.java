// StudentManageService.java
package com.edu.service;

import com.edu.domain.*;
import com.edu.domain.dto.*;
import com.edu.repository.*;
import org.springframework.data.domain.PageImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeacherManageService {
   private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
  
     /**
     * 分页查询教师列表
     */
    @Transactional(readOnly = true)
    public Page<TeacherDTO> getTeacherList(TeacherListRequest request) {
        Pageable pageable = PageRequest.of(
            request.getPage() != null ? request.getPage() : 0,
            request.getSize() != null ? request.getSize() : 10
        );
        
        String keyword = request.getKeyword();
        String department = request.getDepartment();
        
        Page<Teacher> teacherPage;
        
        if (keyword != null && !keyword.isEmpty()) {
            // 关键词搜索：按姓名、工号、用户名搜索
            teacherPage = teacherRepository.findByKeyword(keyword, pageable);
        } else if (department != null && !department.isEmpty()) {
            teacherPage = teacherRepository.findByDepartment(department, pageable);
        } else {
            teacherPage = teacherRepository.findAll(pageable);
        }
        
        // 转换DTO
        List<TeacherDTO> dtoList = teacherPage.getContent().stream()
            .map(teacher -> new TeacherDTO(teacher, teacher.getUser()))
            .collect(Collectors.toList());
        
        return new PageImpl<>(dtoList, pageable, teacherPage.getTotalElements());
    }

    /**
     * 获取教师详情
     */
    @Transactional(readOnly = true)
    public TeacherDTO getTeacherById(Long id) {
        Teacher teacher = teacherRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("教师不存在，ID: " + id));
        return new TeacherDTO(teacher, teacher.getUser());
    }

    /**
     * 创建教师
     */
    @Transactional
    public TeacherDTO createTeacher(TeacherDTO dto) {
        log.info("创建教师: {}", dto);
        
        // 参数校验
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new RuntimeException("姓名不能为空");
        }
        if (dto.getTeacherNo() == null || dto.getTeacherNo().trim().isEmpty()) {
            throw new RuntimeException("工号不能为空");
        }
        if (dto.getUsername() == null || dto.getUsername().trim().isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }
        
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new RuntimeException("用户名已存在: " + dto.getUsername());
        }
        
        // 检查工号是否已存在
        if (teacherRepository.existsByTeacherNo(dto.getTeacherNo())) {
            throw new RuntimeException("工号已存在: " + dto.getTeacherNo());
        }
        
        // 创建用户
        User user = User.builder()
            .username(dto.getUsername())
            .password(passwordEncoder.encode("123456")) // 默认密码
            .name(dto.getName())
            .role(Role.TEACHER)//默认为教师
            .status(UserStatus.ACTIVE)
            .gender(dto.getGender())
            .email(dto.getEmail())
            .phone(dto.getPhone())
            .avatar(dto.getAvatar())
            .build();
        User savedUser = userRepository.save(user);
        
        // 创建教师
        Teacher teacher = new Teacher();
        teacher.setUser(savedUser);
        teacher.setTeacherNo(dto.getTeacherNo());
        teacher.setDepartment(dto.getDepartment());
        teacher.setTitle(dto.getTitle());
        teacher.setOffice(dto.getOffice());
        if (dto.getJoinDate() != null) {
            teacher.setJoinDate(dto.getJoinDate().atStartOfDay());
        }
        
        Teacher savedTeacher = teacherRepository.save(teacher);
        log.info("教师创建成功: id={}, name={}", savedTeacher.getId(), savedTeacher.getUser().getName());
        
        return new TeacherDTO(savedTeacher, savedUser);
    }

    /**
     * 更新教师
     */
    @Transactional
    public TeacherDTO updateTeacher(Long id, TeacherDTO dto) {
        log.info("更新教师, ID: {}", id);
        
        Teacher teacher = teacherRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("教师不存在，ID: " + id));
        User user = teacher.getUser();
        
        // 更新用户名（如果提供且改变）
        if (dto.getUsername() != null && !dto.getUsername().trim().isEmpty()) {
            if (!user.getUsername().equals(dto.getUsername())) {
                if (userRepository.existsByUsername(dto.getUsername())) {
                    throw new RuntimeException("用户名已存在: " + dto.getUsername());
                }
                user.setUsername(dto.getUsername());
            }
        }
        
        // 更新姓名
        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            user.setName(dto.getName());
        }
        
        // 更新工号（如果提供且改变）
        if (dto.getTeacherNo() != null && !dto.getTeacherNo().trim().isEmpty()) {
            if (!teacher.getTeacherNo().equals(dto.getTeacherNo())) {
                if (teacherRepository.existsByTeacherNo(dto.getTeacherNo())) {
                    throw new RuntimeException("工号已存在: " + dto.getTeacherNo());
                }
                teacher.setTeacherNo(dto.getTeacherNo());
            }
        }
        
        // 更新其他字段
        if (dto.getGender() != null) {
            user.setGender(dto.getGender());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        if (dto.getDepartment() != null) {
            teacher.setDepartment(dto.getDepartment());
        }
        if (dto.getTitle() != null) {
            teacher.setTitle(dto.getTitle());
        }
        if (dto.getOffice() != null) {
            teacher.setOffice(dto.getOffice());
        }
        if (dto.getJoinDate() != null ) {
             teacher.setJoinDate(dto.getJoinDate().atStartOfDay());
        }
        
        userRepository.save(user);
        Teacher savedTeacher = teacherRepository.save(teacher);
        
        return new TeacherDTO(savedTeacher, user);
    }

    /**
     * 将教师转为管理员
     */
    @Transactional
    public TeacherDTO promoteToAdmin(Long id) {
        log.info("将教师转为管理员, ID: {}", id);
        
        Teacher teacher = teacherRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("教师不存在，ID: " + id));
        User user = teacher.getUser();
        
        user.setRole(Role.ADMIN);
        userRepository.save(user);
        
        log.info("教师已转为管理员: id={}, name={}", teacher.getId(), user.getName());
        
        return new TeacherDTO(teacher, user);
    }

    /**
     * 删除教师
     */
    @Transactional
    public void deleteTeacher(Long id) {
        log.info("删除教师, ID: {}", id);
        
        Teacher teacher = teacherRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("教师不存在，ID: " + id));
        User user = teacher.getUser();
        
        // 先解除关联
        teacher.setUser(null);
        teacherRepository.save(teacher);
        
        // 删除 Teacher
        teacherRepository.delete(teacher);
        
        // 删除 User
        if (user != null) {
            userRepository.delete(user);
        }
        
        log.info("教师删除成功: id={}", id);
    }

    /**
     * 重置密码
     */
    @Transactional
    public void resetPassword(Long id) {
        log.info("重置教师密码, ID: {}", id);
        
        Teacher teacher = teacherRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("教师不存在，ID: " + id));
        User user = teacher.getUser();
        
        user.setPassword(passwordEncoder.encode("123456"));
        userRepository.save(user);
        
        log.info("教师密码重置成功: id={}", id);
    }

    /**
     * 获取统计卡片数据
     */
    @Transactional(readOnly = true)
    public TeacherManageStatsVO getStats() {
        long totalTeacherCount = teacherRepository.count();
        long activeTeacherCount = userRepository.countByRoleAndStatus(Role.TEACHER, UserStatus.ACTIVE);
        long adminCount = userRepository.countByRole(Role.ADMIN);
        
        // 各院系教师数量统计
        List<Object[]> departmentStats = teacherRepository.countGroupByDepartment();
        
        // 各职称教师数量统计
        List<Object[]> titleStats = teacherRepository.countGroupByTitle();
        
        return TeacherManageStatsVO.builder()
            .totalTeacherCount(totalTeacherCount)
            .activeTeacherCount(activeTeacherCount)
            .adminCount(adminCount)
            .departmentStats(departmentStats)
            .titleStats(titleStats)
            .build();
    }
}