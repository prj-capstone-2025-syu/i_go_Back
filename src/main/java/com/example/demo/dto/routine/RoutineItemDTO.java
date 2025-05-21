package com.example.demo.dto.routine;

import lombok.*;

@Data
public class RoutineItemDTO {
    private Long id;
    private String name;
    private int durationMinutes;
    private boolean isFlexibleTime;
    private int orderIndex;
}
