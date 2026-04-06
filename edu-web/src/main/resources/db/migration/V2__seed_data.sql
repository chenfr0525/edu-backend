-- Seed Users (password is '123456' BCrypt encoded)
INSERT INTO users (username, password, name, role) VALUES 
('student', '$2a$10$sMS2ftr1MJ1VtqEmBME89OnermYbi1Oz1qETR2GHGRHcRoaDnUxTu', '张三', 'STUDENT'),
('teacher', '$2a$10$sMS2ftr1MJ1VtqEmBME89OnermYbi1Oz1qETR2GHGRHcRoaDnUxTu', '王老师', 'TEACHER');

-- Seed Classes
INSERT INTO classes (name, teacher_id) VALUES ('高三(1)班', 2);

-- Seed Exams
INSERT INTO exams (name, class_id, exam_date) VALUES ('期中考试', 1, '2026-03-15 09:00:00');

-- Seed Grades
INSERT INTO grades (student_id, exam_id, subject, score) VALUES 
(1, 1, '语文', 85),
(1, 1, '数学', 92),
(1, 1, '英语', 78);

-- Seed Knowledge Points
INSERT INTO knowledge_points (name, category) VALUES 
('三角函数', '数学'),
('函数单调性', '数学'),
('数列求和', '数学');

-- Seed Questions
INSERT INTO questions (content, knowledge_point_id) VALUES 
('求 sin(x) 的导数', 1),
('判断 f(x)=x^2 的单调性', 2);

-- Seed Error Records
INSERT INTO error_records (student_id, question_id, exam_id) VALUES (1, 1, 1);

-- Seed Semesters
INSERT INTO semesters (code, name, start_date, end_date, is_current) VALUES 
('2024-2025-1', '2024-2025 第一学期', '2024-09-01', '2025-01-20', TRUE);

-- Seed Courses
INSERT INTO courses (name, code, teacher_id, credit, description) VALUES 
('Vue3 企业级项目实战', 'vue3-pro', 2, 4, '基于 Vue3 + TS 的全栈项目实战课程');

-- Seed Enrollments
INSERT INTO enrollments (student_id, course_id, semester_id, progress, score) VALUES 
(1, 1, 1, 75, 92.5);

-- Seed Attendances
INSERT INTO attendances (student_id, date, attendance_rate) VALUES 
(1, '2026-04-01', 100),
(1, '2026-04-02', 100),
(1, '2026-04-03', 95);

-- Seed Homeworks
INSERT INTO homeworks (course_id, title, content, deadline) VALUES 
(1, 'Vue3 基础语法练习', '完成响应式 API 相关练习题', '2026-04-15 23:59:59');

-- Seed Submissions
INSERT INTO submissions (homework_id, student_id, content, status, score, feedback) VALUES 
(1, 1, '已完成所有练习，详见附件代码。', 'GRADED', 95.0, '做的非常棒，逻辑清晰！');
