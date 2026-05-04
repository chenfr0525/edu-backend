package com.edu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu.domain.Course;
import com.edu.domain.KnowledgePoint;
import com.edu.domain.dto.FieldMapping;
import com.edu.domain.dto.ImportResult;
import com.edu.domain.dto.ParseResult;
import com.edu.domain.dto.ValidationError;
import com.edu.repository.CourseRepository;
import com.edu.repository.KnowledgePointRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgePointImportValidator {

    private final DeepSeekService deepSeekService;
    private final KnowledgePointRepository knowledgePointRepository;
    private final CourseRepository courseRepository;

    /**
     * 获取知识点解析阶段字段映射（用于AI解析Excel文件）
     * 字段名：用户友好的名称（知识点名称、父知识点名称）
     */
    public List<FieldMapping> getKnowledgePointParseFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();

        // 知识点名称（必填）
        FieldMapping name = new FieldMapping();
        name.setTargetField("name");
        name.setFieldDescription("知识点名称");
        name.setPossibleNames(Arrays.asList("知识点名称", "名称", "知识点名", "Name", "知识点"));
        name.setRequired(true);
        name.setDataType("string");
        mappings.add(name);

        // 父知识点名称（可选，用于建立层级关系）
        FieldMapping parentName = new FieldMapping();
        parentName.setTargetField("parentName");
        parentName.setFieldDescription("父知识点名称");
        parentName.setPossibleNames(Arrays.asList("父知识点", "上级知识点", "父节点", "Parent Knowledge Point"));
        parentName.setRequired(false);
        parentName.setDataType("string");
        mappings.add(parentName);

        // 描述（可选）
        FieldMapping description = new FieldMapping();
        description.setTargetField("description");
        description.setFieldDescription("知识点描述");
        description.setPossibleNames(Arrays.asList("描述", "说明", "详细介绍", "Description"));
        description.setRequired(false);
        description.setDataType("string");
        mappings.add(description);

        // 排序（可选）
        FieldMapping sortOrder = new FieldMapping();
        sortOrder.setTargetField("sortOrder");
        sortOrder.setFieldDescription("排序顺序");
        sortOrder.setPossibleNames(Arrays.asList("排序", "顺序", "Sort Order"));
        sortOrder.setRequired(false);
        sortOrder.setDataType("number");
        mappings.add(sortOrder);

        return mappings;
    }

    /**
     * 获取知识点导入阶段字段映射（用于验证前端传来的数据）
     * 字段名：代码中使用的字段名（parentId）
     */
    public List<FieldMapping> getKnowledgePointImportFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();

        // 知识点名称（必填）
        FieldMapping name = new FieldMapping();
        name.setTargetField("name");
        name.setFieldDescription("知识点名称");
        name.setPossibleNames(Arrays.asList("name", "知识点名称"));
        name.setRequired(true);
        name.setDataType("string");
        mappings.add(name);

        // 父知识点ID（可选）
        FieldMapping parentId = new FieldMapping();
        parentId.setTargetField("parentId");
        parentId.setFieldDescription("父知识点ID");
        parentId.setPossibleNames(Arrays.asList("parentId", "父知识点ID"));
        parentId.setRequired(false);
        parentId.setDataType("number");
        parentId.setNeedExist(true);
        mappings.add(parentId);

        // 描述（可选）
        FieldMapping description = new FieldMapping();
        description.setTargetField("description");
        description.setFieldDescription("知识点描述");
        description.setPossibleNames(Arrays.asList("description", "描述"));
        description.setRequired(false);
        description.setDataType("string");
        mappings.add(description);

        // 排序（可选）
        FieldMapping sortOrder = new FieldMapping();
        sortOrder.setTargetField("sortOrder");
        sortOrder.setFieldDescription("排序顺序");
        sortOrder.setPossibleNames(Arrays.asList("sortOrder", "排序"));
        sortOrder.setRequired(false);
        sortOrder.setDataType("number");
        mappings.add(sortOrder);

        return mappings;
    }

    /**
     * AI解析知识点文件后，自动将父知识点名称转换为ID
     */
    public ParseResult parseAndConvertKnowledgePointFile(String fileContent, String fileName, Long courseId) {
        // 1. 获取解析阶段的字段映射
        List<FieldMapping> mappings = getKnowledgePointParseFieldMappings();
        
        // 2. AI解析文件
        ParseResult result = deepSeekService.parseFileData(fileContent, fileName, "knowledge", mappings);
        
        // 3. 解析成功后，自动将父知识点名称转换为ID
        if (result.isSuccess() && result.getData() != null && !result.getData().isEmpty()) {
            convertKnowledgePointParseResultToIds(result, courseId);
        }
        
        return result;
    }

    /**
     * 将知识点解析结果中的父知识点名称转换为ID
     */
    private void convertKnowledgePointParseResultToIds(ParseResult result, Long courseId) {
        List<Map<String, Object>> data = result.getData();
        
        // 获取当前课程的所有知识点，建立名称到ID的映射
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new RuntimeException("课程不存在"));
        
        List<KnowledgePoint> allKps = knowledgePointRepository.findByCourse(course);
        Map<String, Long> kpNameToIdMap = allKps.stream()
            .collect(Collectors.toMap(
                KnowledgePoint::getName,
                KnowledgePoint::getId,
                (existing, replacement) -> existing
            ));
        
        // 记录当前批次中新增的知识点名称到ID的临时映射（用于同一批次内的父子关系）
        Map<String, Long> tempKpNameToIdMap = new HashMap<>();
        
        for (Map<String, Object> row : data) {
            String kpName = (String) row.get("name");
            if (kpName != null && !kpName.isEmpty()) {
                // 暂时存储名称，等确认导入时再创建
                row.put("_tempName", kpName);
            }
            
            // 父知识点名称 -> parentId
            String parentName = (String) row.get("parentName");
            if (parentName != null && !parentName.isEmpty()) {
                Long parentId = kpNameToIdMap.get(parentName);
                if (parentId != null) {
                    row.put("parentId", parentId);
                } else {
                    // 可能是在同一批次中新增的父知识点，暂时标记，等确认导入时处理
                    row.put("_tempParentName", parentName);
                    row.put("parentId", null);
                    row.put("_warning_parentName", "父知识点 '" + parentName + "' 将在本次导入中创建，请确保父知识点在列表中");
                }
                row.remove("parentName");
            }
            
            // 设置默认排序
            if (row.get("sortOrder") == null) {
                row.put("sortOrder", 0);
            }
        }
    }

    /**
     * 确认导入知识点数据
     */
    @Transactional
    public ImportResult insertKnowledgePointData(Long courseId, List<Map<String, Object>> data) {
        List<FieldMapping> mappings = getKnowledgePointImportFieldMappings();
        
        // 1. 验证数据
        List<ValidationError> errors = deepSeekService.validateData(data, mappings);
        if (!errors.isEmpty()) {
            log.error("数据验证失败：{}", errors);
            return ImportResult.builder()
                .success(false)
                .errorMessage(buildErrorMessage(errors))
                .errors(errors.stream()
                    .map(ValidationError::toString)
                    .collect(Collectors.toList()))
                .build();
        }

        // 2. 获取课程
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new RuntimeException("课程不存在"));

        // 3. 先创建所有知识点（第一遍，获取ID映射）
        List<KnowledgePoint> createdKps = new ArrayList<>();
        Map<String, Long> nameToIdMap = new HashMap<>();
        
        // 第一遍：创建所有知识点，暂时不设置父子关系
        for (Map<String, Object> row : data) {
            try {
                String name = (String) row.get("name");
                String description = (String) row.get("description");
                Integer sortOrder = row.get("sortOrder") != null ? ((Number) row.get("sortOrder")).intValue() : 0;
                
                // 检查同名知识点是否已存在
                if (nameToIdMap.containsKey(name) || 
                    knowledgePointRepository.existsByCourseAndName(course, name)) {
                    throw new RuntimeException("知识点 '" + name + "' 已存在");
                }
                
                KnowledgePoint kp = KnowledgePoint.builder()
                    .name(name)
                    .description(description)
                    .course(course)
                    .level(1)
                    .sortOrder(sortOrder)
                    .build();
                
                KnowledgePoint saved = knowledgePointRepository.save(kp);
                createdKps.add(saved);
                nameToIdMap.put(name, saved.getId());
                
                log.info("创建知识点成功：{}", name);
            } catch (Exception e) {
                log.error("创建知识点失败：{}", e.getMessage());
                throw new RuntimeException("导入失败，已回滚所有数据：" + e.getMessage());
            }
        }
        
        // 第二遍：设置父子关系
        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> row = data.get(i);
            KnowledgePoint kp = createdKps.get(i);
            
            Long parentId = null;
            if (row.get("parentId") != null) {
                parentId = ((Number) row.get("parentId")).longValue();
            } else if (row.get("_tempParentName") != null) {
                String tempParentName = (String) row.get("_tempParentName");
                parentId = nameToIdMap.get(tempParentName);
                if (parentId == null) {
                    log.warn("父知识点 '{}' 未找到，将忽略父子关系", tempParentName);
                }
            }
            
            if (parentId != null) {
                KnowledgePoint parent = knowledgePointRepository.findById(parentId)
                    .orElse(null);
                if (parent != null) {
                    kp.setParent(parent);
                    kp.setLevel(parent.getLevel() + 1);
                    knowledgePointRepository.save(kp);
                }
            }
        }

        String message = String.format("导入完成！成功导入 %d 条知识点", createdKps.size());
        log.info(message);

        return ImportResult.builder()
            .success(true)
            .successCount(createdKps.size())
            .failCount(0)
            .message(message)
            .build();
    }

    /**
     * 构建错误消息
     */
    private String buildErrorMessage(List<ValidationError> errors) {
        StringBuilder sb = new StringBuilder();
        for (ValidationError error : errors) {
            sb.append(error.getErrorMessage()).append("\n");
        }
        return sb.toString();
    }
}