package com.example.demo.dto.midpoint;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for representing geographical coordinates (latitude and longitude)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Coordinates {
    private double lat;
    private double lng;
}
