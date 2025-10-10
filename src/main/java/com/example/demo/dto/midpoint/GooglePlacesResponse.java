package com.example.demo.dto.midpoint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

/**
 * DTO for Google Places API (Nearby Search) response.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GooglePlacesResponse {
    private List<GooglePlace> results;
    private String status;
}
