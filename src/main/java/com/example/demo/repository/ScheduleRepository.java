package com.example.demo.repository;

import com.example.demo.entity.schedule.Schedule;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    List<Schedule> findByUserIdAndStartTimeAfterOrderByStartTimeAsc(Long userId, LocalDateTime startTime, Pageable pageable);
    List<Schedule> findByUserIdAndStartTimeBetween(Long userId, LocalDateTime start, LocalDateTime end);
    List<Schedule> findByUserIdAndTitleAndStartTime(Long userId, String title, LocalDateTime startTime);
    List<Schedule> findByUserIdAndStartTime(Long userId, LocalDateTime startTime);
    List<Schedule> findByUserIdAndTitle(Long userId, String title);
    @Query("SELECT s FROM Schedule s WHERE s.startTime >= :startTimeStart AND s.startTime < :startTimeEnd AND s.status = :status AND s.user.fcmToken IS NOT NULL AND s.user.fcmToken <> ''")
    List<Schedule> findByStartTimeBetweenAndStatusAndUserFcmTokenIsNotNull(
            @Param("startTimeStart") LocalDateTime startTimeStart,
            @Param("startTimeEnd") LocalDateTime startTimeEnd,
            @Param("status") Schedule.ScheduleStatus status
    );
    // IN_PROGRESS 상태이고 FCM 토큰이 있는 사용자의 스케줄 조회
    @Query("SELECT s FROM Schedule s WHERE s.status = :status AND s.user.fcmToken IS NOT NULL AND s.user.fcmToken <> ''")
    List<Schedule> findByStatusAndUserFcmTokenIsNotNull(@Param("status") Schedule.ScheduleStatus status);

    // 진행 중인 일정 조회 (startTime <= now AND endTime > now), 가장 최근 시작된 것 우선
    @Query("SELECT s FROM Schedule s WHERE s.user.id = :userId AND s.startTime <= :now AND s.endTime > :now ORDER BY s.startTime DESC")
    List<Schedule> findLatestInProgressSchedulesByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now, Pageable pageable);
}