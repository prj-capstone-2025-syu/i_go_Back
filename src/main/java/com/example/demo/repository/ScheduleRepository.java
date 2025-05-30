package com.example.demo.repository;

import com.example.demo.entity.schedule.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    List<Schedule> findByUserIdAndStartTimeAfterOrderByStartTimeAsc(Long userId, LocalDateTime startTime, Pageable pageable);
    List<Schedule> findByUserIdAndStartTimeBetween(Long userId, LocalDateTime start, LocalDateTime end);
    List<Schedule> findByUserIdAndTitleAndStartTime(Long userId, String title, LocalDateTime startTime);
    List<Schedule> findByUserIdAndStartTime(Long userId, LocalDateTime startTime);
    List<Schedule> findByUserIdAndTitle(Long userId, String title);
}