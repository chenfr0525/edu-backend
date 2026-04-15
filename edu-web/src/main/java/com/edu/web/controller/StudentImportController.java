package com.edu.web.controller;

import com.edu.domain.dto.*;
import com.edu.service.DeepSeekService;
import com.edu.service.StudentImportValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/student/import")
@RequiredArgsConstructor
public class StudentImportController {
   private final DeepSeekService deepSeekService;
    private final StudentImportValidator validator;
    
    /**
     * 测试用：直接传入 JSON 数据验证
     */
    @PostMapping("/test")
    public ResponseEntity<ParseResult> testImport(@RequestBody List<Map<String, Object>> testData) {
        ParseResult result = new ParseResult();
        result.setSessionId(UUID.randomUUID().toString());
        result.setData(testData);
        
        // 获取字段映射
        List<FieldMapping> mappings = validator.getStudentFieldMappings();
        
        // 处理默认值
        validator.applyDefaultValues(testData, mappings);
        
        // 自动填充用户名
        validator.autoFillUsername(testData);
        
        // 验证数据
        List<ValidationError> errors = validator.validateStudentData(testData, mappings);
        
        result.setSuccess(errors.isEmpty());
        result.setErrors(errors);
        result.setSummary(String.format("共 %d 条数据，成功 %d 条，失败 %d 条", 
            testData.size(), 
            testData.size() - errors.size(), 
            errors.size()));
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * AI 解析文件后导入
     */
    @PostMapping("/parse")
    public ResponseEntity<ParseResult> parseAndImport(@RequestBody FileParseRequest request) {
        request.setFieldMappings( validator.getStudentFieldMappings());
        
        // 调用 AI 解析
        ParseResult result = deepSeekService.parseFileData(
            request.getFileContent(),
            request.getFileName(),
            request.getDataType(),
            request.getFieldMappings()
        );
        
        if (result.isSuccess() && result.getData() != null) {
            // 处理默认值
            validator.applyDefaultValues(result.getData(),  validator.getStudentFieldMappings());
            
            // 自动填充用户名
            validator.autoFillUsername(result.getData());
            
            // 验证数据
            List<ValidationError> errors = validator.validateStudentData(
                result.getData(), 
                 validator.getStudentFieldMappings()
            );
            
            result.setSuccess(errors.isEmpty());
            result.setErrors(errors);
            result.setSummary(String.format("解析完成，共 %d 条数据，验证通过 %d 条", 
                result.getData().size(), 
                result.getData().size() - errors.size()));
        }
        
        return ResponseEntity.ok(result);
    }
    
   
}
