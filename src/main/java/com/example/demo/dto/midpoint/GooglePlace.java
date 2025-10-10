package com.example.demo.dto.midpoint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * DTO for a single place returned by Google Places API.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GooglePlace {
    private String name;
    private Geometry geometry;
    private double rating;
    private String vicinity; // A feature name of a nearby location (e.g., street name).

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Geometry {
        private Location location;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Location {
        private double lat;
        private double lng;
    }
}
