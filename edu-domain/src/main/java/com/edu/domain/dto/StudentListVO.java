// StudentListVO.java
package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class StudentListVO {
    private List<StudentInfoVO> records;
    private Long total;
    private Integer current;
    private Integer size;
    private Integer pages;
}