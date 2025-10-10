package com.example.demo.dto.midpoint;

import lombok.Data;
import java.util.List;

/**
 * DTO for the midpoint calculation request
 * Contains a list of location names to find the midpoint for
 */
@Data
public class MidpointRequest {
    private List<String> locations;
}
