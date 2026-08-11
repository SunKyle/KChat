package com.example.app.repository;

import com.example.app.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, String> {

    List<Reminder> findByUserIdAndStatusOrderByRemindAtAsc(String userId, String status);

    List<Reminder> findByUserIdOrderByRemindAtAsc(String userId);

    /**
     * 查找所有已到期但尚未触发的提醒。
     */
    @Query("SELECT r FROM Reminder r WHERE r.status = 'pending' AND r.remindAt <= :now")
    List<Reminder> findDueReminders(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Reminder r SET r.status = 'cancelled' WHERE r.id = :id AND r.userId = :userId AND r.status = 'pending'")
    int cancelByIdAndUserId(@Param("id") String id, @Param("userId") String userId);
}