-- Fix user passwords to be valid BCrypt hashes for '123456'
UPDATE users SET password = '$2a$10$sMS2ftr1MJ1VtqEmBME89OnermYbi1Oz1qETR2GHGRHcRoaDnUxTu' WHERE username IN ('student', 'teacher');
