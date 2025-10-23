package com.example.demo.controller;

import com.example.demo.dto.midpoint.MidpointResponse;
import com.example.demo.dto.midpoint.SmartMidpointRequest; // 요청 DTO
import com.example.demo.entity.entityInterface.AppUser; // AppUser 인터페이스 또는 클래스
import com.example.demo.service.SmartMidpointService; // 스마트 서비스
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal; // @AuthenticationPrincipal
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono; // Mono 사용

/**
 * 중간지점 계산 및 추천 요청을 처리하는 REST 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/midpoint")
@RequiredArgsConstructor
public class MidpointController {

    // SmartMidpointService만 주입받습니다.
    private final SmartMidpointService smartMidpointService;
    // MidpointService는 SmartMidpointService 내부에서 사용되므로 컨트롤러에서는 제거합니다.
    // private final MidpointService midpointService;

    /**
     * AI 추천을 포함한 스마트 중간지점 찾기 (대화형)
     * @param appUser 인증된 사용자 정보 (AppUser 객체)
     * @param request 사용자 메시지가 담긴 요청 DTO
     * @return AI 추천 내용이 포함된 응답 (Mono 비동기)
     */
    @PostMapping("/smart")
    public Mono<ResponseEntity<MidpointResponse>> smartMidpoint(
            @AuthenticationPrincipal AppUser appUser,
            @RequestBody SmartMidpointRequest request) {

        // 사용자 ID 가져오기 (Null 체크 포함)
        Long userId = getUserIdFromAppUser(appUser);
        if (userId == null) {
            log.warn("Cannot get user ID from authenticated principal.");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(MidpointResponse.builder().success(false).message("인증 정보가 유효하지 않습니다.").build()));
        }

        String userMessage = request.getMessage(); // DTO에서 메시지 가져오기
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(MidpointResponse.builder().success(false).message("메시지를 입력해주세요.").build()));
        }

        log.info("Smart midpoint request from user {}: '{}'", userId, userMessage.trim());

        // SmartMidpointService의 메인 로직 호출 (비동기 응답 처리)
        return smartMidpointService.processMidpointRequest(userId, userMessage.trim())
                .map(ResponseEntity::ok) // 성공 시 200 OK 와 MidpointResponse body
                .onErrorResume(e -> { // 서비스 로직 내에서 발생한 예외 처리
                    log.error("Error processing smart midpoint request for user {}: {}", userId, e.getMessage(), e);
                    // 사용자에게는 일반적인 오류 메시지 전달
                    MidpointResponse errorResponse = MidpointResponse.builder()
                            .success(false)
                            .message("요청 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.")
                            .build();
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
                });
    }

    /**
     * 사용자의 중간지점 찾기 세션을 초기화합니다.
     * @param appUser 인증된 사용자 정보
     * @return 성공 메시지가 담긴 응답 (동기)
     */
    @PostMapping("/reset")
    public ResponseEntity<MidpointResponse> resetSession(
            @AuthenticationPrincipal AppUser appUser) { // @AuthenticationPrincipal 사용

        Long userId = getUserIdFromAppUser(appUser);
        if (userId == null) {
            log.warn("Cannot get user ID from authenticated principal for session reset.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(MidpointResponse.builder().success(false).message("인증 정보가 유효하지 않습니다.").build());
        }

        log.info("Resetting midpoint session for user {}", userId);

        try {
            // SmartMidpointService의 리셋 메소드 호출 (동기)
            MidpointResponse response = smartMidpointService.resetAndStartOver(userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error resetting midpoint session for user {}: {}", userId, e.getMessage(), e);
            MidpointResponse errorResponse = MidpointResponse.builder()
                    .success(false)
                    .message("세션 초기화 중 오류가 발생했습니다.")
                    .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * AppUser 객체에서 사용자 ID를 안전하게 추출하는 헬퍼 메소드
     * (주의: AppUser 인터페이스/클래스에 getId() 메소드가 있어야 함)
     */
    private Long getUserIdFromAppUser(AppUser appUser) {
        if (appUser != null) {
            try {
                // AppUser 인터페이스 또는 클래스에 정의된 ID getter 메소드 호출
                return appUser.getId(); // 실제 메소드명 확인 필요 (예: getId(), getUserSeq() 등)
            } catch (Exception e) {
                 log.error("Failed to get user ID from AppUser object: {}", appUser, e);
                 return null;
            }
        }
        log.warn("AppUser object provided by @AuthenticationPrincipal is null.");
        return null;
    }

    // --- /find 엔드포인트는 제거됨 ---

}