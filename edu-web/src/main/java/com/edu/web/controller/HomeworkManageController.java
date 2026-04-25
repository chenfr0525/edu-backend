package com.edu.web.controller;

import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.domain.Homework;
import com.edu.domain.User;
import com.edu.domain.dto.*;
import com.edu.service.AuthService;
import com.edu.service.DeepSeekService;
import com.edu.service.FileProcessService;
import com.edu.service.HomeworkManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/homework")
@RequiredArgsConstructor
@Slf4j
public class HomeworkManageController {

    private final DeepSeekService deepSeekService;
    private final HomeworkManageService homeworkManageService;
    private final AuthService authService;
    private final FileProcessService fileProcessService;

    /**
     * 1. 获取作业列表
     */
    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<PageResult<HomeworkListVO>> getHomeworkList(@ModelAttribute HomeworkListRequest request) {
        User currentUser = authService.getUser();
        org.springframework.data.domain.Page<HomeworkListVO> page = homeworkManageService.getHomeworkList(
            request, currentUser.getId(), currentUser.getRole().name());
        return Result.success(PageResult.of(page));
    }

    /**
     * 2. 获取统计卡片数据
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<HomeworkStatisticsVO> getStatistics(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long courseId) {
        User currentUser = authService.getUser();
        return Result.success(homeworkManageService.getStatistics(
            currentUser.getId(), currentUser.getRole().name(), classId, courseId));
    }

    /**
     * 3. 获取作业详情
     */
    @GetMapping("/{homeworkId}/detail")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<HomeworkDetailVO> getHomeworkDetail(@PathVariable Long homeworkId) {
        User currentUser = authService.getUser();
        return Result.success(homeworkManageService.getHomeworkDetail(
            homeworkId, currentUser.getId(), currentUser.getRole().name()));
    }

    /**
     * 4. 手动创建作业
     */
    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<Homework> createHomework(@RequestBody HomeworkCreateRequest request) {
        log.info("create homework: {}", request);
        return Result.success(homeworkManageService.createHomework(request));
    }

    /**
     * 5. AI解析作业文件
     */
    @PostMapping("/import/parse")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<ParseResult> parseHomeworkFile(@RequestBody FileParseRequest request) {
         request.setFieldMappings(homeworkManageService.getHomeworkFieldMappings());
        if (request.getFileContent() == null || request.getFileContent().isEmpty()) {
            return Result.error("文件内容不能为空");
        }
        ParseResult result = deepSeekService.parseFileData(
            request.getFileContent(),
            request.getFileName(),
            request.getDataType(),
            request.getFieldMappings()
        );
        return Result.success(result);
    }

    /**
     * 6. 确认导入作业
     */
    @PostMapping("/import/confirm")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<String> confirmHomeworkImport(@RequestBody ConfirmInsertRequest request) {
         User currentUser = authService.getUser();
         String message = fileProcessService.confirmAndInsert(request,currentUser);
        return Result.success(message);
    }

    /**
     * 7. AI解析作业成绩文件
     */
    @PostMapping("/grades/import/parse")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<ParseResult> parseHomeworkGradeFile(@RequestBody FileParseRequest request) {
          request.setFieldMappings(homeworkManageService.getHomeworkGradeFieldMappings());
        if (request.getFileContent() == null || request.getFileContent().isEmpty()) {
            return Result.error("文件内容不能为空");
        }
       ParseResult result = deepSeekService.parseFileData(
            request.getFileContent(),
            request.getFileName(),
            "作业成绩",
            request.getFieldMappings()
        );
        return Result.success(result);
    }

    /**
     * 8. 确认导入作业成绩
     */
    @PostMapping("/{homeworkId}/grades/import/confirm")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<String> confirmHomeworkGradeImport(
            @PathVariable Long homeworkId,
            @RequestBody ConfirmInsertRequest request) {
                User currentUser = authService.getUser();
        HomeworkGradeImportResultVO result = homeworkManageService.confirmHomeworkGradeImport(
            homeworkId, request.getData());
        return result.isSuccess() ? Result.success("数据导入成功") : Result.success(result.getMessage());
    }

    /**
     * 9. 删除作业
     */
    @DeleteMapping("/{homeworkId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<Void> deleteHomework(@PathVariable Long homeworkId) {
        User currentUser = authService.getUser();
        homeworkManageService.deleteHomework(homeworkId, currentUser.getId(), currentUser.getRole().name());
        return Result.success(null);
    }

    /**
     * 10. AI整体分析报告
     */
    @GetMapping("/ai-analysis")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<HomeworkAiAnalysisVO> getAiAnalysis(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long courseId) {
        User currentUser = authService.getUser();
        return Result.success(homeworkManageService.getOverallAiAnalysis(
            currentUser.getId(), currentUser.getRole().name(), classId, courseId));
    }

  
/**
 * 编辑作业
 * PUT /api/homework/update/{homeworkId}
 */
@PutMapping("/update/{homeworkId}")
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public Result<Homework> updateHomework(
        @PathVariable Long homeworkId,
        @RequestBody HomeworkUpdateRequest request) {
    User currentUser = authService.getUser();
    Homework updated = homeworkManageService.updateHomework(
        homeworkId, request, currentUser.getId(), currentUser.getRole().name());
    return Result.success(updated);
}
}