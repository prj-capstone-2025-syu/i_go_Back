package com.example.demo.dto.weather;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
//main: 날씨 상태(예: "Clear", "Rain", "Snow" 등)
//description: 상태에 대한 상세 설명(예: "맑음", "약한 비" 등)
//icon: 날씨 아이콘 코드(예: "01d", "09n" 등, 아이콘 이미지를 표시할 때 사용)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherDetails {
    private int id;
    private String main;
    private String description;
    private String icon;
}
