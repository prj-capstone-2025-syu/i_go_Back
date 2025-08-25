package com.example.demo.service;

import com.example.demo.dto.schedule.ScheduleWeatherResponse;
import com.example.demo.dto.weather.WeatherResponse;
import com.example.demo.entity.schedule.Schedule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleWeatherService {

    private final ScheduleService scheduleService;
    private final WeatherApiService weatherApiService;

    // OpenWeatherMap API는 5일(120시간) 예보까지 제공
    private static final int MAX_FORECAST_DAYS = 5;

    // 캐시된 날씨 정보 저장 (스케줄 ID -> 날씨 정보)
    private final ConcurrentMap<Long, ScheduleWeatherResponse.WeatherInfo> weatherCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, LocalDateTime> weatherCacheTime = new ConcurrentHashMap<>();

    /**
     * 가장 가까운 다가오는 스케줄에 대한 날씨 정보를 가져옵니다.
     * 스케줄이 5일 이내에 있고 좌표 정보가 있을 때만 날씨 정보를 제공합니다.
     * @param userId 사용자 ID
     * @return 날씨 정보가 포함된 스케줄 정보 (Optional)
     */
    public Mono<Optional<ScheduleWeatherResponse>> getNextScheduleWithWeather(Long userId) {
        log.info("다음 스케줄의 날씨 정보 조회 시작 - 사용자 ID: {}", userId);

        try {
            // 다가오는 스케줄 1개 조회
            Optional<Schedule> nextSchedule = scheduleService.getUpcomingSchedules(userId, 1)
                    .stream()
                    .findFirst();

            if (nextSchedule.isEmpty()) {
                log.info("사용자 {}의 다가오는 스케줄이 없습니다.", userId);
                return Mono.just(Optional.empty());
            }

            Schedule schedule = nextSchedule.get();
            log.info("다음 스케줄 발견 - ID: {}, 제목: '{}', 시작시간: {}, 종료시간: {}",
                    schedule.getId(), schedule.getTitle(), schedule.getStartTime(), schedule.getEndTime());

            return processScheduleWeather(schedule);

        } catch (Exception e) {
            log.error("스케줄 날씨 정보 조회 중 오류 발생 - 사용자 ID: {}, 에러: {}", userId, e.getMessage(), e);
            return Mono.just(Optional.empty());
        }
    }

    /**
     * 현재 진행 중인 스케줄의 날씨 정보를 가져옵니다.
     * 스케줄이 현재 시간에 진행 중이고 좌표 정보가 있을 때 날씨 정보를 제공합니다.
     * @param userId 사용자 ID
     * @return 날씨 정보가 포함된 현재 진행 중인 스케줄 정보 (Optional)
     */
    public Mono<Optional<ScheduleWeatherResponse>> getCurrentScheduleWithWeather(Long userId) {
        log.info("현재 진행 중인 스케줄의 날씨 정보 조회 시작 - 사용자 ID: {}", userId);

        try {
            // 현재 진행 중인 스케줄 조회
            Optional<Schedule> currentSchedule = scheduleService.getLatestInProgressSchedule(userId);

            if (currentSchedule.isEmpty()) {
                log.info("사용자 {}의 현재 진행 중인 스케줄이 없습니다.", userId);
                return Mono.just(Optional.empty());
            }

            Schedule schedule = currentSchedule.get();
            log.info("현재 진행 중인 스케줄 발견 - ID: {}, 제목: '{}', 시작시간: {}, 종료시간: {}",
                    schedule.getId(), schedule.getTitle(), schedule.getStartTime(), schedule.getEndTime());

            return processScheduleWeather(schedule);

        } catch (Exception e) {
            log.error("현재 진행 중인 스케줄 날씨 정보 조회 중 오류 발생 - 사용자 ID: {}, 에러: {}", userId, e.getMessage(), e);
            return Mono.just(Optional.empty());
        }
    }

    /**
     * 특정 스케줄에 대한 날씨 정보를 가져옵니다.
     * @param userId 사용자 ID
     * @param scheduleId 스케줄 ID
     * @return 날씨 정보가 포함된 스케줄 정보
     */
    public Mono<ScheduleWeatherResponse> getScheduleWithWeather(Long userId, Long scheduleId) {
        log.info("특정 스케줄의 날씨 정보 조회 - 사용자 ID: {}, 스케줄 ID: {}", userId, scheduleId);

        try {
            Schedule schedule = scheduleService.getScheduleById(userId, scheduleId);

            // 캐시된 날씨 정보가 있고 1시간 이내면 사용
            if (isWeatherCacheValid(scheduleId)) {
                log.info("캐시된 날씨 정보 사용 - 스케줄 ID: {}", scheduleId);
                ScheduleWeatherResponse response = ScheduleWeatherResponse.fromSchedule(schedule);
                response.setWeather(weatherCache.get(scheduleId));
                return Mono.just(response);
            }

            return processScheduleWeather(schedule)
                    .map(optional -> optional.orElse(ScheduleWeatherResponse.fromSchedule(schedule)));

        } catch (Exception e) {
            log.error("특정 스케줄 날씨 정보 조회 중 오류 발생 - 사용자 ID: {}, 스케줄 ID: {}, 에러: {}",
                    userId, scheduleId, e.getMessage(), e);
            return Mono.error(e);
        }
    }

    /**
     * 매 시간마다 진행 중이거나 곧 시작될 스케줄의 날씨 정보를 업데이트합니다.
     */
    @Scheduled(cron = "0 0 * * * ?") // 매 시간 정각에 실행
    public void updateScheduleWeatherInfo() {
        log.info("⏰ 정기 날씨 정보 업데이트 작업 시작");
        LocalDateTime now = LocalDateTime.now();

        try {
            // 모든 사용자의 활성 스케줄 조회 (진행 중이거나 24시간 이내 시작 예정)
            List<Schedule> activeSchedules = scheduleService.getActiveSchedulesForWeatherUpdate(now);

            log.info("날씨 업데이트 대상 스케줄 {}개 발견", activeSchedules.size());

            for (Schedule schedule : activeSchedules) {
                try {
                    updateWeatherForSchedule(schedule);
                } catch (Exception e) {
                    log.error("스케줄 ID {}의 날씨 정보 업데이트 실패: {}", schedule.getId(), e.getMessage());
                }
            }

            // 오래된 캐시 정리 (24시간 이상 된 것)
            cleanupOldCache();

        } catch (Exception e) {
            log.error("정기 날씨 정보 업데이트 작업 실패: {}", e.getMessage(), e);
        }

        log.info("✅ 정기 날씨 정보 업데이트 작업 완료");
    }

    /**
     * 특정 스케줄의 날씨 정보를 업데이트합니다.
     */
    private void updateWeatherForSchedule(Schedule schedule) {
        if (schedule.getDestinationY() == null || schedule.getDestinationX() == null) {
            log.debug("스케줄 ID {}는 좌표 정보가 없어 날씨 업데이트를 건너뜁니다.", schedule.getId());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        long daysUntilSchedule = ChronoUnit.DAYS.between(now, schedule.getStartTime());

        if (daysUntilSchedule > MAX_FORECAST_DAYS) {
            log.debug("스케줄 ID {}는 {}일 후로 예보 범위를 벗어나 날씨 업데이트를 건너뜁니다.",
                    schedule.getId(), daysUntilSchedule);
            return;
        }

        try {
            if (daysUntilSchedule <= 1) {
                // 현재 날씨 조회
                weatherApiService.getCurrentWeather(schedule.getDestinationY(), schedule.getDestinationX())
                    .subscribe(weatherResponse -> {
                        ScheduleWeatherResponse.WeatherInfo weatherInfo = createWeatherInfo(weatherResponse);
                        weatherCache.put(schedule.getId(), weatherInfo);
                        weatherCacheTime.put(schedule.getId(), LocalDateTime.now());
                        log.info("현재 날씨 정보 업데이트 완료 - 스케줄 ID: {}, 온도: {}°C",
                                schedule.getId(), weatherInfo.getTemperature());
                    });
            } else {
                // 예보 조회
                weatherApiService.getForecast(schedule.getDestinationY(), schedule.getDestinationX())
                    .subscribe(forecastResponse -> {
                        var closestForecast = findClosestForecast(forecastResponse, schedule.getStartTime());
                        if (closestForecast.isPresent()) {
                            ScheduleWeatherResponse.WeatherInfo weatherInfo = createWeatherInfoFromForecast(closestForecast.get());
                            weatherCache.put(schedule.getId(), weatherInfo);
                            weatherCacheTime.put(schedule.getId(), LocalDateTime.now());
                            log.info("예보 날씨 정보 업데이트 완료 - 스케줄 ID: {}, 온도: {}°C",
                                    schedule.getId(), weatherInfo.getTemperature());
                        }
                    });
            }
        } catch (Exception e) {
            log.error("스케줄 ID {}의 날씨 정보 업데이트 중 오류: {}", schedule.getId(), e.getMessage());
        }
    }

    /**
     * 스케줄에 대한 날씨 정보를 처리합니다.
     * 시간 기반으로 날씨 API를 선택합니다.
     */
    private Mono<Optional<ScheduleWeatherResponse>> processScheduleWeather(Schedule schedule) {
        LocalDateTime now = LocalDateTime.now();
        long daysUntilSchedule = ChronoUnit.DAYS.between(now, schedule.getStartTime());

        ScheduleWeatherResponse response = ScheduleWeatherResponse.fromSchedule(schedule);

        // 좌표가 없는 경우
        if (schedule.getDestinationY() == null || schedule.getDestinationX() == null) {
            log.warn("스케줄 ID {}에 좌표 정보가 없어 날씨 정보를 가져올 수 없습니다.", schedule.getId());
            return Mono.just(Optional.of(response));
        }

        // 5일 이후 스케줄인 경우 날씨 정보 없이 반환
        if (daysUntilSchedule > MAX_FORECAST_DAYS) {
            log.info("스케줄 ID {}이(가) {}일 후로 예보 범위({}일)를 벗어났습니다. 날씨 정보 없이 반환합니다.",
                    schedule.getId(), daysUntilSchedule, MAX_FORECAST_DAYS);
            return Mono.just(Optional.of(response));
        }

        // 진행 중 여부 판단 (시간 기반)
        boolean isInProgress = now.isAfter(schedule.getStartTime()) && now.isBefore(schedule.getEndTime());

        // 과거 스케줄이지만 종료 시간이 현재 시간보다 이후인 경우(진행 중)
        if (schedule.getStartTime().isBefore(now) && !isInProgress) {
            log.info("스케줄 ID {}이(가) 과거 완료된 일정입니다. 날씨 정보 없이 반환합니다.", schedule.getId());
            return Mono.just(Optional.of(response));
        }

        log.info("스케줄 ID {}이(가) {}일 후 예정 또는 진행 중으로 날씨 정보를 조회합니다. 진행 중: {}",
                schedule.getId(), daysUntilSchedule, isInProgress);

        // 캐시된 날씨 정보가 있고 유효하면 사용
        if (isWeatherCacheValid(schedule.getId())) {
            response.setWeather(weatherCache.get(schedule.getId()));
            return Mono.just(Optional.of(response));
        }

        // 시간 기반 날씨 API 선택
        // 당일 또는 내일 스케줄인 경우 OR 진행 중인 경우 = 현재 날씨 조회
        if (daysUntilSchedule <= 1 || isInProgress) {
            return fetchCurrentWeather(schedule, response);
        } else {
            // 2~5일 후 스케줄인 경우 예보 조회
            return fetchForecastWeather(schedule, response);
        }
    }

    /**
     * 현재 날씨 정보 조회
     */
    private Mono<Optional<ScheduleWeatherResponse>> fetchCurrentWeather(Schedule schedule, ScheduleWeatherResponse response) {
        return weatherApiService.getCurrentWeather(schedule.getDestinationY(), schedule.getDestinationX())
                .map(weatherResponse -> {
                    ScheduleWeatherResponse.WeatherInfo weatherInfo = createWeatherInfo(weatherResponse);
                    response.setWeather(weatherInfo);

                    // 캐시에 저장
                    weatherCache.put(schedule.getId(), weatherInfo);
                    weatherCacheTime.put(schedule.getId(), LocalDateTime.now());

                    log.info("현재 날씨 정보 조회 성공 - 스케줄 ID: {}, 온도: {}°C, 날씨: {}",
                            schedule.getId(), weatherResponse.getMain().getTemp(),
                            weatherResponse.getWeather().get(0).getDescription());
                    return Optional.of(response);
                })
                .doOnError(error -> log.error("현재 날씨 정보 조회 실패 - 스케줄 ID: {}, 에러: {}",
                        schedule.getId(), error.getMessage()))
                .onErrorReturn(Optional.of(response));
    }

    /**
     * 예보 날씨 정보 조회 (2~5일 후)
     *
     * TODO: 추후 개선 사항 - 3시간 간격 예보 리스트 제공 기능
     * 현재는 스케줄 시간과 가장 가까운 단일 예보만 제공하지만,
     * 스케줄 며칠 전부터는 하루 종일의 3시간 간격 예보를 제공하는 것도 유용할 수 있음
     *
     * 예시 구현 아이디어:
     * - 3일 이상 전: 해당 날짜의 전체 3시간 간격 예보 리스트 (8개: 00시, 03시, 06시, ..., 21시)
     * - 2일 전: 스케줄 시간 기준 ±6시간 예보 리스트 (3~5개)
     * - 1일 전: 스케줄 시간과 가장 가까운 예보 1개
     * - 진행 중: 실시간 현재 날씨
     *
     * 장점: 사용자가 하루 종일의 날씨 변화를 미리 파악 가능
     * 단점: 데이터량 증가, UI 복잡성 증가
     */
    private Mono<Optional<ScheduleWeatherResponse>> fetchForecastWeather(Schedule schedule, ScheduleWeatherResponse response) {
        return weatherApiService.getForecast(schedule.getDestinationY(), schedule.getDestinationX())
                .map(forecastResponse -> {
                    // 현재: 스케줄 시간과 가장 가까운 단일 예보만 선택
                    var closestForecast = findClosestForecast(forecastResponse, schedule.getStartTime());

                    /* TODO: 3시간 간격 예보 리스트 기능 구현 시 아래 주석 해제 및 수정
                    long daysUntilSchedule = ChronoUnit.DAYS.between(LocalDateTime.now(), schedule.getStartTime());

                    if (daysUntilSchedule >= 3) {
                        // 3일 이상 전: 해당 날짜의 전체 예보 리스트
                        var dailyForecasts = getDailyForecastList(forecastResponse, schedule.getStartTime());
                        response.setWeatherList(dailyForecasts); // 새로운 필드 필요
                    } else if (daysUntilSchedule >= 2) {
                        // 2일 전: 스케줄 시간 기준 ±6시간 예보
                        var nearbyForecasts = getNearbyForecastList(forecastResponse, schedule.getStartTime(), 6);
                        response.setWeatherList(nearbyForecasts);
                    } else {
                        // 1일 전: 기존 로직 (단일 예보)
                        response.setWeather(weatherInfo);
                    }
                    */

                    if (closestForecast.isPresent()) {
                        ScheduleWeatherResponse.WeatherInfo weatherInfo = createWeatherInfoFromForecast(closestForecast.get());
                        response.setWeather(weatherInfo);

                        // 캐시에 저장
                        weatherCache.put(schedule.getId(), weatherInfo);
                        weatherCacheTime.put(schedule.getId(), LocalDateTime.now());

                        log.info("예보 날씨 정보 조회 성공 - 스케줄 ID: {}, 온도: {}°C",
                                schedule.getId(), closestForecast.get().getMain().getTemp());
                    } else {
                        log.warn("스케줄 ID {}에 대한 적절한 예보 데이터를 찾을 수 없습니다.", schedule.getId());
                    }
                    return Optional.of(response);
                })
                .doOnError(error -> log.error("예보 날씨 정보 조회 실패 - 스케줄 ID: {}, 에러: {}",
                        schedule.getId(), error.getMessage()))
                .onErrorReturn(Optional.of(response));
    }

    /**
     * 날씨 캐시가 유효한지 확인 (1시간 이내)
     */
    private boolean isWeatherCacheValid(Long scheduleId) {
        if (!weatherCache.containsKey(scheduleId) || !weatherCacheTime.containsKey(scheduleId)) {
            return false;
        }

        LocalDateTime cacheTime = weatherCacheTime.get(scheduleId);
        return ChronoUnit.HOURS.between(cacheTime, LocalDateTime.now()) < 1;
    }

    /**
     * 오래된 캐시 정리
     */
    private void cleanupOldCache() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        weatherCacheTime.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        weatherCache.entrySet().removeIf(entry -> !weatherCacheTime.containsKey(entry.getKey()));
        log.info("날씨 캐시 정리 완료");
    }

    /**
     * 스케줄 시간과 가장 가까운 예보 데이터 찾기
     */
    private Optional<com.example.demo.dto.weather.WeatherForecastResponse.ForecastItem> findClosestForecast(
            com.example.demo.dto.weather.WeatherForecastResponse forecastResponse, LocalDateTime scheduleTime) {

        if (forecastResponse.getList() == null || forecastResponse.getList().isEmpty()) {
            return Optional.empty();
        }

        return forecastResponse.getList().stream()
                .min((f1, f2) -> {
                    LocalDateTime f1Time = LocalDateTime.parse(f1.getDt_txt().replace(" ", "T"));
                    LocalDateTime f2Time = LocalDateTime.parse(f2.getDt_txt().replace(" ", "T"));

                    long diff1 = Math.abs(ChronoUnit.HOURS.between(scheduleTime, f1Time));
                    long diff2 = Math.abs(ChronoUnit.HOURS.between(scheduleTime, f2Time));

                    return Long.compare(diff1, diff2);
                });
    }

    /**
     * WeatherResponse를 WeatherInfo DTO로 변환
     */
    private ScheduleWeatherResponse.WeatherInfo createWeatherInfo(WeatherResponse weatherResponse) {
        return ScheduleWeatherResponse.WeatherInfo.builder()
                .temperature(weatherResponse.getMain().getTemp())
                .feelsLike(weatherResponse.getMain().getFeels_like())
                .humidity(weatherResponse.getMain().getHumidity())
                .description(weatherResponse.getWeather().get(0).getDescription())
                .weatherType(weatherApiService.determineWeatherType(weatherResponse))
                .icon(weatherResponse.getWeather().get(0).getIcon())
                .build();
    }

    /**
     * ForecastItem을 WeatherInfo DTO로 변환
     */
    private ScheduleWeatherResponse.WeatherInfo createWeatherInfoFromForecast(
            com.example.demo.dto.weather.WeatherForecastResponse.ForecastItem forecast) {
        return ScheduleWeatherResponse.WeatherInfo.builder()
                .temperature(forecast.getMain().getTemp())
                .feelsLike(forecast.getMain().getFeels_like())
                .humidity(forecast.getMain().getHumidity())
                .description(forecast.getWeather().get(0).getDescription())
                .weatherType(weatherApiService.determineWeatherTypeFromForecast(forecast))
                .icon(forecast.getWeather().get(0).getIcon())
                .build();
    }
}
