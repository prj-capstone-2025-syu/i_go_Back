package com.example.demo.dto.routine;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoutineItemRequestDTO {
    private String name;
    private int durationMinutes;
    @JsonProperty("isFlexibleTime")
    private boolean isFlexibleTime;
}
