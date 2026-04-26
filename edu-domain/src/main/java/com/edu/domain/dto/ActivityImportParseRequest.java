package com.edu.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor 
public class ActivityImportParseRequest {
   private String fileContent;
    private String fileName;
    private String activityType; // LOGIN/HOMEWORK/EXAM/STUDY_DURATION/RESOURCE
}
