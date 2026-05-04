package com.edu.domain.dto;
import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class ConfirmInsertRequest {
     /**
     * 导入类型：
     * - student: 学生导入
     * - exam: 考试导入
     * - exam_grade: 考试成绩导入
     * - homework: 作业导入
     * - homework_grade: 作业成绩导入
     * - knowledge: 知识点导入
     * - STUDY: 学习时长导入
     * - RESOURCE: 资源访问导入
     */
    private String type;
    
    /**
     * 导入的数据列表（确认后的数据，包含ID字段）
     */
    private List<Map<String, Object>> data;
    
    /**
     * 考试ID（考试成绩导入时必填）
     */
    private Long examId;
    
    /**
     * 作业ID（作业成绩导入时必填）
     */
    private Long homeworkId;
    
    /**
     * 课程ID（知识点导入时可选，也可以从URL路径获取）
     */
    private Long courseId;
}
