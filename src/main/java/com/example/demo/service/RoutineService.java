package com.example.demo.service;

import com.example.demo.dto.routine.*;
import com.example.demo.entity.routine.Routine;
import com.example.demo.entity.routine.RoutineItem;
import com.example.demo.entity.user.User;
import com.example.demo.repository.RoutineItemRepository;
import com.example.demo.repository.RoutineRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RoutineService {
    private final RoutineRepository routineRepository;
    private final RoutineItemRepository routineItemRepository;
    private final UserRepository userRepository;

    // 모든 루틴 조회
    @Transactional(readOnly = true)
    public List<RoutineResponseDTO> getAllRoutinesByUserId(Long userId) {
        User user = getUserById(userId);
        List<Routine> routines = routineRepository.findAllByUserId(user);
        return routines.stream()
                .map(this::convertToRoutineResponseDTO)
                .collect(Collectors.toList());
    }

    // 특정 루틴 조회
    @Transactional(readOnly = true)
    public RoutineResponseDTO getRoutineById(Long userId, Long routineId) {
        Routine routine = getRoutineWithOwnerCheck(userId, routineId);
        return convertToRoutineResponseDTO(routine);
    }

    // 루틴 생성
    public RoutineResponseDTO createRoutine(Long userId, RoutineRequestDTO requestDTO) {
        User user = getUserById(userId);

        Routine routine = new Routine();
        routine.setName(requestDTO.getName());
        routine.setUser(user);

        routineRepository.save(routine);
        return convertToRoutineResponseDTO(routine);
    }

    // 루틴 수정
    public RoutineResponseDTO updateRoutine(Long userId, Long routineId, RoutineRequestDTO requestDTO) {
        Routine routine = getRoutineWithOwnerCheck(userId, routineId);
        routine.setName(requestDTO.getName());
        routineRepository.save(routine);
        return convertToRoutineResponseDTO(routine);
    }

    // 루틴 삭제
    public void deleteRoutine(Long userId, Long routineId) {
        Routine routine = getRoutineWithOwnerCheck(userId, routineId);
        routineRepository.delete(routine);
    }

    // 루틴에 아이템 추가
    public RoutineItemDTO addRoutineItem(Long userId, Long routineId, RoutineItemRequestDTO requestDTO) {
        Routine routine = getRoutineWithOwnerCheck(userId, routineId);

        RoutineItem item = new RoutineItem();
        item.setName(requestDTO.getName());
        item.setDurationMinutes(requestDTO.getDurationMinutes());
        item.setFlexible(requestDTO.isFlexibleTime());

        routine.addItem(item); // 연관관계 설정 및 순서 자동 지정
        routineRepository.save(routine);

        return convertToRoutineItemDTO(item);
    }

    // 루틴 아이템 수정
    public RoutineItemDTO updateRoutineItem(Long userId, Long routineId, Long itemId, RoutineItemRequestDTO requestDTO) {
        Routine routine = getRoutineWithOwnerCheck(userId, routineId);

        RoutineItem item = routine.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("해당 아이템을 찾을 수 없습니다."));

        item.setName(requestDTO.getName());
        item.setDurationMinutes(requestDTO.getDurationMinutes());
        item.setFlexible(requestDTO.isFlexibleTime());

        routineItemRepository.save(item);
        return convertToRoutineItemDTO(item);
    }

    // 루틴 아이템 삭제
    public void deleteRoutineItem(Long userId, Long routineId, Long itemId) {
        Routine routine = getRoutineWithOwnerCheck(userId, routineId);

        RoutineItem itemToRemove = routine.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("해당 아이템을 찾을 수 없습니다."));

        routine.getItems().remove(itemToRemove);

        // 삭제 후 남은 아이템들의 순서 재조정
        for (int i = 0; i < routine.getItems().size(); i++) {
            routine.getItems().get(i).setOrderIndex(i);
        }

        routineRepository.save(routine);
    }

    // 아이템 순서 변경
    public List<RoutineItemDTO> reorderItems(Long userId, Long routineId, List<OrderChangeDTO> orderChanges) {
        Routine routine = getRoutineWithOwnerCheck(userId, routineId);

        // 변경 요청된 아이템들에 대해 순서 업데이트
        Map<Long, Integer> newPositions = orderChanges.stream()
                .collect(Collectors.toMap(OrderChangeDTO::getItemId, OrderChangeDTO::getNewPosition));

        for (RoutineItem item : routine.getItems()) {
            if (newPositions.containsKey(item.getId())) {
                item.setOrderIndex(newPositions.get(item.getId()));
            }
        }

        // 순서대로 정렬
        routine.getItems().sort(Comparator.comparingInt(RoutineItem::getOrderIndex));

        routineRepository.save(routine);

        return routine.getItems().stream()
                .map(this::convertToRoutineItemDTO)
                .collect(Collectors.toList());
    }

    // 헬퍼 메소드: 사용자 조회
    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }

    // 헬퍼 메소드: 루틴 조회 및 소유자 확인
    private Routine getRoutineWithOwnerCheck(Long userId, Long routineId) {
        Routine routine = routineRepository.findById(routineId)
                .orElseThrow(() -> new IllegalArgumentException("루틴을 찾을 수 없습니다."));

        if (!routine.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 루틴에 접근 권한이 없습니다.");
        }

        return routine;
    }

    // 변환 메소드: Routine -> RoutineResponseDTO
    private RoutineResponseDTO convertToRoutineResponseDTO(Routine routine) {
        RoutineResponseDTO responseDTO = new RoutineResponseDTO();
        responseDTO.setId(routine.getId());
        responseDTO.setName(routine.getName());

        List<RoutineItemDTO> itemDTOs = routine.getItems().stream()
                .sorted(Comparator.comparingInt(RoutineItem::getOrderIndex))
                .map(this::convertToRoutineItemDTO)
                .collect(Collectors.toList());

        responseDTO.setItems(itemDTOs);

        //각 루틴 하나의 총 시간을 계산해서 반환
        int totalDuration = routine.getItems().stream()
                .mapToInt(item -> item.getDurationMinutes())
                .sum();
        responseDTO.setTotalDurationMinutes(totalDuration);

        return responseDTO;
    }

    // 변환 메소드: RoutineItem -> RoutineItemDTO
    private RoutineItemDTO convertToRoutineItemDTO(RoutineItem item) {
        RoutineItemDTO itemDTO = new RoutineItemDTO();
        itemDTO.setId(item.getId());
        itemDTO.setName(item.getName());
        itemDTO.setDurationMinutes(item.getDurationMinutes());
        itemDTO.setFlexibleTime(item.isFlexible());
        itemDTO.setOrderIndex(item.getOrderIndex());
        return itemDTO;
    }
}