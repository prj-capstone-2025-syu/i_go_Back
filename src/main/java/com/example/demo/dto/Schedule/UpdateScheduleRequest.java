package com.example.demo.dto.Schedule;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateScheduleRequest {
    private Long routineId;
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String location;
    private String memo;
    private String supplies;
    private String category;
}