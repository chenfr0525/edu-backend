-- Seed Users (password is '123456' BCrypt encoded)
INSERT INTO users (username, password, name, role) VALUES 
('student', '$2a$10$ByI78zLvGzJua9yU.W3Y9u16Z2fXfXfXfXfXfXfXfXfXfXfXfXfX.', '张三', 'STUDENT'),
('teacher', '$2a$10$ByI78zLvGzJua9yU.W3Y9u16Z2fXfXfXfXfXfXfXfXfXfXfXfXfX.', '王老师', 'TEACHER');

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
