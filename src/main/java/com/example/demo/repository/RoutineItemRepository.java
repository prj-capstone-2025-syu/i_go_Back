package com.example.demo.repository;

import com.example.demo.entity.routine.Routine;
import com.example.demo.entity.routine.RoutineItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineItemRepository extends JpaRepository<RoutineItem, Long> {
}
