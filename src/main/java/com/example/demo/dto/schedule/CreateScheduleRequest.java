package com.example.demo.dto.schedule;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateScheduleRequest {
    private Long routineId;
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String startLocation;
    private Double startX;
    private Double startY;
    private String location;
    private Double destinationX;
    private Double destinationY;
    private String memo;
    private String supplies;
    private String category;
}