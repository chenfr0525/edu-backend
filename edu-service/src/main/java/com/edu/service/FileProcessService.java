// edu-service/FileProcessService.java
package com.edu.service;

import com.edu.domain.Student;
import com.edu.domain.dto.ConfirmInsertRequest;
import com.edu.domain.dto.ExamGradeImportResult;
import com.edu.domain.dto.FileParseRequest;
import com.edu.domain.dto.ParseResult;
import com.edu.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessService {
    
    private final DeepSeekService deepSeekService;
    private final StudentImportValidator studentImportValidator;
    private final ExamImportValidator examImportValidator;
    private final ExamGradeImportValidator examGradeImportValidator;
    
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
    public String confirmAndInsert(ConfirmInsertRequest request) {
        // 2. 使用确认后的数据（可能被用户修改过）
        List<Map<String, Object>> dataToInsert = request.getData();
        if (dataToInsert == null || dataToInsert.isEmpty()) {
            return "没有数据可插入";
        }
         String type = request.getType();
        if (type == null || type.isEmpty()) {
            return "请指定导入类型：student、exam、exam_grade";
        }
          String result;
        switch (type) {
            case "student":
                result = studentImportValidator.insertStudentData(dataToInsert);
                break;
            case "exam":
                result = examImportValidator.insertExamData(dataToInsert);
                break;
            case "exam_grade":
                // 考试成绩导入需要 examId，所以可能需要特殊处理
                // 如果 ConfirmInsertRequest 中没有 examId，需要单独处理
                result = "考试成绩导入请使用专用接口";
                break;
            default:
                result = "不支持的导入类型: " + type;
                break;
        }
            return result;
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