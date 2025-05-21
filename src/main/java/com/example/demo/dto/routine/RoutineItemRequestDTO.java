package com.example.demo.dto.routine;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoutineItemRequestDTO {
    private String name;
    private int durationMinutes;
    private boolean isFlexibleTime;
}
