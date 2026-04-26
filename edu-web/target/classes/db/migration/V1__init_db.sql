CREATE TABLE users (
    id BIGINT  NOT NULL  AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    gender VARCHAR(10) DEFAULT NULL COMMENT '性别',
    avatar VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    email VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    phone VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/FROZEN/PENDING',
    last_login_time DATETIME DEFAULT NULL COMMENT '最后登录时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 学生表
CREATE TABLE IF NOT EXISTS `student` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
     `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `student_no` VARCHAR(20) NOT NULL COMMENT '学号',
    `class_id` BIGINT COMMENT '班级ID',
    `grade` VARCHAR(20) NOT NULL COMMENT '年级',    
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student_no` (`student_no`),
     KEY `idx_user_id` (`user_id`),
     KEY `idx_class_id` (`class_id`),
    CONSTRAINT `fk_student_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) 
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生表';

-- 教师扩展表
CREATE TABLE `teacher` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '教师ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `teacher_no` VARCHAR(50) NOT NULL COMMENT '教师工号',
    `department` VARCHAR(100) DEFAULT NULL COMMENT '部门',
    `title` VARCHAR(50) DEFAULT NULL COMMENT '职称',
    `office` VARCHAR(100) DEFAULT NULL COMMENT '办公室',
    `join_date` DATE DEFAULT NULL COMMENT '入职日期',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_teacher_no` (`teacher_no`),
    KEY `idx_user_id` (`user_id`),
    CONSTRAINT `fk_teacher_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师信息表';

CREATE TABLE Menu (
    id BIGINT  NOT NULL  AUTO_INCREMENT,
    role VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    path VARCHAR(255) NOT NULL,
    component VARCHAR(50) NOT NULL,
    icon VARCHAR(50) NOT NULL,
    children_id BIGINT DEFAULT NULL COMMENT '子菜单ID',
    meta VARCHAR(500) DEFAULT NULL COMMENT '元数据',
    PRIMARY KEY (`id`),
    KEY `idx_children_id` (`children_id`),
    FOREIGN KEY (`children_id`) REFERENCES `Menu` (`id`) ON DELETE SET NULL
);

CREATE TABLE classes (
    id BIGINT AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    grade VARCHAR(20) NOT NULL COMMENT '年级',
    teacher_id BIGINT DEFAULT NULL COMMENT '班主任ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    FOREIGN KEY (teacher_id) REFERENCES teacher(id)
);

-- 课程表
CREATE TABLE `course` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '课程ID',
    `name` VARCHAR(100) NOT NULL COMMENT '课程名称',
    `description` TEXT COMMENT '课程描述',
    `icon` VARCHAR(50) DEFAULT NULL COMMENT '图标',
    `teacher_id` BIGINT NOT NULL COMMENT '授课教师ID',
    `credit` INT DEFAULT 0 COMMENT '学分',
    `status` VARCHAR(20) DEFAULT 'ONGOING' COMMENT '状态: ONGOING/COMPLETED/DROPPED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_teacher_id` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表';

-- 学期表
CREATE TABLE `semester` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '学期ID',
    `name` VARCHAR(50) NOT NULL COMMENT '学期名称',
    `start_date` DATE NOT NULL COMMENT '开始日期',
    `end_date` DATE NOT NULL COMMENT '结束日期',
    `is_current` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否当前学期',
    PRIMARY KEY (`id`),
    KEY `idx_start_date` (`start_date`),
    KEY `idx_end_date` (`end_date`),
    KEY `idx_is_current` (`is_current`),
    CONSTRAINT `chk_date_range` CHECK (`start_date` < `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学期表';

-- 选课表
CREATE TABLE `enrollment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '选课ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `semester_id` BIGINT NOT NULL COMMENT '学期ID',
    `progress` INT DEFAULT 0 COMMENT '学习进度(%)',
    `score` DECIMAL(5,2) DEFAULT NULL COMMENT '最终成绩',
    `status` VARCHAR(20) DEFAULT 'ONGOING' COMMENT '状态: ONGOING/COMPLETED/DROPPED',
    `enrolled_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '选课时间',
    PRIMARY KEY (`id`),
    KEY `idx_student_id` (`student_id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_semester_id` (`semester_id`),
    CONSTRAINT `fk_enrollment_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
    CONSTRAINT `fk_enrollment_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
    CONSTRAINT `fk_enrollment_semester` FOREIGN KEY (`semester_id`) REFERENCES `semester` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='选课表';

-- 作业表
CREATE TABLE homework (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '作业ID',
    name  VARCHAR(200) NOT NULL COMMENT '作业标题',
    description TEXT COMMENT '作业描述',
    knowledge_point_ids JSON DEFAULT NULL COMMENT '知识点ID列表，如[1,2,3]',
    course_id BIGINT NOT  NULL COMMENT '课程ID',
    question_count INT NOT NULL DEFAULT 0 COMMENT '题目数量',
    total_score INT NOT NULL DEFAULT 100 COMMENT '总分',
    status VARCHAR(20) NOT NULL DEFAULT 'ONGOING' COMMENT '状态: ONGOING/PENDING/COMPLETED/EXPIRED',
    deadline DATETIME NOT NULL COMMENT '截止时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_knowledge_point_id` (`knowledge_point_id`),
    FOREIGN KEY (course_id) REFERENCES course(`id`)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作业表';

-- 作业提交表
CREATE TABLE `submission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '提交ID',
    `homework_id` BIGINT NOT NULL COMMENT '作业ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `content` TEXT COMMENT '提交内容',
    `attachments` VARCHAR(500) DEFAULT NULL COMMENT '附件',
    `score` INT DEFAULT NULL COMMENT '得分',
    `feedback` TEXT COMMENT '评语',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SUBMITTED/GRADED',
    `submitted_at` DATETIME DEFAULT NULL COMMENT '提交时间',
    `graded_at` DATETIME DEFAULT NULL COMMENT '批改时间',
    PRIMARY KEY (`id`),
    KEY `idx_homework_id` (`homework_id`),
    KEY `idx_student_id` (`student_id`),
    CONSTRAINT `fk_submission_homework` FOREIGN KEY (`homework_id`) REFERENCES `homework` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作业提交表';

-- 考试表
CREATE TABLE `exam` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '考试ID',
    `name` VARCHAR(200) NOT NULL COMMENT '考试名称',
    `type` VARCHAR(20) NOT NULL COMMENT '类型: MOCK/UNIT/MONTHLY/MIDTERM/FINAL',
    knowledge_point_ids JSON DEFAULT NULL COMMENT '知识点ID列表，如[1,2,3]',
    `class_id` BIGINT NOT NULL COMMENT '班级ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `exam_date` DATE NOT NULL COMMENT '考试日期',
    `start_time` TIME DEFAULT NULL COMMENT '开始时间',
    `end_time` TIME DEFAULT NULL COMMENT '结束时间',
    `duration` INT DEFAULT NULL COMMENT '时长(分钟)',
    `full_score` INT NOT NULL DEFAULT 100 COMMENT '总分',
    `pass_score` INT NOT NULL DEFAULT 60 COMMENT '及格分',
    `location` VARCHAR(100) DEFAULT NULL COMMENT '考试地点',
    `status` VARCHAR(20) NOT NULL DEFAULT 'UPCOMING' COMMENT '状态: UPCOMING/ONGOING/COMPLETED',
    `description` TEXT COMMENT '考试说明',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_class_id` (`class_id`),
    FOREIGN KEY (class_id) REFERENCES classes(`id`),
    FOREIGN KEY (course_id) REFERENCES course(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试表';

-- 考试成绩表
CREATE TABLE `exam_grade` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '成绩ID',
    `exam_id` BIGINT NOT NULL COMMENT '考试ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `score` DECIMAL(5,2) DEFAULT NULL COMMENT '成绩',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_exam_student` (`exam_id`, `student_id`),
    KEY `idx_student_id` (`student_id`),
    KEY `idx_exam_id` (`exam_id`),
    FOREIGN KEY (student_id) REFERENCES student(`id`),
    FOREIGN KEY (exam_id) REFERENCES exam(`id`),
    CONSTRAINT `fk_exam_grade_exam` FOREIGN KEY (`exam_id`) REFERENCES `exam` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试成绩表';

-- 知识点表
CREATE TABLE `knowledge_point` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '知识点ID',
    `name` VARCHAR(100) NOT NULL COMMENT '知识点名称',
    `description` TEXT COMMENT '描述',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父知识点ID',
    `level` INT NOT NULL DEFAULT 0 COMMENT '层级',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_parent_id` (`parent_id`),
    FOREIGN KEY (course_id) REFERENCES course(`id`),
    FOREIGN KEY (parent_id) REFERENCES knowledge_point(`id`),
    CONSTRAINT `fk_knowledge_point_parent` FOREIGN KEY (`parent_id`) REFERENCES `knowledge_point` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点表';

-- 学生知识点掌握度表
CREATE TABLE `student_knowledge_mastery` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `knowledge_point_id` BIGINT NOT NULL COMMENT '知识点ID',
    `mastery_level` DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '掌握度 0-100',
    `score` DECIMAL(5,2) DEFAULT NULL COMMENT '成绩',
    `last_practice_time` DATETIME DEFAULT NULL COMMENT '最后练习时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student_knowledge` (`student_id`, `knowledge_point_id`),
    KEY `idx_student_id` (`student_id`),
    KEY `idx_knowledge_point_id` (`knowledge_point_id`),
    FOREIGN KEY (student_id) REFERENCES student(`id`),
    FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(`id`),
    CONSTRAINT `fk_mastery_knowledge_point` FOREIGN KEY (`knowledge_point_id`) REFERENCES `knowledge_point` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生知识点掌握度表';

-- 活动记录表（可用于生成假数据）
CREATE TABLE `activity_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `type` VARCHAR(20) NOT NULL DEFAULT 'visit' COMMENT '类型: LOGIN/HOMEWORK/EXAM/RESOURCE/VIDEO',
    `description` TEXT COMMENT '描述',
    `activity_date` DATE NOT NULL COMMENT '活动日期',
    `study_duration` INT NOT NULL DEFAULT 0 COMMENT '学习时长(分钟)',
    PRIMARY KEY (`id`),
    KEY `idx_student_id` (`student_id`),
    KEY `idx_activity_date` (`activity_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动记录表';

-- 错题表
CREATE TABLE `error_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `name` VARCHAR(100) NOT NULL COMMENT '错题名称',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `knowledge_point_id` BIGINT NOT NULL COMMENT '知识点ID',
    `last_error_at` DATETIME DEFAULT NULL COMMENT '最近错误时间',
    PRIMARY KEY (`id`),
    KEY `idx_student_id` (`student_id`),
    KEY `idx_knowledge_point_id` (`knowledge_point_id`),
    FOREIGN KEY (student_id) REFERENCES student(`id`),
    FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(`id`),
    CONSTRAINT `fk_error_knowledge_point` FOREIGN KEY (`knowledge_point_id`) REFERENCES `knowledge_point` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='错题表';

