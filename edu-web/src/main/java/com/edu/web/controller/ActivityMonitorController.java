package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.User;
import com.edu.domain.dto.*;
import com.edu.service.ActivityMonitorService;
import com.edu.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activity-monitor")
@RequiredArgsConstructor
@Slf4j
public class ActivityMonitorController {

    private final ActivityMonitorService activityMonitorService;
    private final AuthService authService;

    /**
     * 1. 获取学生活跃度列表（五列数据）
     */
    @GetMapping("/student-list")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<ActivityListResponseVO> getStudentActivityList(@ModelAttribute ActivityStudentListRequest request) {
        User currentUser = authService.getUser();
        return Result.success(activityMonitorService.getStudentActivityList(
            request, currentUser.getId(), currentUser.getRole().name()));
    }

    /**
     * 2. 获取学生活跃度详情
     */
    @GetMapping("/student/{studentId}/detail")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<StudentActivityDetailVO> getStudentActivityDetail(
            @PathVariable Long studentId,
            @ModelAttribute StudentActivityDetailRequest request) {
        return Result.success(activityMonitorService.getStudentActivityDetail(studentId, request));
    }

    /**
     * 3. AI解析活跃度数据文件
     */
    @PostMapping("/import/parse")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<ParseResult> parseActivityFile(@RequestBody ActivityImportParseRequest request) {
        if (request.getFileContent() == null || request.getFileContent().isEmpty()) {
            return Result.error("文件内容不能为空");
        }
        ParseResult result = activityMonitorService.parseActivityFile(
            request.getFileContent(), request.getFileName(), request.getActivityType());
        return Result.success(result);
    }

    /**
     * 4. 确认导入活跃度数据
     */
    @PostMapping("/import/confirm")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<ActivityImportResultVO> confirmActivityImport(@RequestBody ConfirmInsertRequest request) {
        ActivityImportResultVO result = activityMonitorService.confirmActivityImport(request.getData());
        return result.isSuccess() ? Result.success(result) : Result.error(result.getMessage());
    }

    /**
     * 5. 获取活跃度统计卡片
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<ActivityOverallStatisticsVO> getStatistics(@RequestParam(required = false) Long classId) {
        User currentUser = authService.getUser();
        return Result.success(activityMonitorService.getActivityStatistics(
            classId, currentUser.getId(), currentUser.getRole().name()));
    }

    /**
     * 6. 获取ECharts图表数据
     */
    @GetMapping("/chart-data")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<ActivityChartDataVO> getChartData(@RequestParam(required = false) Long classId) {
        User currentUser = authService.getUser();
        return Result.success(activityMonitorService.getChartData(
            classId, currentUser.getId(), currentUser.getRole().name()));
    }
       /**
     * 8. 低活跃度预警列表
     */
    @GetMapping("/low-activity-warning")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<List<WarningItem>> getLowActivityWarnings(@RequestParam(required = false) Long classId) {
        ActivityChartDataVO chartData = getChartData(classId).getData();
        return Result.success(chartData.getLowActivityWarnings());
    }
}