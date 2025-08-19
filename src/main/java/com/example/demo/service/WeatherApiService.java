package com.example.demo.service;

import com.example.demo.dto.weather.WeatherDetails;
import com.example.demo.dto.weather.WeatherForecastResponse;
import com.example.demo.dto.weather.WeatherMain;
import com.example.demo.dto.weather.WeatherResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class WeatherApiService {
    private final WebClient webClient;

    @Value("${weather.api.key}")
    private String apiKey;

    // 위도/경도로 현재 날씨 조회
    public Mono<WeatherResponse> getCurrentWeather(double lat, double lon) {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder
                .scheme("https")
                .host("api.openweathermap.org")
                .path("/data/2.5/weather")
                .queryParam("lat", lat)
                .queryParam("lon", lon)
                .queryParam("appid", apiKey)
                .queryParam("units", "metric")
                .queryParam("lang", "kr")
                .build())
            .retrieve()
            .bodyToMono(WeatherResponse.class)
            .doOnSuccess(response -> log.info("Weather API success for lat: {}, lon: {}", lat, lon))
            .doOnError(error -> log.error("Weather API error for lat: {}, lon: {}", lat, lon, error))
            .onErrorResume(throwable -> {
                log.error("Weather API fallback triggered", throwable);
                return Mono.just(createFallbackWeather(lat, lon));
            });
    }

    // 5일 예보 조회
    public Mono<WeatherForecastResponse> getForecast(double lat, double lon) {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder
                .scheme("https")
                .host("api.openweathermap.org")
                .path("/data/2.5/forecast")
                .queryParam("lat", lat)
                .queryParam("lon", lon)
                .queryParam("appid", apiKey)
                .queryParam("units", "metric")
                .queryParam("lang", "kr")
                .build())
            .retrieve()
            .bodyToMono(WeatherForecastResponse.class)
            .doOnSuccess(response -> log.info("Forecast API success for lat: {}, lon: {}", lat, lon))
            .doOnError(error -> log.error("Forecast API error for lat: {}, lon: {}", lat, lon, error));
    }

    // API 장애 시 기본 날씨 데이터
    private WeatherResponse createFallbackWeather(double lat, double lon) {
        return WeatherResponse.builder()
            .main(WeatherMain.builder()
                .temp(20.0)
                .feels_like(20.0)
                .humidity(50)
                .build())
            .weather(List.of(WeatherDetails.builder()
                .main("Clear")
                .description("맑음")
                .build()))
            .name("Unknown")
            .build();
    }

    // 날씨 조건을 문자열로 변환 (현재 날씨용)
    public String determineWeatherType(WeatherResponse response) {
        if (response.getWeather() == null || response.getWeather().isEmpty()) {
            return "알 수 없음";
        }

        String main = response.getWeather().get(0).getMain().toLowerCase();
        int humidity = response.getMain().getHumidity();
        double temp = response.getMain().getTemp();

        return categorizeWeather(main, temp, humidity);
    }

    // 날씨 조건을 문자열로 변환 (예보용)
    public String determineWeatherTypeFromForecast(WeatherForecastResponse.ForecastItem forecast) {
        if (forecast.getWeather() == null || forecast.getWeather().isEmpty()) {
            return "알 수 없음";
        }

        String main = forecast.getWeather().get(0).getMain().toLowerCase();
        int humidity = forecast.getMain().getHumidity();
        double temp = forecast.getMain().getTemp();

        return categorizeWeather(main, temp, humidity);
    }

    // 공통 날씨 분류 로직
    private String categorizeWeather(String main, double temp, int humidity) {
        // 온도 기반 우선 판단
        if (temp <= 5) return "추위";
        if (temp >= 30) return "더위";

        // 날씨 상태 기반 판단
        if (main.contains("rain")) return "비";
        if (main.contains("snow")) return "눈";
        if (main.contains("thunderstorm") || main.contains("thunder")) return "천둥";
        if (main.contains("drizzle")) return "이슬비";
        if (main.contains("mist") || main.contains("fog")) return "안개";
        if (main.contains("haze")) return "아지랑이";
        if (main.contains("dust") || main.contains("sand")) return "먼지";
        if (main.contains("smoke")) return "연기";
        if (main.contains("cloud")) {
            // 구름 정도에 따른 세분화 (필요시 추가 데이터 활용)
            return "흐림";
        }

        // 습도 기반 추가 판단
        if (humidity > 80) return "습함";
        if (humidity < 30) return "건조";

        // 기본값
        return "맑음";
    }
}
