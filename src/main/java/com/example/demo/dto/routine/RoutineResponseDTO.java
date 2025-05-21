package com.example.demo.dto.routine;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoutineResponseDTO {
    private Long id;
    private String name;
    private List<RoutineItemDTO> items;
    private int totalDurationMinutes;
}
