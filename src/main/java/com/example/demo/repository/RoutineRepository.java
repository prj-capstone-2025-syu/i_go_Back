package com.example.demo.repository;

import com.example.demo.entity.user.User;
import java.util.*;
import com.example.demo.entity.routine.Routine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoutineRepository extends JpaRepository<Routine, Long> {
    // 문제였던 코드: List<Routine> findAllByUserId(User user);
    List<Routine> findAllByUser(User user);

    // 사용자 ID로 모든 루틴 삭제
    @Modifying
    @Query("DELETE FROM Routine r WHERE r.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}

