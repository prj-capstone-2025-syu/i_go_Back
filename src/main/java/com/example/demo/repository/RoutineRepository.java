package com.example.demo.repository;

import com.example.demo.entity.user.User;
import java.util.*;
import com.example.demo.entity.routine.Routine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoutineRepository extends JpaRepository<Routine, Long> {
    List<Routine> findAllByUser(User user);

    // 사용자와 루틴 이름으로 검색 (대소문자 구분 없음)
    Optional<Routine> findByUserAndNameIgnoreCase(User user, String name);

    // 사용자 ID와 루틴 이름으로 검색
    @Query("SELECT r FROM Routine r WHERE r.user.id = :userId AND LOWER(r.name) = LOWER(:name)")
    Optional<Routine> findByUserIdAndNameIgnoreCase(@Param("userId") Long userId, @Param("name") String name);

    // 사용자 ID와 루틴 이름으로 검색 (부분 일치, 대소문자 구분 없음)
    @Query("SELECT r FROM Routine r WHERE r.user.id = :userId AND LOWER(r.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Routine> findByUserIdAndNameContainingIgnoreCase(@Param("userId") Long userId, @Param("name") String name);

    // 사용자 ID로 모든 루틴 삭제
    @Modifying
    @Query("DELETE FROM Routine r WHERE r.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
