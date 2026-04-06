-- Update users table
ALTER TABLE users ADD COLUMN student_no VARCHAR(50);
ALTER TABLE users ADD COLUMN email VARCHAR(255);
ALTER TABLE users ADD COLUMN phone VARCHAR(50);
ALTER TABLE users ADD COLUMN avatar VARCHAR(255);
ALTER TABLE users ADD COLUMN status VARCHAR(50) DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN last_login_time TIMESTAMP NULL;

-- Create semesters table
CREATE TABLE semesters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    start_date DATE,
    end_date DATE,
    is_current BOOLEAN DEFAULT FALSE
);

-- Create courses table
CREATE TABLE courses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) UNIQUE,
    teacher_id BIGINT,
    credit INT,
    description TEXT,
    FOREIGN KEY (teacher_id) REFERENCES users(id)
);

-- Create enrollments table
CREATE TABLE enrollments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT,
    course_id BIGINT,
    semester_id BIGINT,
    progress INT DEFAULT 0,
    score DOUBLE,
    FOREIGN KEY (student_id) REFERENCES users(id),
    FOREIGN KEY (course_id) REFERENCES courses(id),
    FOREIGN KEY (semester_id) REFERENCES semesters(id)
);

-- Create attendances table
CREATE TABLE attendances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT,
    date DATE NOT NULL,
    attendance_rate INT DEFAULT 100,
    FOREIGN KEY (student_id) REFERENCES users(id)
);

-- Create homeworks table
CREATE TABLE homeworks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    deadline TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (course_id) REFERENCES courses(id)
);

-- Create submissions table
CREATE TABLE submissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    homework_id BIGINT,
    student_id BIGINT,
    content TEXT,
    files VARCHAR(1000),
    status VARCHAR(50) DEFAULT 'SUBMITTED',
    score DOUBLE,
    feedback TEXT,
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    graded_at TIMESTAMP NULL,
    FOREIGN KEY (homework_id) REFERENCES homeworks(id),
    FOREIGN KEY (student_id) REFERENCES users(id)
);
