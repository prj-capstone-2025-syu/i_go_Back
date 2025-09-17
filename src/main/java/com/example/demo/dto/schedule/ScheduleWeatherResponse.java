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

    // 시작 위치 정보
    private String startLocation;
    private Double startX; // 시작 위치 경도
    private Double startY; // 시작 위치 위도

    // 목적지 정보
    private String location; // 목적지
    private Double destinationX; // 목적지 경도
    private Double destinationY; // 목적지 위도

    // 날씨 정보 - 출발지와 도착지 분리
    private WeatherInfo startLocationWeather;      // 출발지 날씨
    private WeatherInfo destinationWeather;        // 도착지 날씨

    // 하위 호환성을 위한 기존 필드 (deprecated)
    @Deprecated
    private WeatherInfo weather; // 기존 코드 호환성을 위해 유지, destinationWeather와 동일

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
                .startLocation(schedule.getStartLocation())
                .startX(schedule.getStartX())
                .startY(schedule.getStartY())
                .location(schedule.getLocation())
                .destinationX(schedule.getDestinationX())
                .destinationY(schedule.getDestinationY())
                .build();
    }

    // 하위 호환성을 위한 메서드들
    public void setWeather(WeatherInfo weather) {
        this.weather = weather;
        this.destinationWeather = weather; // 기존 코드 호환성
    }

    public WeatherInfo getWeather() {
        return destinationWeather != null ? destinationWeather : weather;
    }
}
