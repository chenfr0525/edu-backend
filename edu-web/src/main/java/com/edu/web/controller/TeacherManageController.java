package com.edu.web.controller;

import com.edu.domain.dto.TeacherDTO;
import com.edu.domain.dto.TeacherListRequest;
import com.edu.domain.dto.TeacherManageStatsVO;
import com.edu.service.TeacherManageService;
import com.edu.common.Result;
import com.edu.common.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teacher-manage")
@RequiredArgsConstructor
@Slf4j
public class TeacherManageController {
   private final TeacherManageService teacherManageService;

    /**
     * 获取教师列表（分页，支持筛选）
     * POST /api/teacher-manage/list
     */
    @PostMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResult<TeacherDTO>> getTeacherList(@RequestBody TeacherListRequest request) {
        Page<TeacherDTO> teacherPage = teacherManageService.getTeacherList(request);
        return Result.success(PageResult.of(teacherPage));
    }

    /**
     * 获取统计卡片数据
     * GET /api/teacher-manage/stats
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<TeacherManageStatsVO> getStats() {
        TeacherManageStatsVO stats = teacherManageService.getStats();
        return Result.success(stats);
    }

    /**
     * 获取教师详情
     * GET /api/teacher-manage/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<TeacherDTO> getTeacherById(@PathVariable Long id) {
        TeacherDTO teacher = teacherManageService.getTeacherById(id);
        return Result.success(teacher);
    }

    /**
     * 创建教师
     * POST /api/teacher-manage
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<TeacherDTO> createTeacher(@RequestBody TeacherDTO dto) {
        TeacherDTO teacher = teacherManageService.createTeacher(dto);
        return Result.success(teacher);
    }

    /**
     * 更新教师
     * PUT /api/teacher-manage/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<TeacherDTO> updateTeacher(@PathVariable Long id, @RequestBody TeacherDTO dto) {
        TeacherDTO teacher = teacherManageService.updateTeacher(id, dto);
        return Result.success(teacher);
    }

    /**
     * 将教师转为管理员
     * POST /api/teacher-manage/{id}/promote-admin
     */
    @PostMapping("/{id}/promote-admin")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<TeacherDTO> promoteToAdmin(@PathVariable Long id) {
        TeacherDTO teacher = teacherManageService.promoteToAdmin(id);
        return Result.success(teacher);
    }

    /**
     * 删除教师
     * DELETE /api/teacher-manage/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteTeacher(@PathVariable Long id) {
        teacherManageService.deleteTeacher(id);
        return Result.success(null);
    }

    /**
     * 重置密码
     * POST /api/teacher-manage/{id}/reset-password
     */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> resetPassword(@PathVariable Long id) {
        teacherManageService.resetPassword(id);
        return Result.success(null);
    }
}
