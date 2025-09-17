package com.example.demo.dto.weather;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 위도/경도 DTO
public class Coordinates {
    private double lat;
    private double lon;
}
