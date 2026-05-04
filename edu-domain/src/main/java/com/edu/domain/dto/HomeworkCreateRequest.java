package com.edu.domain.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

import com.edu.domain.HomeworkStatus;

@Data  // 自动生成 getter/setter
@NoArgsConstructor  // 无参构造函数
@AllArgsConstructor  // 全参构造函数
public class HomeworkCreateRequest {
    private String name;
    private String description;
    private Long knowledgePointId = 1L;  // 默认1
    private Long courseId;
    private Integer questionCount = 0;
    private Integer totalScore = 100;
    private HomeworkStatus status = HomeworkStatus.ONGOING;
    private LocalDateTime deadline;
    private List<Long> knowledgePointIds;
}
