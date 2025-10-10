package com.example.demo.controller;

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

/**
 * REST Controller for handling midpoint calculation requests
 */
@Slf4j
@RestController
@RequestMapping("/api/midpoint")
@RequiredArgsConstructor
public class MidpointController {

    private final MidpointService midpointService;
    private final SmartMidpointService smartMidpointService;

    /**
     * Calculates the geographical midpoint of multiple locations
     *
     * @param appUser Authenticated user information
     * @param request Request containing list of locations
     * @return ResponseEntity with MidpointResponse containing midpoint data
     */
    @PostMapping("/find")
    public ResponseEntity<MidpointResponse> findMidpoint(
            @AuthenticationPrincipal AppUser appUser,
            @RequestBody MidpointRequest request) {

        log.info("Midpoint calculation request from user {}: {}",
                appUser.getId(), request.getLocations());

        MidpointResponse response = midpointService.findMidpoint(request.getLocations());

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Smart midpoint calculation with AI recommendations
     * Implements MVP flow: ask for number of people first, then collect locations
     *
     * @param appUser Authenticated user information
     * @param request Request containing user message for conversational flow
     * @return ResponseEntity with MidpointResponse containing AI-powered recommendations
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

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }

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
     * Reset user's midpoint session (for testing or if user wants to start over)
     *
     * @param appUser Authenticated user information
     * @return ResponseEntity with success message
     */
    @PostMapping("/reset")
    public ResponseEntity<MidpointResponse> resetSession(
            @AuthenticationPrincipal AppUser appUser) {

        log.info("Resetting midpoint session for user {}", appUser.getId());

        try {
            // SmartMidpointService에서 사용자 세션을 초기화하고 새로운 플로우 시작
            MidpointResponse response = smartMidpointService.processMidpointRequest(
                    appUser.getId(),
                    ""  // 빈 메시지로 초기 상태로 리셋
            );

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
