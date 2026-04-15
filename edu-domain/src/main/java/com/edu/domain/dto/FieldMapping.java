package com.edu.domain.dto;

import java.util.List;

import lombok.Data;

@Data
public class FieldMapping {
    private String targetField;      // 目标字段名（数据库字段）
    private String fieldDescription; // 字段描述（用于AI识别）
    private List<String> possibleNames;   // 可能的列名
    private boolean required;        // 是否必填
    private String dataType;         // 数据类型：string, number, date
    private boolean unique;          // 是否唯一（新增）
    private boolean needExist;       // 是否需要存在性校验（新增）
    private String defaultValue;     // 默认值（新增）
    private String regex;            // 正则校验（新增）
}