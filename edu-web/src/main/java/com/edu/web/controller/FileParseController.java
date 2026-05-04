package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.User;
import com.edu.domain.dto.ConfirmInsertRequest;
import com.edu.domain.dto.FieldMapping;
import com.edu.domain.dto.FileParseRequest;
import com.edu.domain.dto.ImportResult;
import com.edu.domain.dto.ParseResult;
import com.edu.domain.dto.ValidationError;
import com.edu.service.ActivityImportValidator;
import com.edu.service.AuthService;
import com.edu.service.DeepSeekService;
import com.edu.service.ExamGradeImportValidator;
import com.edu.service.ExamImportValidator;
import com.edu.service.FileProcessService;
import com.edu.service.HomeworkManageService;
import com.edu.service.KnowledgePointImportValidator;
import com.edu.service.StudentImportValidator;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileParseController {
    private final DeepSeekService deepSeekService;
    private final FileProcessService fileProcessService;
    private final StudentImportValidator  studentImportvalidator;
    private final ExamImportValidator examImportValidator;           
    private final ExamGradeImportValidator examGradeImportValidator;
    private final HomeworkManageService homeworkManageService;  
    private final AuthService authService;
    private final KnowledgePointImportValidator knowledgePointImportValidator;
    private final ActivityImportValidator activityImportValidator;
  /**
     * 上传并解析文件
     */
    @PostMapping("/parse")
    public Result<ParseResult> parseFile(@RequestBody FileParseRequest request) {
        User currentUser = authService.getUser();

        List<FieldMapping> mappings = getFieldMappingsByDataType(request.getDataType());
        request.setFieldMappings(mappings);
        if ("homework".equals(request.getDataType())) {
        // 作业解析需要转换名称到ID
        ParseResult result = homeworkManageService.parseAndConvertHomeworkFile(
            request.getFileContent(),
            request.getFileName(),
            currentUser.getId(),
            currentUser.getRole().name()
        );
        return Result.success(result);
    }

     if ("exam".equals(request.getDataType())) {
            ParseResult result = examImportValidator.parseAndConvertExamFile(
                request.getFileContent(),
                request.getFileName(),
                currentUser.getId(),
                currentUser.getRole().name()
            );
            return Result.success(result);
        }
        
        if ("exam_grade".equals(request.getDataType())) {
            ParseResult result = examGradeImportValidator.parseAndConvertExamGradeFile(
                request.getFileContent(),
                request.getFileName()
            );
            return Result.success(result);
        }

         if ("knowledge".equals(request.getDataType())) {
        // 知识点解析需要知道课程ID
        Long courseId = request.getCourseId();
        if (courseId == null) {
            ParseResult error = new ParseResult();
            error.setSuccess(false);
            error.setErrors(Arrays.asList(new ValidationError("请先选择课程")));
            return Result.error("请先选择课程");
        }
        ParseResult result = knowledgePointImportValidator.parseAndConvertKnowledgePointFile(
            request.getFileContent(),
            request.getFileName(),
            courseId
        );
        return Result.success(result);
    }

     if ("STUDY".equals(request.getDataType())) {
        //学习时长导入
        ParseResult result = activityImportValidator.parseAndConvertStudyFile(
                request.getFileContent(),
                request.getFileName()
            );
            return Result.success(result);
    }else if ("RESOURCE".equals(request.getDataType())) { 
          //资源访问导入
        ParseResult result = activityImportValidator.parseAndConvertResourceFile(
                request.getFileContent(),
                request.getFileName()
            );
            return Result.success(result);
    }
        
        // 验证必填参数
        if (request.getFileContent() == null || request.getFileContent().isEmpty()) {
            ParseResult error = new ParseResult();
            error.setSuccess(false);
            error.setErrors(Arrays.asList(new ValidationError("文件内容不能为空")));
            return Result.error("文件内容不能为空");
        }
        
        ParseResult result = deepSeekService.parseFileData(request.getFileContent(),request.getFileName(),request.getDataType(),request.getFieldMappings());
        return Result.success(result);
    }

    private List<FieldMapping> getFieldMappingsByDataType(String dataType) {
        if (dataType == null) return new ArrayList<>();
        
        switch (dataType) {
            case "student":
                return studentImportvalidator.getStudentFieldMappings();
            case "exam":
                return examImportValidator.getExamParseFieldMappings();
            case "exam_grade":
                return examGradeImportValidator.getExamGradeParseFieldMappings();
            case "homework":
                return homeworkManageService.getHomeworkParseFieldMappings();
            case "homework_grade":
                return homeworkManageService.getHomeworkGradeParseFieldMappings();
            default:
                return new ArrayList<>();
        }
    }
    
    /**
     * 确认并插入数据
     */
    @PostMapping("/confirm")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<ImportResult> confirmInsert(@RequestBody ConfirmInsertRequest request) {
        User currentUser = authService.getUser();
        ImportResult result = fileProcessService.confirmAndInsert(request, currentUser);
        return result.isSuccess() ? Result.success(result) : Result.error(result.getMessage());
    }
}
