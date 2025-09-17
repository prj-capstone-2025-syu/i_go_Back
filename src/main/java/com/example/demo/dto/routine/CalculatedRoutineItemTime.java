package com.example.demo.dto.routine;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalculatedRoutineItemTime {
    private Long routineItemId;
    private String routineItemName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int durationMinutes;
    private Long routineId; // 어떤 루틴에 속한 아이템인지 식별하기 위함
}