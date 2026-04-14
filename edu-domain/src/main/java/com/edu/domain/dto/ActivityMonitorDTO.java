package com.edu.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ActivityMonitorDTO {
    private Double classAvgActivityScore;
    private Double thisWeekAvgActivity;
    private Double lastWeekAvgActivity;
    private Double activityChange;
    private Long activeStudentCount;
    private Long lowActivityCount;
    private List<LowActivityStudentDTO> lowActivityStudents;
    private Long criticalAlertCount;
    private List<CriticalAlertDTO> criticalAlerts;
}