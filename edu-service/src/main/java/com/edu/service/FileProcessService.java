// edu-service/FileProcessService.java
package com.edu.service;

import com.edu.domain.dto.*;
import com.edu.domain.Student;
import com.edu.domain.dto.ConfirmInsertRequest;
import com.edu.domain.dto.FileParseRequest;
import com.edu.domain.dto.ParseResult;
import com.edu.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessService {
    
    private final DeepSeekService deepSeekService;
    private final StudentRepository studentRepository;
    
    // 临时文件存储目录
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + "/edu-uploads/";
    
    /**
     * 步骤1：解析文件并暂存数据
     */
    public ParseResult parseAndTempSave(FileParseRequest request) {
        // 1. 保存临时文件（可选，用于调试）
        String tempFilePath = saveTempFile(request.getFileContent(), request.getFileName());
        
        try {
            // 2. AI 解析文件
            ParseResult result = deepSeekService.parseFileData(
                request.getFileContent(),
                request.getFileName(),
                request.getDataType(),
                request.getFieldMappings()
            );
            
            // 3. 如果解析成功，将结果暂存到内存或Redis
            if (result.isSuccess()) {
                cacheParseResult(result.getSessionId(), result);
            }
            
            return result;
            
        } finally {
            // 4. 删除临时文件
            deleteTempFile(tempFilePath);
        }
    }
    
    /**
     * 步骤2：确认并插入数据库
     */
    @Transactional
    public void confirmAndInsert(ConfirmInsertRequest request) {
        // 1. 获取缓存的数据
        ParseResult cachedResult = getCachedResult(request.getSessionId());
        if (cachedResult == null) {
            throw new RuntimeException("数据已过期或不存在，请重新上传文件");
        }
        
        // 2. 使用确认后的数据（可能被用户修改过）
        List<Map<String, Object>> dataToInsert = request.getData();
        if (dataToInsert == null || dataToInsert.isEmpty()) {
            dataToInsert = cachedResult.getData();
        }
        
        // 3. 插入数据库
        if (request.isConfirmed()) {
            insertDataToDatabase(dataToInsert);
            log.info("数据已插入数据库，共 {} 条记录", dataToInsert.size());
        } else {
            log.info("用户取消插入，数据已丢弃");
        }
        
        // 4. 清理缓存
        clearCache(request.getSessionId());
    }
    
    /**
     * 插入数据到数据库（根据你的业务修改）
     */
    private void insertDataToDatabase(List<Map<String, Object>> dataList) {
        for (Map<String, Object> row : dataList) {
            Student student = new Student();
            // student.setName((String) row.get("name"));
            // student.setStudentNo((String) row.get("studentNo"));
            // student.setClassName((String) row.get("className"));
            // ... 其他字段
            
            studentRepository.save(student);
        }
    }
    
    /**
     * 保存临时文件
     */
    private String saveTempFile(String base64Content, String fileName) {
        try {
            Path tempDir = Paths.get(TEMP_DIR);
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
            }
            
            String tempFileName = UUID.randomUUID().toString() + "_" + fileName;
            Path tempFile = tempDir.resolve(tempFileName);
            
            // 解码 Base64 并保存
            byte[] fileBytes = Base64.getDecoder().decode(base64Content);
            Files.write(tempFile, fileBytes);
            
            log.info("临时文件已保存: {}", tempFile.toString());
            return tempFile.toString();
            
        } catch (Exception e) {
            log.error("保存临时文件失败", e);
            return null;
        }
    }
    
    /**
     * 删除临时文件
     */
    private void deleteTempFile(String filePath) {
        if (filePath == null) return;
        
        try {
            Files.deleteIfExists(Paths.get(filePath));
            log.info("临时文件已删除: {}", filePath);
        } catch (Exception e) {
            log.warn("删除临时文件失败: {}", filePath, e);
        }
    }
    
    // 简单的内存缓存（生产环境建议用 Redis）
    private final Map<String, ParseResult> cache = new HashMap<>();
    
    private void cacheParseResult(String sessionId, ParseResult result) {
        cache.put(sessionId, result);
        // 30分钟后自动过期（简化版，实际应该用定时任务）
    }
    
    private ParseResult getCachedResult(String sessionId) {
        return cache.get(sessionId);
    }
    
    private void clearCache(String sessionId) {
        cache.remove(sessionId);
    }
}