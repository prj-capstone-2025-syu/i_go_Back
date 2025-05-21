package com.example.demo.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateScheduleRequest {
    private Long routineId;
    private String title;
    private LocalDateTime startTime;
    private String location;
    private String memo;
    private String category;
}