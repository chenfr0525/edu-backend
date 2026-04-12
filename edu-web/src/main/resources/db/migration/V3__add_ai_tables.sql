-- 1. AI分析报告表
CREATE TABLE IF NOT EXISTS `ai_analysis_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `target_type` VARCHAR(20) NOT NULL COMMENT '目标类型: STUDENT/CLASS/COURSE',
    `target_id` BIGINT NOT NULL COMMENT '目标ID',
    `semester_id` BIGINT NOT NULL COMMENT '学期ID',
    `report_type` VARCHAR(30) NOT NULL COMMENT '报告类型: HOMEWORK/EXAM/ACTIVITY/KNOWLEDGE/COMPREHENSIVE',
    `analysis_data` JSON NOT NULL COMMENT 'AI分析数据',
    `charts_config` JSON COMMENT '可视化图表配置',
    `suggestions` TEXT COMMENT 'AI生成的个性化建议',
    `summary` TEXT COMMENT '分析摘要',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_target` (`target_type`, `target_id`),
    KEY `idx_semester` (`semester_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI分析报告表';

-- 2. 知识点得分明细表
CREATE TABLE IF NOT EXISTS `knowledge_point_score_detail` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `student_id` BIGINT NOT NULL,
    `knowledge_point_id` BIGINT NOT NULL,
    `source_type` VARCHAR(20) NOT NULL COMMENT '来源: HOMEWORK/EXAM',
    `source_id` BIGINT NOT NULL COMMENT '作业ID或考试ID',
    `score_rate` DECIMAL(5,2) NOT NULL COMMENT '该知识点得分率(0-100)',
    `max_score` DECIMAL(5,2) COMMENT '该知识点满分',
    `actual_score` DECIMAL(5,2) COMMENT '实际得分',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_student_kp` (`student_id`, `knowledge_point_id`),
    KEY `idx_source` (`source_type`, `source_id`),
    FOREIGN KEY (`student_id`) REFERENCES `student`(`id`),
    FOREIGN KEY (`knowledge_point_id`) REFERENCES `knowledge_point`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点得分明细表';

-- 3. 成绩预测表
CREATE TABLE IF NOT EXISTS `score_prediction` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `student_id` BIGINT NOT NULL,
    `course_id` BIGINT NOT NULL,
    `exam_type` VARCHAR(30) COMMENT '考试类型',
    `predicted_score` DECIMAL(5,2) NOT NULL COMMENT '预测分数',
    `confidence_lower` DECIMAL(5,2) COMMENT '置信区间下限',
    `confidence_upper` DECIMAL(5,2) COMMENT '置信区间上限',
    `trend` VARCHAR(20) COMMENT '趋势: IMPROVING/STABLE/DECLINING',
    `factors` JSON COMMENT '影响因素',
    `prediction_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `actual_score` DECIMAL(5,2) DEFAULT NULL COMMENT '实际成绩',
    PRIMARY KEY (`id`),
    KEY `idx_student_course` (`student_id`, `course_id`),
    FOREIGN KEY (`student_id`) REFERENCES `student`(`id`),
    FOREIGN KEY (`course_id`) REFERENCES `course`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成绩预测表';

-- 4. 活跃度预警表
CREATE TABLE IF NOT EXISTS `activity_alert` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `student_id` BIGINT NOT NULL,
    `class_id` BIGINT NOT NULL,
    `semester_id` BIGINT NOT NULL,
    `alert_type` VARCHAR(30) NOT NULL COMMENT '预警类型: LOW_ACTIVITY/DECLINING_TREND/NO_SUBMISSION',
    `alert_level` VARCHAR(20) NOT NULL COMMENT '预警级别: INFO/WARNING/CRITICAL',
    `activity_score` DECIMAL(5,2) COMMENT '当前活跃度得分',
    `threshold` DECIMAL(5,2) COMMENT '阈值',
    `details` JSON COMMENT '详细数据',
    `is_resolved` BOOLEAN DEFAULT FALSE,
    `resolved_at` DATETIME DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_student` (`student_id`),
    KEY `idx_unresolved` (`is_resolved`),
    FOREIGN KEY (`student_id`) REFERENCES `student`(`id`),
    FOREIGN KEY (`class_id`) REFERENCES `classes`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活跃度预警表';

-- 5. 班级高频错题统计表
CREATE TABLE IF NOT EXISTS `class_wrong_question_stats` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `class_id` BIGINT NOT NULL,
    `knowledge_point_id` BIGINT NOT NULL,
    `source_type` VARCHAR(20) NOT NULL COMMENT 'HOMEWORK/EXAM',
    `source_id` BIGINT NOT NULL,
    `error_count` INT NOT NULL COMMENT '错误人数',
    `total_students` INT NOT NULL COMMENT '总学生数',
    `error_rate` DECIMAL(5,2) COMMENT '错误率百分比',
    `rank_in_class` INT COMMENT '错题排名',
    `stat_date` DATE NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_class_date` (`class_id`, `stat_date`),
    FOREIGN KEY (`class_id`) REFERENCES `classes`(`id`),
    FOREIGN KEY (`knowledge_point_id`) REFERENCES `knowledge_point`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级高频错题统计表';