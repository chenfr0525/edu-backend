-- 2. homework 表扩展（AI 解析数据）
ALTER TABLE homework ADD COLUMN ai_parsed_data JSON COMMENT 'AI解析的作业数据(知识点分布、每题分值等)';
ALTER TABLE homework ADD COLUMN knowledge_points_mapping JSON COMMENT '题目与知识点的映射关系';
ALTER TABLE homework ADD COLUMN avg_score DECIMAL(5,2) DEFAULT NULL COMMENT '班级平均分';
ALTER TABLE homework ADD COLUMN pass_rate DECIMAL(5,2) DEFAULT NULL COMMENT '及格率';

-- 3. exam 表扩展
ALTER TABLE exam ADD COLUMN ai_parsed_data JSON COMMENT 'AI解析的考试数据';
ALTER TABLE exam ADD COLUMN knowledge_points_distribution JSON COMMENT '知识点分值分布';
ALTER TABLE exam ADD COLUMN class_avg_score DECIMAL(5,2) DEFAULT NULL COMMENT '班级平均分';
ALTER TABLE exam ADD COLUMN highest_score DECIMAL(5,2) DEFAULT NULL COMMENT '最高分';
ALTER TABLE exam ADD COLUMN lowest_score DECIMAL(5,2) DEFAULT NULL COMMENT '最低分';

-- 4. exam_grade 表扩展
ALTER TABLE exam_grade ADD COLUMN class_rank INT COMMENT '班级排名';
ALTER TABLE exam_grade ADD COLUMN grade_rank INT COMMENT '年级排名';
ALTER TABLE exam_grade ADD COLUMN score_trend VARCHAR(20) COMMENT '成绩趋势: UP/STABLE/DOWN';
ALTER TABLE exam_grade ADD COLUMN knowledge_point_scores JSON COMMENT '各知识点得分详情';

-- 5. submission 表扩展
ALTER TABLE submission ADD COLUMN knowledge_point_scores JSON COMMENT '各知识点得分详情';
ALTER TABLE submission ADD COLUMN submission_late_minutes INT DEFAULT 0 COMMENT '延迟提交分钟数';
ALTER TABLE submission ADD COLUMN ai_feedback TEXT COMMENT 'AI生成的反馈建议';

-- 6. student_knowledge_mastery 表扩展
ALTER TABLE student_knowledge_mastery ADD COLUMN weakness_level VARCHAR(20) COMMENT '薄弱程度: SEVERE/MODERATE/MILD/GOOD';
ALTER TABLE student_knowledge_mastery ADD COLUMN suggested_actions TEXT COMMENT '建议的学习行动';
ALTER TABLE student_knowledge_mastery ADD COLUMN last_exam_score_rate DECIMAL(5,2) COMMENT '最近一次考试该知识点得分率';

-- 7. activity_record 表扩展
ALTER TABLE activity_record ADD COLUMN activity_score DECIMAL(5,2) DEFAULT 0 COMMENT '活跃度得分(0-100)';
ALTER TABLE activity_record ADD COLUMN interaction_count INT DEFAULT 0 COMMENT '互动次数';
ALTER TABLE activity_record ADD COLUMN resource_access_count INT DEFAULT 0 COMMENT '资源访问次数';