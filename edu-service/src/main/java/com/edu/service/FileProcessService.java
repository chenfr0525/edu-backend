// edu-service/FileProcessService.java
package com.edu.service;

import com.edu.domain.ActivityStatus;
import com.edu.domain.Student;
import com.edu.domain.User;
import com.edu.domain.dto.ActivityImportResultVO;
import com.edu.domain.dto.ConfirmInsertRequest;
import com.edu.domain.dto.ExamGradeImportResult;
import com.edu.domain.dto.FileParseRequest;
import com.edu.domain.dto.HomeworkGradeImportResultVO;
import com.edu.domain.dto.ImportResult;
import com.edu.domain.dto.KnowledgePointImportResultVO;
import com.edu.domain.dto.ParseResult;
import com.edu.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessService {
    
    private final DeepSeekService deepSeekService;
    private final StudentImportValidator studentImportValidator;
    private final ExamImportValidator examImportValidator;
    private final ExamGradeImportValidator examGradeImportValidator;
    private final HomeworkManageService homeworkManageService;
    private final CourseAnalysisService courseAnalysisService;
    private final ActivityImportValidator activityImportValidator;

    
    /**
     * 步骤1：解析文件并暂存数据
     */
    public ParseResult parseAndTempSave(FileParseRequest request) {
      
            // 2. AI 解析文件
            ParseResult result = deepSeekService.parseFileData(
                request.getFileContent(),
                request.getFileName(),
                request.getDataType(),
                request.getFieldMappings()
            );
            
            
            return result;
    }
    
    /**
     * 步骤2：确认并插入数据库
     */
    @Transactional
    public ImportResult confirmAndInsert(ConfirmInsertRequest request, User user) {
        List<Map<String, Object>> dataToInsert = request.getData();
        if (dataToInsert == null || dataToInsert.isEmpty()) {
            return ImportResult.builder()
                .success(false)
                .message("没有数据可插入")
                .build();
        }
        
        String type = request.getType();
        if (type == null || type.isEmpty()) {
            return ImportResult.builder()
                .success(false)
                .message("请指定导入类型：student、exam、exam_grade、homework、homework_grade、knowledge、STUDY、RESOURCE")
                .build();
        }
        try {
            switch (type) {
                case "student":
                    return studentImportValidator.insertStudentData(dataToInsert);
                    
                case "exam":
                    return examImportValidator.insertExamData(request.getData(), user);
                    
                case "exam_grade":
                    if (request.getExamId() == null) {
                        return ImportResult.builder()
                            .success(false)
                            .message("考试成绩导入需要指定考试ID")
                            .build();
                    }
                    ExamGradeImportResult gradeResult = examGradeImportValidator.insertExamGradeData(
                        request.getExamId(), dataToInsert);
                    return convertExamGradeResult(gradeResult);
                    
                case "homework":
                    return homeworkManageService.confirmHomeworkImport(dataToInsert, user);
                case "homework_grade":
                    if (request.getHomeworkId() == null) {
                        return ImportResult.builder()
                            .success(false)
                            .message("作业成绩导入需要指定作业ID")
                            .build();
                    }
                    HomeworkGradeImportResultVO homeworkGradeResult = homeworkManageService.confirmHomeworkGradeImport(
                        request.getHomeworkId(), dataToInsert);
                    return convertHomeworkGradeResult(homeworkGradeResult);
                    
                case "knowledge":
                    if (request.getCourseId() == null) {
                        return ImportResult.builder()
                            .success(false)
                            .message("知识点导入需要指定课程ID")
                            .build();
                    }
                    KnowledgePointImportResultVO knowledgeResult = courseAnalysisService.confirmKnowledgePointImport(
                        request.getCourseId(), dataToInsert);
                    return convertKnowledgeResult(knowledgeResult);
                case "STUDY":
                    return activityImportValidator.insertActivityData(ActivityStatus.STUDY, request.getData());
                case "RESOURCE":
                    return activityImportValidator.insertActivityData(ActivityStatus.RESOURCE, request.getData());
                default:
                    return ImportResult.builder()
                        .success(false)
                        .message("不支持的导入类型: " + type)
                        .build();
            }
        } catch (Exception e) {
            log.error("导入失败", e);
            return ImportResult.builder()
                .success(false)
                .message("导入失败: " + e.getMessage())
                .build();
        }
    }

    /**
     * 转换考试成绩导入结果
     */
    private ImportResult convertExamGradeResult(ExamGradeImportResult result) {
        return ImportResult.builder()
            .success(result.isSuccess())
            .successCount(result.getSuccessCount())
            .failCount(result.getFailCount())
            .errors(result.getErrors() != null ? 
                result.getErrors().stream().map(e -> e.getErrorMessage()).collect(Collectors.toList()) : 
                new ArrayList<>())
            .message(result.getMessage())
            .errorMessage(result.getErrorMessage())
            .build();
    }

    /**
     * 转换作业成绩导入结果
     */
    private ImportResult convertHomeworkGradeResult(HomeworkGradeImportResultVO result) {
        return ImportResult.builder()
            .success(result.isSuccess())
            .successCount(result.getSuccessCount())
            .failCount(result.getFailCount())
            .errors(new ArrayList<>())
            .message(result.getMessage())
            .build();
    }

    /**
     * 转换知识点导入结果
     */
    private ImportResult convertKnowledgeResult(KnowledgePointImportResultVO result) {
        return ImportResult.builder()
            .success(result.isSuccess())
            .successCount(result.getSuccessCount())
            .failCount(result.getFailCount())
            .errors(result.getErrors())
            .message(result.getMessage())
            .build();
    }

    /**
     * 转换活跃度导入结果
     */
    private ImportResult convertActivityResult(ActivityImportResultVO result) {
        return ImportResult.builder()
            .success(result.isSuccess())
            .successCount(result.getSuccessCount())
            .failCount(result.getFailCount())
            .errors(result.getErrors())
            .message(result.getMessage())
            .build();
    }



      /**
     * 考试成绩导入专用方法（需要 examId）
     */
    @Transactional
    public ExamGradeImportResult confirmAndInsertExamGrade(ConfirmInsertRequest request, Long examId) {
        List<Map<String, Object>> dataToInsert = request.getData();
        if (dataToInsert == null || dataToInsert.isEmpty()) {
            return ExamGradeImportResult.builder()
                .success(false)
                .message("没有数据可插入")
                .build();
        }
        
        return examGradeImportValidator.insertExamGradeData(examId, dataToInsert);
    }
    
}