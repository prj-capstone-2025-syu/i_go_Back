package com.example.demo.controller;

import com.example.demo.dto.schedule.ScheduleWeatherResponse;
import com.example.demo.entity.entityInterface.AppUser;
import com.example.demo.service.ScheduleWeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final ScheduleWeatherService scheduleWeatherService;

    /**
     * 가장 관련성 높은 스케줄의 날씨 정보 조회
     * - 진행 중인 스케줄이 있으면 해당 스케줄의 실시간 날씨
     * - 없으면 다가오는 스케줄의 예보/현재 날씨 (시간에 따라 자동 선택)
     * - 당일/내일: 현재 날씨 API 사용
     * - 2~5일 후: 예보 API 사용
     */
    @GetMapping("/schedule")
    public Mono<ResponseEntity<?>> getRelevantScheduleWeather(@AuthenticationPrincipal AppUser appUser) {
        log.info("🌤️ [WeatherController] 관련 스케줄 날씨 정보 요청 - 사용자 ID: {}", appUser.getId());

        // 먼저 현재 진행 중인 스케줄이 있는지 확인
        return scheduleWeatherService.getCurrentScheduleWithWeather(appUser.getId())
                .flatMap(currentScheduleOpt -> {
                    if (currentScheduleOpt.isPresent()) {
                        log.info("✅ [WeatherController] 현재 진행 중인 스케줄 날씨 정보 반환 - 사용자 ID: {}, 스케줄 ID: {}",
                                appUser.getId(), currentScheduleOpt.get().getScheduleId());
                        return Mono.just(ResponseEntity.ok(currentScheduleOpt.get()));
                    } else {
                        // 진행 중인 스케줄이 없으면 다가오는 스케줄 조회
                        log.info("📋 [WeatherController] 진행 중인 스케줄 없음, 다가오는 스케줄 조회 - 사용자 ID: {}", appUser.getId());
                        return scheduleWeatherService.getNextScheduleWithWeather(appUser.getId())
                                .map(nextScheduleOpt -> {
                                    if (nextScheduleOpt.isPresent()) {
                                        log.info("✅ [WeatherController] 다가오는 스케줄 날씨 정보 반환 - 사용자 ID: {}, 스케줄 ID: {}",
                                                appUser.getId(), nextScheduleOpt.get().getScheduleId());
                                        return ResponseEntity.ok(nextScheduleOpt.get());
                                    } else {
                                        log.info("📭 [WeatherController] 관련 스케줄이 없음 - 사용자 ID: {}", appUser.getId());
                                        return ResponseEntity.ok(Map.of("message", "현재 진행 중이거나 다가오는 스케줄이 없습니다."));
                                    }
                                });
                    }
                })
                .onErrorResume(error -> {
                    log.error("❌ [WeatherController] 스케줄 날씨 정보 조회 실패 - 사용자 ID: {}, 오류: {}",
                            appUser.getId(), error.getMessage());
                    return Mono.just(ResponseEntity.status(500)
                            .body(Map.of("error", "날씨 정보를 가져오는데 실패했습니다.")));
                });
    }

    /**
     * 특정 스케줄의 날씨 정보 조회
     * 프론트엔드에서 특정 일정의 상세 날씨 정보
     */
    @GetMapping("/schedule/{scheduleId}")
    public Mono<ResponseEntity<ScheduleWeatherResponse>> getScheduleWeather(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable Long scheduleId) {

        log.info("🌤️ [WeatherController] 특정 스케줄 날씨 정보 요청 - 사용자 ID: {}, 스케줄 ID: {}",
                appUser.getId(), scheduleId);

        return scheduleWeatherService.getScheduleWithWeather(appUser.getId(), scheduleId)
                .map(response -> {
                    log.info("✅ [WeatherController] 특정 스케줄 날씨 정보 반환 성공 - 사용자 ID: {}, 스케줄 ID: {}",
                            appUser.getId(), scheduleId);
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(error -> {
                    log.error("❌ [WeatherController] 특정 스케줄 날씨 정보 조회 실패 - 사용자 ID: {}, 스케줄 ID: {}, 오류: {}",
                            appUser.getId(), scheduleId, error.getMessage());

                    // 오류 시 빈 ScheduleWeatherResponse 반환
                    ScheduleWeatherResponse errorResponse = ScheduleWeatherResponse.builder()
                            .scheduleId(scheduleId)
                            .title("오류 발생")
                            .startTime(null)
                            .endTime(null)
                            .location("알 수 없음")
                            .weather(null)
                            .build();

                    return Mono.just(ResponseEntity.status(500).body(errorResponse));
                });
    }

    /**
     * 날씨 캐시 강제 새로고침 (디버깅용)
     * 개발/테스트 환경에서 날씨 정보를 강제로 업데이트
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshWeatherCache(@AuthenticationPrincipal AppUser appUser) {
        log.info("🔄 [WeatherController] 날씨 캐시 강제 새로고침 요청 - 사용자 ID: {}", appUser.getId());

        try {
            // 스케줄러 메서드를 수동으로 호출하여 캐시 새로고침
            scheduleWeatherService.updateScheduleWeatherInfo();
            log.info("✅ [WeatherController] 날씨 캐시 새로고침 완료 - 사용자 ID: {}", appUser.getId());
            return ResponseEntity.ok(Map.of("message", "날씨 캐시가 새로고침되었습니다."));
        } catch (Exception e) {
            log.error("❌ [WeatherController] 날씨 캐시 새로고침 실패 - 사용자 ID: {}, 오류: {}",
                    appUser.getId(), e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("error", "날씨 캐시 새로고침에 실패했습니다."));
        }
    }
}
