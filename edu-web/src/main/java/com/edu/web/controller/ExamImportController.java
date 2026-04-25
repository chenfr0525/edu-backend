package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.User;
import com.edu.domain.dto.ConfirmInsertRequest;
import com.edu.domain.dto.FileParseRequest;
import com.edu.domain.dto.ParseResult;
import com.edu.domain.dto.ExamGradeImportResult;
import com.edu.service.AuthService;
import com.edu.service.DeepSeekService;
import com.edu.service.ExamImportValidator;
import com.edu.service.ExamGradeImportValidator;
import com.edu.service.FileProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exam-import")
@RequiredArgsConstructor
@Slf4j
public class ExamImportController {

    private final DeepSeekService deepSeekService;
    private final FileProcessService fileProcessService;
    private final ExamImportValidator examImportValidator;
    private final ExamGradeImportValidator examGradeImportValidator;
    private final AuthService authService;

    /**
     * 上传并解析考试文件
     */
    @PostMapping("/parse")
    public Result<ParseResult> parseExamFile(@RequestBody FileParseRequest request) {
        request.setFieldMappings(examImportValidator.getExamFieldMappings());
        
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
     * 确认并导入考试数据
     */
    @PostMapping("/confirm")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<String> confirmExamInsert(@RequestBody ConfirmInsertRequest request) {
        User currentUser = authService.getUser();
        String message = fileProcessService.confirmAndInsert(request,currentUser);
        return Result.success(message);
    }

    /**
     * 上传并解析考试成绩文件
     */
    @PostMapping("/grade/parse")
    public Result<ParseResult> parseExamGradeFile(@RequestBody FileParseRequest request) {
        request.setFieldMappings(examGradeImportValidator.getExamGradeFieldMappings());
        
        if (request.getFileContent() == null || request.getFileContent().isEmpty()) {
            return Result.error("文件内容不能为空");
        }
        
        ParseResult result = deepSeekService.parseFileData(
            request.getFileContent(),
            request.getFileName(),
            "考试成绩",
            request.getFieldMappings()
        );
        return Result.success(result);
    }

    /**
     * 确认并导入考试成绩数据
     * @param examId 考试ID
     * @param request 确认请求
     */
    @PostMapping("/grade/confirm/{examId}")
    public Result<String> confirmExamGradeInsert(
            @PathVariable Long examId,
            @RequestBody ConfirmInsertRequest request) {
        
        ExamGradeImportResult result = examGradeImportValidator.insertExamGradeData(
            examId, 
            request.getData()
        );
        
        return result.isSuccess() ? Result.success("数据导入成功") : Result.error(result.getMessage());
    }
}