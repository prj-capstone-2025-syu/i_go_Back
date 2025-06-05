package com.example.demo.dto.Schedule;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateScheduleRequest {
    private Long routineId;
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // 출발지 정보 추가
    private String startLocation;
    private Double startX;
    private Double startY;

    // 기존 location은 도착지를 의미
    private String location;
    private Double destinationX;
    private Double destinationY;

    private String memo;
    private String supplies;
    private String category;
}