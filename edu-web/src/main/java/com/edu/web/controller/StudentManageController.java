package com.edu.web.controller;

import com.edu.domain.Student;
import com.edu.domain.dto.StudentDTO;
import com.edu.service.StudentService;
import com.edu.common.Result;
import com.edu.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")  // 只有老师可以访问
public class StudentManageController {

    private final StudentService studentService;

    /**
     * 分页查询学生列表
     * GET /api/students?page=0&size=10&keyword=张三
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
     * GET /api/students/1
     */
    @GetMapping("/{id}")
    public Result<Student> getById(@PathVariable Long id) {
        return Result.success(studentService.getStudentById(id));
    }

    /**
     * 创建学生
     * POST /api/students
     */
    @PostMapping
    public Result<Student> create(@RequestBody StudentDTO dto) {
        return Result.success(studentService.createStudent(dto));
    }

    /**
     * 更新学生
     * PUT /api/students/1
     */
    @PutMapping("/{id}")
    public Result<Student> update(@PathVariable Long id, @RequestBody StudentDTO dto) {
        return Result.success(studentService.updateStudent(id, dto));
    }

    /**
     * 删除学生
     * DELETE /api/students/1
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        studentService.deleteStudent(id);
        return Result.success(null);
    }
}