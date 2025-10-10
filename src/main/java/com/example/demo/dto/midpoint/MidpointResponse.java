package com.example.demo.dto.midpoint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for the midpoint calculation response
 * Contains the calculated midpoint coordinates and address
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MidpointResponse {
    private Coordinates midpointCoordinates;
    private String midpointAddress;
    private boolean success;
    private String message;
}
