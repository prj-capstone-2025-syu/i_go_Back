package com.example.demo.dto.weather;

//OpenWeatherMap API에서 받는 전체 응답 데이터를 담음

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherResponse {
    private WeatherMain main;
    private List<WeatherDetails> weather;
    private String name;
    private Coordinates coord;
    private int visibility;
    private Wind wind;
    private Clouds clouds;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Wind {
        private double speed;
        private int deg;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Clouds {
        private int all;
    }
}
