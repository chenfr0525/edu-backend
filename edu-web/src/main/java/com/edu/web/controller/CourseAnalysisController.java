package com.edu.web.controller;

import com.edu.common.Result;
import com.edu.domain.Course;
import com.edu.domain.KnowledgePoint;
import com.edu.domain.User;
import com.edu.domain.dto.*;
import com.edu.service.AuthService;
import com.edu.service.CourseAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/course")
@RequiredArgsConstructor
@Slf4j
public class CourseAnalysisController {

    private final CourseAnalysisService courseAnalysisService;
    private final AuthService authService;

    // ==================== 课程管理 ====================

    /**
     * 1. 获取课程列表
     */
    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<List<CourseListVO>> getCourseList() {
        User currentUser = authService.getUser();
        return Result.success(courseAnalysisService.getCourseList(
            currentUser.getId(), currentUser.getRole().name()));
    }

    /**
     * 2. 获取课程详情
     */
    @GetMapping("/{courseId}/detail")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<CourseDetailVO> getCourseDetail(@PathVariable Long courseId) {
        User currentUser = authService.getUser();
        return Result.success(courseAnalysisService.getCourseDetail(
            courseId, currentUser.getId(), currentUser.getRole().name()));
    }

    /**
     * 3. 获取课程统计卡片（4个指标）
     */
    @GetMapping("/{courseId}/statistics")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<CourseStatisticsVO> getCourseStatistics(@PathVariable Long courseId) {
        User currentUser = authService.getUser();
        return Result.success(courseAnalysisService.getCourseStatistics(
            courseId, currentUser.getId(), currentUser.getRole().name()));
    }

    /**
     * 4. 创建课程（仅管理员）
     */
    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Course> createCourse(@RequestBody CourseCreateRequest request) {
        return Result.success(courseAnalysisService.createCourse(request));
    }

    /**
     * 5. 编辑课程
     */
    @PutMapping("/update/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Course> updateCourse(@PathVariable Long courseId, 
                                        @RequestBody CourseUpdateRequest request) {
        return Result.success(courseAnalysisService.updateCourse(courseId, request));
    }

    /**
     * 6. 删除课程
     */
    @DeleteMapping("/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteCourse(@PathVariable Long courseId) {
        courseAnalysisService.deleteCourse(courseId);
        return Result.success(null);
    }

    // ==================== 知识点管理 ====================

    /**
     * 7. 获取课程知识点列表
     */
    @GetMapping("/{courseId}/knowledge-points")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<List<KnowledgePointVO>> getKnowledgePoints(@PathVariable Long courseId) {
        User currentUser = authService.getUser();
        return Result.success(courseAnalysisService.getKnowledgePoints(
            courseId, currentUser.getId(), currentUser.getRole().name()));
    }

    /**
     * 8. 手动创建知识点
     */
    @PostMapping("/knowledge-point/create")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<KnowledgePoint> createKnowledgePoint(@RequestBody KnowledgePointCreateRequest request) {
        return Result.success(courseAnalysisService.createKnowledgePoint(request));
    }

    /**
     * 9. 编辑知识点
     */
    @PutMapping("/knowledge-point/update/{kpId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<KnowledgePoint> updateKnowledgePoint(@PathVariable Long kpId,
                                                        @RequestBody KnowledgePointUpdateRequest request) {
        return Result.success(courseAnalysisService.updateKnowledgePoint(kpId, request));
    }

    /**
     * 10. 删除知识点
     */
    @DeleteMapping("/knowledge-point/{kpId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<Void> deleteKnowledgePoint(@PathVariable Long kpId) {
        courseAnalysisService.deleteKnowledgePoint(kpId);
        return Result.success(null);
    }

    /**
     * 11. AI解析知识点文件
     */
    @PostMapping("/knowledge-point/import/parse")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<ParseResult> parseKnowledgePointFile(@RequestBody KnowledgePointImportParseRequest request) {
        if (request.getFileContent() == null || request.getFileContent().isEmpty()) {
            return Result.error("文件内容不能为空");
        }
        ParseResult result = courseAnalysisService.parseKnowledgePointFile(
            request.getFileContent(), request.getFileName(), request.getCourseId());
        return Result.success(result);
    }

    /**
     * 12. 确认导入知识点
     */
    @PostMapping("/{courseId}/knowledge-point/import/confirm")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<String> confirmKnowledgePointImport(
            @PathVariable Long courseId,
            @RequestBody ConfirmInsertRequest request) {
        KnowledgePointImportResultVO result = courseAnalysisService.confirmKnowledgePointImport(
            courseId, request.getData());
        return result.isSuccess() ? Result.success("数据导入成功"): Result.error(result.getMessage());
    }

    /**
     * 13. 知识点详情分析
     */
    @GetMapping("/{courseId}/knowledge-point/{kpId}/detail")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<KnowledgePointDetailVO> getKnowledgePointDetail(
            @PathVariable Long courseId,
            @PathVariable Long kpId) {
        User currentUser = authService.getUser();
        return Result.success(courseAnalysisService.getKnowledgePointDetail(
            courseId, kpId, currentUser.getId(), currentUser.getRole().name()));
    }

    /**
     * 14. ECharts图表数据
     */
    @GetMapping("/{courseId}/chart-data")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<CourseChartDataVO> getChartData(@PathVariable Long courseId) {
        User currentUser = authService.getUser();
        return Result.success(courseAnalysisService.getChartData(
            courseId, currentUser.getId(), currentUser.getRole().name()));
    }

    /**
     * 15. AI整体分析报告
     */
    @GetMapping("/{courseId}/ai-analysis")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public Result<CourseAiAnalysisVO> getAiAnalysis(@PathVariable Long courseId) {
        User currentUser = authService.getUser();
        return Result.success(courseAnalysisService.getAiAnalysis(
            courseId, currentUser.getId(), currentUser.getRole().name()));
    }
}