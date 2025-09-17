package com.example.demo.dto.weather;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
// WeatherResponse 내부의 핵심 온도/습도 정보만 담음
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherMain {
    private double temp;
    private double feels_like;
    private double temp_min;
    private double temp_max;
    private int pressure;
    private int humidity;
}
