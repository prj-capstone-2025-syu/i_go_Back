package com.example.demo.repository;

import com.example.demo.entity.fcm.Notification;
import com.example.demo.entity.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserOrderByCreatedAtDesc(User user);
    List<Notification> findByUserAndIsReadOrderByCreatedAtDesc(User user, boolean isRead);

    // 특정 사용자의 특정 관련 ID와 타입으로 알림이 존재하는지 확인하기 위한 메서드
    Optional<Notification> findByUserAndRelatedIdAndNotificationType(User user, Long relatedId, String notificationType);

    // 사용자의 최근 알림을 Pageable을 사용하여 조회
    List<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    // 사용자 ID로 모든 알림 삭제
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}

