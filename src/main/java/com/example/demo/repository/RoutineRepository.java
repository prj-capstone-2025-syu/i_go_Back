package com.example.demo.repository;

import com.example.demo.entity.user.User;

import java.util.*;
import com.example.demo.entity.routine.Routine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineRepository extends JpaRepository<Routine, Long> {
    List<Routine> findAllByUserId(User user);
}
