-- 插入用户数据 (密码为加密后的 '123456'，实际使用时需替换为真实加密密码)
INSERT INTO users (username, password, name, role, gender, email, phone, status) VALUES
('teacher1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5E9', '张明', 'TEACHER', '男', 'zhang.ming@school.com', '13800000001', 'active'),
('teacher2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5E9', '李芳', 'TEACHER', '女', 'li.fang@school.com', '13800000002', 'active'),
('student1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5E9', '王小明', 'STUDENT', '男', 'wang.xiaoming@school.com', '13800000011', 'active'),
('student2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5E9', '李小萌', 'STUDENT', '女', 'li.xiaomeng@school.com', '13800000012', 'active'),
('student3', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5E9', '陈小东', 'STUDENT', '男', 'chen.xiaodong@school.com', '13800000013', 'active'),
('student4', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5E9', '赵小薇', 'STUDENT', '女', 'zhao.xiaowei@school.com', '13800000014', 'active'),
('student5', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5E9', '周小杰', 'STUDENT', '男', 'zhou.xiaojie@school.com', '13800000015', 'active'),
('teacher3', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5E9', '王丽', 'TEACHER', '女', 'wang.li@school.com', '13800000003', 'active');

-- 插入教师扩展信息
INSERT INTO teacher (user_id, teacher_no, department, title, office, join_date) VALUES
(1, 'T20240001', '计算机科学系', '副教授', '信息楼301', '2015-09-01'),
(2, 'T20240002', '数学系', '讲师', '数学楼205', '2018-09-01'),
(8, 'T20240003', '计算机科学系', '教授', '信息楼302', '2010-09-01');

-- 插入班级信息
INSERT INTO classes (name, grade, teacher_id) VALUES
('计算机1班', '大一', 1),
('计算机2班', '大一', 3),
('数学1班', '大一', 2);

-- 插入学生信息
INSERT INTO student (user_id, student_no, class_id, grade) VALUES
(3, 'S20240001', 1, '大一'),
(4, 'S20240002', 1, '大一'),
(5, 'S20240003', 2, '大一'),
(6, 'S20240004', 2, '大一'),
(7, 'S20240005', 3, '大一');

-- 插入课程信息
INSERT INTO course (name, description, teacher_id, credit, status) VALUES
('Java程序设计', 'Java基础与面向对象编程', 1, 4, 'ongoing'),
('数据库原理', 'MySQL数据库设计与应用', 3, 3, 'ongoing'),
('高等数学', '微积分与线性代数基础', 2, 4, 'ongoing');

-- 插入学期信息
INSERT INTO semester (name, start_date, end_date, is_current) VALUES
('2024-2025学年第一学期', '2024-09-01', '2025-01-15', 1),
('2024-2025学年第二学期', '2025-03-01', '2025-07-10', 0);

-- 插入选课记录
INSERT INTO enrollment (student_id, course_id, semester_id, progress, score, status) VALUES
(1, 1, 1, 65, NULL, 'ongoing'),
(1, 2, 1, 50, NULL, 'ongoing'),
(2, 1, 1, 70, NULL, 'ongoing'),
(2, 3, 1, 80, NULL, 'ongoing'),
(3, 2, 1, 45, NULL, 'ongoing'),
(3, 1, 1, 60, NULL, 'ongoing'),
(4, 3, 1, 75, NULL, 'ongoing');

-- 插入知识点
INSERT INTO knowledge_point (name, description, course_id, parent_id, level, sort_order) VALUES
('Java基础语法', '变量、数据类型、运算符等', 1, NULL, 0, 1),
('面向对象', '类、对象、继承、多态', 1, NULL, 0, 2),
('集合框架', 'List、Set、Map等', 1, NULL, 0, 3),
('SQL基础', '增删改查语句', 2, NULL, 0, 1),
('函数与极限', '函数概念、极限计算', 3, NULL, 0, 1);

-- 插入作业
INSERT INTO homework (name, description, knowledge_point_id, course_id, question_count, total_score, status, deadline) VALUES
('Java作业1：基础语法练习', '变量、循环、条件判断', 1, 1, 10, 100, 'ongoing', '2024-12-20 23:59:59'),
('SQL作业1：查询练习', '多表查询与聚合函数', 4, 2, 8, 100, 'ongoing', '2024-12-22 23:59:59');

-- 插入作业提交记录
INSERT INTO submission (homework_id, student_id, content, status, submitted_at) VALUES
(1, 1, '已完成所有题目', 'submitted', '2024-12-15 10:30:00'),
(1, 2, '部分题目未完成', 'submitted', '2024-12-16 14:20:00'),
(2, 1, '已完成', 'submitted', '2024-12-18 09:15:00');

-- 插入考试
INSERT INTO exam (name, type, class_id, course_id, exam_date, start_time, end_time, duration, full_score, pass_score, location, status) VALUES
('Java期中考试', 'midterm', 1, 1, '2024-11-15', '09:00:00', '11:00:00', 120, 100, 60, '信息楼101', 'completed'),
('数据库期末考试', 'final', 2, 2, '2025-01-10', '14:00:00', '16:00:00', 120, 100, 60, '信息楼102', 'upcoming');

-- 插入考试成绩
INSERT INTO exam_grade (exam_id, student_id, score, remark) VALUES
(1, 1, 85, '优秀'),
(1, 2, 72, '良好');

-- 插入知识点掌握度
INSERT INTO student_knowledge_mastery (student_id, knowledge_point_id, mastery_level, score, last_practice_time) VALUES
(1, 1, 75.00, 75, '2024-12-18 10:00:00'),
(1, 4, 80.00, 80, '2024-12-19 14:30:00'),
(2, 1, 65.00, 65, '2024-12-17 09:00:00'),
(2, 5, 70.00, 70, '2024-12-16 15:20:00');

-- 插入活动记录
INSERT INTO activity_record (student_id, type, description, activity_date, study_duration) VALUES
(1, 'study', '学习Java集合框架', '2024-12-18', 90),
(1, 'homework', '完成Java作业', '2024-12-15', 60),
(2, 'study', '学习SQL多表查询', '2024-12-17', 45),
(2, 'video', '观看数据库原理视频', '2024-12-16', 30),
(3, 'study', '学习高等数学', '2024-12-18', 120);

-- 插入错题记录
INSERT INTO error_record (name, student_id, knowledge_point_id, last_error_at) VALUES
('Java循环练习题', 2, 1, '2024-12-10 20:30:00'),
('SQL连接查询题', 3, 4, '2024-12-12 19:45:00');

-- 插入菜单数据
-- ========================
-- 学生菜单 (role = 'STUDENT')
-- ========================
INSERT INTO Menu (role, name, path, component, icon, children_id, meta) VALUES 
('STUDENT', '个人驾驶舱', 'dashboard', 'student/dashboard/index.vue', 'Monitor', NULL, '{"title":"控制台","keepAlive":true}'),
('STUDENT', '知识点掌握', 'knowledge-mastery', 'student/knowledge-mastery/index.vue', 'Star', NULL, '{"title":"知识点掌握"}'),
('STUDENT', '课程概览', 'course-overview', 'student/course-overview/index.vue', 'Memo', NULL, '{"title":"课程概览"}'),
('STUDENT', '作业跟踪', 'homework-tracking', 'student/homework-tracking/index.vue', 'Files', NULL, '{"title":"作业跟踪"}'),
('STUDENT', '成绩分析', 'grade-analysis', 'student/grade-analysis/index.vue', 'TrendCharts', NULL, '{"title":"成绩分析"}'),
('STUDENT', '个人中心', '/personal-center', 'personal-center/index.vue', 'User', NULL, '{"title":"个人中心"}');

-- ========================
-- 教师菜单 (role = 'TEACHER')
-- ========================
INSERT INTO Menu (role, name, path, component, icon, children_id, meta) VALUES 
('TEACHER', '教学看板', 'dashboard', 'teacher/dashboard/index.vue', 'Monitor', NULL, '{"title":"控制台"}'),
('TEACHER', '学生管理', 'user-manage', 'teacher/user-manage/index.vue', 'User', NULL, '{"title":"学生管理"}'),
('TEACHER', '作业管理', 'work-manage', 'teacher/work-manage/index.vue', 'MessageBox', NULL, '{"title":"作业管理"}'),
('TEACHER', '考试管理', 'exam-manage', 'teacher/exam-manage/index.vue', 'Calendar', NULL, '{"title":"考试管理"}'),
('TEACHER', '课程分析', 'course-analysis', 'teacher/course-analysis/index.vue', 'Memo', NULL, '{"title":"课程分析"}'),
('TEACHER', '班级成绩', 'class-grade', 'teacher/class-grade/index.vue', 'Star', NULL, '{"title":"班级成绩"}'),
('TEACHER', '活跃度监控', 'activity-monitor', 'teacher/activity-monitor/index.vue', 'Monitor', NULL, '{"title":"活跃度监控"}'),
('TEACHER', '个人中心', '/personal-center', 'personal-center/index.vue', 'User', NULL, '{"title":"个人中心"}');