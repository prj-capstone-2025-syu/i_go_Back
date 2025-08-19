package com.example.demo.dto.schedule;

import com.example.demo.entity.schedule.Schedule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleWeatherResponse {
    private Long scheduleId;
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String location;
    private Double destinationX; // 경도
    private Double destinationY; // 위도

    // 날씨 정보
    private WeatherInfo weather;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeatherInfo {
        private double temperature;
        private double feelsLike;
        private int humidity;
        private String description;
        private String weatherType;
        private String icon;
    }

    public static ScheduleWeatherResponse fromSchedule(Schedule schedule) {
        return ScheduleWeatherResponse.builder()
                .scheduleId(schedule.getId())
                .title(schedule.getTitle())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .location(schedule.getLocation())
                .destinationX(schedule.getDestinationX())
                .destinationY(schedule.getDestinationY())
                .build();
    }
}
