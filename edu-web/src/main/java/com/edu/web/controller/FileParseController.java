package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.dto.ConfirmInsertRequest;
import com.edu.domain.dto.FileParseRequest;
import com.edu.domain.dto.ParseResult;
import com.edu.domain.dto.ValidationError;
import com.edu.service.DeepSeekService;
import com.edu.service.FileProcessService;
import com.edu.service.StudentImportValidator;

import lombok.RequiredArgsConstructor;
import java.util.Arrays;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileParseController {
    private final DeepSeekService deepSeekService;
    private final FileProcessService fileProcessService;
      private final StudentImportValidator  studentImportvalidator;
  /**
     * 上传并解析文件
     */
    @PostMapping("/parse")
    public Result<ParseResult> parseFile(@RequestBody FileParseRequest request) {
         request.setFieldMappings(studentImportvalidator.getStudentFieldMappings());
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
    
    /**
     * 确认并插入数据
     */
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirmInsert(@RequestBody ConfirmInsertRequest request) {
        fileProcessService.confirmAndInsert(request);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 取消插入（删除临时数据）
     */
    @DeleteMapping("/cancel/{sessionId}")
    public ResponseEntity<Void> cancelInsert(@PathVariable String sessionId) {
        // 清理缓存即可
        return ResponseEntity.ok().build();
    }
}
