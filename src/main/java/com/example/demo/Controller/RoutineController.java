package com.example.demo.Controller;

import com.example.demo.dto.routine.*;
import com.example.demo.entity.entityInterface.AppUser;
import java.util.*;

import com.example.demo.service.RoutineService;
import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routines")
@RequiredArgsConstructor
public class RoutineController {
    private final RoutineService routineService;

    // 사용자의 모든 루틴 조회
    @GetMapping
    public List<RoutineResponseDTO> getAllRoutines(@AuthenticationPrincipal AppUser appUser) {
        return routineService.getAllRoutinesByUserId(appUser.getId());
    }

    // 특정 루틴 조회
    @GetMapping("/{routineId}")
    public RoutineResponseDTO getRoutine(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable Long routineId) {
        return routineService.getRoutineById(appUser.getId(), routineId);
    }

    // 루틴 생성
    @PostMapping
    public RoutineResponseDTO createRoutine(
            @AuthenticationPrincipal AppUser appUser,
            @RequestBody RoutineRequestDTO requestDTO) {
        return routineService.createRoutine(appUser.getId(), requestDTO);
    }

    // 루틴 수정
    @PutMapping("/{routineId}")
    public RoutineResponseDTO updateRoutine(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable Long routineId,
            @RequestBody RoutineRequestDTO requestDTO) {
        return routineService.updateRoutine(appUser.getId(), routineId, requestDTO);
    }

    // 루틴 삭제
    @DeleteMapping("/{routineId}")
    public ResponseEntity<?> deleteRoutine(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable Long routineId) {
        routineService.deleteRoutine(appUser.getId(), routineId);
        return ResponseEntity.ok(Map.of("message", "루틴이 삭제되었습니다."));
    }

    // 루틴에 아이템 추가
    @PostMapping("/{routineId}/items")
    public RoutineItemDTO addRoutineItem(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable Long routineId,
            @RequestBody RoutineItemRequestDTO requestDTO) {
        return routineService.addRoutineItem(appUser.getId(), routineId, requestDTO);
    }

    // 루틴 아이템 수정
    @PutMapping("/{routineId}/items/{itemId}")
    public RoutineItemDTO updateRoutineItem(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable Long routineId,
            @PathVariable Long itemId,
            @RequestBody RoutineItemRequestDTO requestDTO) {
        return routineService.updateRoutineItem(appUser.getId(), routineId, itemId, requestDTO);
    }

    // 루틴 아이템 삭제
    @DeleteMapping("/{routineId}/items/{itemId}")
    public ResponseEntity<?> deleteRoutineItem(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable Long routineId,
            @PathVariable Long itemId) {
        routineService.deleteRoutineItem(appUser.getId(), routineId, itemId);
        return ResponseEntity.ok(Map.of("message", "루틴 아이템이 삭제되었습니다."));
    }

    // 아이템 순서 변경
    @PutMapping("/{routineId}/items/reorder")
    public List<RoutineItemDTO> reorderItems(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable Long routineId,
            @RequestBody List<OrderChangeDTO> orderChanges) {
        return routineService.reorderItems(appUser.getId(), routineId, orderChanges);
    }

    @GetMapping("/names")
    public List<RoutineNameDTO> getRoutineNames(@AuthenticationPrincipal AppUser appUser) {
        return routineService.getRoutineNamesWithIds(appUser.getId());
    }
}