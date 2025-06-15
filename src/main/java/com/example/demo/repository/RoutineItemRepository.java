package com.example.demo.repository;

import com.example.demo.entity.routine.RoutineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoutineItemRepository extends JpaRepository<RoutineItem, Long> {
    // 사용자 ID로 모든 루틴 아이템 삭제
    @Modifying
    @Query("DELETE FROM RoutineItem ri WHERE ri.routine.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
