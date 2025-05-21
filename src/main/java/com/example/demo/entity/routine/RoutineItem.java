package com.example.demo.entity.routine;

import jakarta.persistence.*;
import lombok.Data;

@Entity @Data
public class RoutineItem {
    @Id @GeneratedValue
    private Long id;

    private String name;
    private int durationMinutes;
    private boolean isFlexible;
    private int orderIndex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id")
    private Routine routine;
}
