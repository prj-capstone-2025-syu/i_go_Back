package com.example.demo.dto.midpoint;

import lombok.Data;
import java.util.List;

/**
 * DTO for Google Geocoding API response
 * Maps the JSON structure returned by Google's Geocoding API
 */
@Data
public class GoogleGeocodingResponse {
    private String status;
    private List<Result> results;

    @Data
    public static class Result {
        private String formatted_address;
        private Geometry geometry;
    }

    @Data
    public static class Geometry {
        private Location location;
    }

    @Data
    public static class Location {
        private double lat;
        private double lng;
    }
}
