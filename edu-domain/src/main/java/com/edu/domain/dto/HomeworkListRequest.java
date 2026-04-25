package com.edu.domain.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 文件解析请求
 */
@Data
public class HomeworkListRequest {
    private Long classId;      // 班级ID（可选）
    private Long courseId;     // 课程ID（可选）
    private String keyword;    // 模糊搜索
    private String status;     // 状态筛选
    private Integer page = 0;
    private Integer size = 10;
}
