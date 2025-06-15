package com.example.demo.controller;

import com.example.demo.dto.transport.TransportTimeRequest;
import com.example.demo.dto.transport.TransportTimeResponse;
import com.example.demo.service.TransportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TransportController {

    private final TransportService transportService;

    @PostMapping("/transport-time")
    public ResponseEntity<TransportTimeResponse> calculateTransportTimes(@RequestBody TransportTimeRequest request) {
        log.info("이동시간 계산 요청: 출발({}, {}) → 도착({}, {})",
                 request.getStartX(), request.getStartY(), request.getEndX(), request.getEndY());

        TransportTimeResponse response = transportService.calculateAllTransportTimes(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transit-time")
    public ResponseEntity<Integer> calculateTransitTime(@RequestBody TransportTimeRequest request) {
        log.info("대중교통 이동시간 계산 요청: 출발({}, {}) → 도착({}, {})",
                 request.getStartX(), request.getStartY(), request.getEndX(), request.getEndY());

        Integer transitTime = transportService.calculateTransitTimeOnly(request);

        if (transitTime == null) {
            log.warn("대중교통 이동시간 계산 불가. API 제한 또는 경로 없음.");
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(transitTime);
    }

    @GetMapping("/transit-api-status")
    public ResponseEntity<Map<String, Object>> getTransitApiStatus() {
        return ResponseEntity.ok(transportService.getTransitApiStatus());
    }
}
