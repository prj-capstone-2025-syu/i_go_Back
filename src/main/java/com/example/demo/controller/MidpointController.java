// MidpointController.java (전체 코드)

package com.example.demo.controller;

import com.example.demo.dto.midpoint.Coordinates;
import com.example.demo.dto.midpoint.GooglePlace;
import com.example.demo.dto.midpoint.MidpointRequest;
import com.example.demo.dto.midpoint.MidpointResponse;
import com.example.demo.dto.midpoint.SmartMidpointRequest;
import com.example.demo.entity.entityInterface.AppUser;
import com.example.demo.service.MidpointService;
import com.example.demo.service.SmartMidpointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

/**
 * 중간지점 계산 요청을 처리하는 REST 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/midpoint")
@RequiredArgsConstructor
public class MidpointController {

    private final MidpointService midpointService;
    private final SmartMidpointService smartMidpointService;

    /**
     * 여러 위치의 지리적 중간지점을 계산합니다. (기본 기능)
     *
     * @param appUser 인증된 사용자 정보
     * @param request 위치 목록이 포함된 요청
     * @return 중간지점 데이터가 포함된 응답
     */
    @PostMapping("/find")
    public ResponseEntity<MidpointResponse> findMidpoint(
            @AuthenticationPrincipal AppUser appUser,
            @RequestBody MidpointRequest request) {

        log.info("Midpoint calculation request from user {}: {}",
                appUser.getId(), request.getLocations());

        try {
            // 1. 수학적 중간 좌표 계산
            Coordinates coords = midpointService.calculateGeometricMidpoint(request.getLocations());

            // 2. 주변 장소 검색 (기본 기능이므로 '일반적인 장소'로 검색)
            List<GooglePlace> places = midpointService.getNearbyPlaces(coords, "point_of_interest");

            // 3. 최고 평점 장소 선정
            GooglePlace bestPlace = places.stream()
                .max(Comparator.comparing(GooglePlace::getRating))
                .orElse(places.get(0));
            
            Coordinates finalCoords = new Coordinates(bestPlace.getGeometry().getLocation().getLat(), bestPlace.getGeometry().getLocation().getLng());
            String finalAddress = bestPlace.getName() + " (" + bestPlace.getVicinity() + ")";

            MidpointResponse response = MidpointResponse.builder()
                .midpointCoordinates(finalCoords)
                .midpointAddress(finalAddress)
                .success(true)
                .message("중간지점을 찾았습니다.")
                .build();
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in basic midpoint finding: {}", e.getMessage(), e);
            MidpointResponse errorResponse = MidpointResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build();
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * AI 추천을 포함한 스마트 중간지점 찾기 (대화형)
     *
     * @param appUser 인증된 사용자 정보
     * @param request 대화 흐름에 따른 사용자 메시지가 담긴 요청
     * @return AI 추천 내용이 포함된 응답
     */
    @PostMapping("/smart")
    public ResponseEntity<MidpointResponse> smartMidpoint(
            @AuthenticationPrincipal AppUser appUser,
            @RequestBody SmartMidpointRequest request) {

        log.info("Smart midpoint request from user {}: '{}'",
                appUser.getId(), request.getMessage());

        try {
            MidpointResponse response = smartMidpointService.processMidpointRequest(
                    appUser.getId(),
                    request.getMessage()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing smart midpoint request: {}", e.getMessage(), e);
            MidpointResponse errorResponse = MidpointResponse.builder()
                    .success(false)
                    .message("스마트 중간위치 계산 중 오류가 발생했습니다: " + e.getMessage())
                    .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 사용자의 중간지점 찾기 세션을 초기화합니다.
     *
     * @param appUser 인증된 사용자 정보
     * @return 성공 메시지가 담긴 응답
     */
    @PostMapping("/reset")
    public ResponseEntity<MidpointResponse> resetSession(
            @AuthenticationPrincipal AppUser appUser) {

        log.info("Resetting midpoint session for user {}", appUser.getId());

        try {
            // [수정] reset 전용 메서드 대신, processMidpointRequest의 초기화 로직을 활용
            MidpointResponse response = smartMidpointService.resetAndStartOver(appUser.getId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error resetting midpoint session: {}", e.getMessage(), e);
            MidpointResponse errorResponse = MidpointResponse.builder()
                    .success(false)
                    .message("세션 초기화 중 오류가 발생했습니다: " + e.getMessage())
                    .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}