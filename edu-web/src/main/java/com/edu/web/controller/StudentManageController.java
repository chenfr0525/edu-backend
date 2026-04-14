package com.edu.web.controller;

import com.edu.domain.ClassInfo;
import com.edu.domain.Course;
import com.edu.domain.Student;
import com.edu.domain.User;
import com.edu.domain.dto.FileImportPreviewVO;
import com.edu.domain.dto.StudentDTO;
import com.edu.domain.dto.StudentDetailVO;
import com.edu.domain.dto.StudentImportConfirmRequest;
import com.edu.domain.dto.StudentImportResultVO;
import com.edu.domain.dto.StudentInfoVO;
import com.edu.domain.dto.StudentListRequest;
import com.edu.domain.dto.StudentManageStatsVO;
import com.edu.service.AuthService;
import com.edu.service.StudentManageService;
import com.edu.service.StudentService;
import com.edu.common.Result;
import com.edu.common.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/student-manage")
@RequiredArgsConstructor
@Slf4j
// @PreAuthorize("hasRole('TEACHER')")  // 只有老师可以访问
public class StudentManageController {

    private final StudentManageService studentManageService;
    private final StudentService studentService;
    private final AuthService authService;

      /**
     * 获取学生列表（分页，支持筛选）
     * POST /api/student-manage/list
     */
    @PostMapping("/list")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<PageResult<Student>> getStudentList(@RequestBody StudentListRequest request) {
        User currentUser = authService.getUser();
        
        //  Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
        Page<Student> studentPage = studentManageService.getStudentList(
            request, currentUser.getId(), currentUser.getRole().name());
        
        // 转换为VO
        // List<StudentInfoVO> voList = studentPage.getContent().stream()
        //     .map(this::convertToVO)
        //     .collect(Collectors.toList());
        
        // PageResult<StudentInfoVO> pageResult = PageResult.<StudentInfoVO>builder()
        //     .list(voList)  // 改为 list，不是 records
        //     .total(studentPage.getTotalElements())
        //     .page(request.getPage() != null ? request.getPage() : 0)  // 改为 page，不是 current
        //     .pageSize(request.getSize() != null ? request.getSize() : 10)  // 改为 pageSize，不是 size
        //     .build();
        
        return Result.success(PageResult.of(studentPage));
    }

     /**
     * 获取统计卡片数据
     * GET /api/student-manage/stats?classId=1&courseId=2
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<StudentManageStatsVO> getStats(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long courseId) {
       User currentUser = authService.getUser();
        
        StudentManageStatsVO stats = studentManageService.getStats(
            currentUser.getId(), currentUser.getRole().name(), classId, courseId);
        
        return Result.success(stats);
    }

      /**
     * 上传并预览学生导入文件
     * POST /api/student-manage/import/preview
     */
    @PostMapping("/import/preview")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<FileImportPreviewVO> previewImport(@RequestParam("file") MultipartFile file) {
        try {
            FileImportPreviewVO preview = studentManageService.parseImportFile(file);
            return Result.success(preview);
        } catch (Exception e) {
            log.error("文件解析失败", e);
            return Result.error("文件解析失败: " + e.getMessage());
        }
    }

      /**
     * 获取文件预览数据
     * GET /api/student-manage/import/preview/{fileId}
     */
    @GetMapping("/import/preview/{fileId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<FileImportPreviewVO> getPreview(@PathVariable String fileId) {
        FileImportPreviewVO preview = studentManageService.getFilePreview(fileId);
        if (preview == null) {
            return Result.error("预览数据已过期，请重新上传");
        }
        return Result.success(preview);
    }

     /**
     * 确认导入学生数据
     * POST /api/student-manage/import/confirm
     */
    @PostMapping("/import/confirm")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<StudentImportResultVO> confirmImport(@RequestBody StudentImportConfirmRequest request) {
        try {
            StudentImportResultVO result = studentManageService.confirmImport(request);
            return Result.success(result);
        } catch (Exception e) {
            log.error("导入失败", e);
            return Result.error("导入失败: " + e.getMessage());
        }
    }

     /**
     * 取消导入（删除临时文件）
     * DELETE /api/student-manage/import/cancel/{fileId}
     */
    @DeleteMapping("/import/cancel/{fileId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<Void> cancelImport(@PathVariable String fileId) {
        studentManageService.cancelImport(fileId);
        return Result.success(null);
    }

     /**
     * 获取学生详情（用于查看详情弹窗）
     * GET /api/student-manage/{id}/detail
     */
    @GetMapping("/{id}/detail")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<StudentDetailVO> getStudentDetail(@PathVariable Long id) {
        Student student = studentService.getStudentById(id);
        User user = student.getUser();
        
        StudentDetailVO detail = StudentDetailVO.builder()
            .id(student.getId())
            .studentNo(student.getStudentNo())
            .name(user.getName())
            .username(user.getUsername())
            .className(student.getClassInfo() != null ? student.getClassInfo().getName() : "未分班")
            .grade(student.getGrade())
            .gender(user.getGender())
            .email(user.getEmail())
            .phone(user.getPhone())
            .avatar(user.getAvatar())
            .status(user.getStatus().name())
            .build();
        
        return Result.success(detail);
    }

    /**
     * 转换Student到StudentInfoVO
     */
    private StudentInfoVO convertToVO(Student student) {
        User user = student.getUser();
        return StudentInfoVO.builder()
            .id(student.getId())
            .studentNo(student.getStudentNo())
            .name(user != null ? user.getName() : "")
            .username(user != null ? user.getUsername() : "")
            .className(student.getClassInfo() != null ? student.getClassInfo().getName() : "未分班")
            .grade(student.getGrade())
            .email(user != null ? user.getEmail() : "")
            .gender(user != null ? user.getGender() : "")
            .phone(user != null ? user.getPhone() : "")
            .avatar(user != null ? user.getAvatar() : "")
            .status(user != null ? user.getStatus().ordinal() : 0)
            .statusText(user != null ? user.getStatus().name() : "ACTIVE")
            .build();
    }




    /**
     * 分页查询学生列表
     */
    @GetMapping
    public Result<PageResult<Student>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<Student> studentPage = studentService.searchStudents(keyword, pageable);
        
        return Result.success(PageResult.of(studentPage));
    }

    /**
     * 根据ID查询学生详情
     */
    @GetMapping("/{id}")
    public Result<Student> getById(@PathVariable Long id) {
        return Result.success(studentService.getStudentById(id));
    }

    /**
     * 创建学生
     */
    @PostMapping
    public Result<Student> create(@RequestBody StudentDTO dto) {
        return Result.success(studentService.createStudent(dto));
    }

    /**
     * 更新学生
     */
    @PutMapping("/{id}")
    public Result<Student> update(@PathVariable Long id, @RequestBody StudentDTO dto) {
        return Result.success(studentService.updateStudent(id, dto));
    }

    /**
     * 删除学生
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        studentService.deleteStudent(id);
        return Result.success(null);
    }

    /**
     * 密码重置
     */
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        studentService.resetPassword(id);
        return Result.success(null);
    }

}