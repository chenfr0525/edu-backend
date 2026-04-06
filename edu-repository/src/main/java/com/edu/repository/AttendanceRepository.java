package com.edu.repository;

import com.edu.domain.Attendance;
import com.edu.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findByStudentAndDateBetween(User student, LocalDate startDate, LocalDate endDate);
}
