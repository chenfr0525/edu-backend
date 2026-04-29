package com.edu.web.controller;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.domain.Exam;
import com.edu.domain.User;
import com.edu.domain.dto.*;
import com.edu.service.AuthService;
import com.edu.service.ExamManageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/exam-manage")
@RequiredArgsConstructor
@Slf4j
public class ExamManageController {

    private final ExamManageService examManageService;
    private final AuthService authService;

    @PostMapping("/list")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<PageResult<ExamInfoVO>> getExamList(@RequestBody ExamListRequest request) {
         User currentUser = authService.getUser();
        Page<ExamInfoVO> page = examManageService.getExamList(
            request, currentUser.getId(), currentUser.getRole().name());
        
        return Result.success(PageResult.of(page));
    }

     /**
     * 编辑考试
     * PUT /api/exam-manage/update/{examId}
     */
    @PutMapping("/update/{examId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<Exam> updateExam(@PathVariable Long examId, @RequestBody ExamCreateRequest request) {
        return Result.success(examManageService.updateExam(examId, request));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<ExamManageStatsVO> getStats(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long courseId) {
        User currentUser = authService.getUser();
        return Result.success(examManageService.getStats(
            currentUser.getId(), currentUser.getRole().name(), classId, courseId));
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<Exam> createExam(@RequestBody ExamCreateRequest request) {
        return Result.success(examManageService.createExam(request));
    }

    @GetMapping("/{examId}/detail")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<ExamDetailVO> getExamDetail(@PathVariable Long examId) {
        return Result.success(examManageService.getExamDetail(examId));
    }

    @DeleteMapping("/{examId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<Void> deleteExam(@PathVariable Long examId) {
        examManageService.deleteExam(examId);
        return Result.success(null);
    }

    @PostMapping("/import/preview")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<ExamImportPreviewVO> previewImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long examId) {
        try {
            return Result.success(examManageService.parseImportFile(file, examId));
        } catch (Exception e) {
            log.error("文件解析失败", e);
            return Result.error("文件解析失败: " + e.getMessage());
        }
    }

    @GetMapping("/import/preview/{fileId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<ExamImportPreviewVO> getPreview(@PathVariable String fileId) {
        ExamImportPreviewVO preview = examManageService.getFilePreview(fileId);
        if (preview == null) return Result.error("预览数据已过期，请重新上传");
        return Result.success(preview);
    }

    @PostMapping("/import/confirm")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<ExamImportResultVO> confirmImport(@RequestBody ExamImportConfirmRequest request) {
        try {
            return Result.success(examManageService.confirmImport(request));
        } catch (Exception e) {
            log.error("导入失败", e);
            return Result.error("导入失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/import/cancel/{fileId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<Void> cancelImport(@PathVariable String fileId) {
        examManageService.cancelImport(fileId);
        return Result.success(null);
    }

    @GetMapping("/ai-report")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<ExamAiAnalysisVO> getAiReport(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long classId) {
        return Result.success(examManageService.getCourseExamAiReport(courseId, classId));
    }

    // ExamManageController.java 新增方法

/**
 * 分页获取考试的学生成绩列表
 * GET /api/exam-manage/{examId}/grades
 */
@GetMapping("/{examId}/grades")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public Result<ExamStudentGradePageVO> getStudentGrades(
        @PathVariable Long examId,
        @ModelAttribute ExamStudentGradePageRequest request) {
    User currentUser = authService.getUser();
    ExamStudentGradePageVO page = examManageService.getStudentGradesPage(
        request, currentUser.getId(), currentUser.getRole().name(), examId);
    
    return Result.success(page);
}
}