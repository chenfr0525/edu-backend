package com.edu.domain.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 文件解析请求
 */
@Data
public class FileParseRequest {
    private String fileContent;      // Base64 编码的文件内容
    private String fileName;         // 文件名
    private String fileType;         // 文件类型：pdf, excel, word, txt, image
    private String dataType;         // 数据类型：student, score, course等
    private List<FieldMapping> fieldMappings;  // 字段映射配置
}
