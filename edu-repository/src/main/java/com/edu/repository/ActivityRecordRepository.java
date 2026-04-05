package com.edu.repository;

import com.edu.domain.ActivityRecord;
import com.edu.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActivityRecordRepository extends JpaRepository<ActivityRecord, Long> {
    List<ActivityRecord> findByUser(User user);
}
