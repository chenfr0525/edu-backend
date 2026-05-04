package com.edu.domain.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationError {
    private int rowIndex;           // 错误行号
    private String field;           // 错误字段
    private String errorMessage;    // 错误信息
    private Object value;           // 错误的值
    private String errorType;       // 错误类型：required, unique, exist, format
    
    // 便捷构造函数
    public ValidationError(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public ValidationError(int rowIndex, String field, String errorMessage, String errorType) {
        this.rowIndex = rowIndex;
        this.field = field;
        this.errorMessage = errorMessage;
        this.errorType = errorType;
    }

     @Override
    public String toString() {
        return String.format("第%d行 [%s] %s: %s (当前值: %s)", 
            rowIndex, errorType, field, errorMessage, value);
    }
}