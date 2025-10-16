package com.example.demo.service;

import com.example.demo.dto.routine.*;
import com.example.demo.entity.routine.Routine;
import com.example.demo.entity.routine.RoutineItem;
import com.example.demo.entity.user.User;
import com.example.demo.repository.RoutineItemRepository;
import com.example.demo.repository.RoutineRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
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
        // 기존 코드: List<Routine> routines = routineRepository.findAllByUserId(user);
        List<Routine> routines = routineRepository.findAllByUser(user);
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

    // 루틴 생성 (아이템 포함)
    public RoutineResponseDTO createRoutine(Long userId, RoutineRequestDTO requestDTO) {
        User user = getUserById(userId);

        Routine routine = new Routine();
        routine.setName(requestDTO.getName());
        routine.setUser(user);

        if (requestDTO.getItems() != null) {
            for (RoutineItemRequestDTO itemRequestDTO : requestDTO.getItems()) {
                RoutineItem item = new RoutineItem();
                item.setName(itemRequestDTO.getName());
                item.setDurationMinutes(itemRequestDTO.getDurationMinutes());
                item.setFlexible(itemRequestDTO.isFlexibleTime());
                routine.addItem(item);
            }
        }

        routineRepository.save(routine);
        return convertToRoutineResponseDTO(routine);
    }

    // 루틴 수정 (아이템 포함, 기존 아이템은 모두 교체됨)
    public RoutineResponseDTO updateRoutine(Long userId, Long routineId, RoutineRequestDTO requestDTO) {
        Routine routine = getRoutineWithOwnerCheck(userId, routineId);
        routine.setName(requestDTO.getName());

        routine.getItems().clear();

        if (requestDTO.getItems() != null) {
            for (RoutineItemRequestDTO itemRequestDTO : requestDTO.getItems()) {
                RoutineItem item = new RoutineItem();
                item.setName(itemRequestDTO.getName());
                item.setDurationMinutes(itemRequestDTO.getDurationMinutes());
                item.setFlexible(itemRequestDTO.isFlexibleTime());
                routine.addItem(item);
            }
        }

        routineRepository.save(routine);
        return convertToRoutineResponseDTO(routine);
    }

    // 루틴 삭제
    public void deleteRoutine(Long userId, Long routineId) {
        Routine routine = getRoutineWithOwnerCheck(userId, routineId);
        routineRepository.delete(routine);
    }

    // 루틴에 아이템 추가 (개별 아이템 추가 시 사용)
    public RoutineItemDTO addRoutineItem(Long userId, Long routineId, RoutineItemRequestDTO requestDTO) {
        Routine routine = getRoutineWithOwnerCheck(userId, routineId);

        RoutineItem item = new RoutineItem();
        item.setName(requestDTO.getName());
        item.setDurationMinutes(requestDTO.getDurationMinutes());
        item.setFlexible(requestDTO.isFlexibleTime());

        routine.addItem(item);
        routineRepository.save(routine);
        RoutineItem savedItem = routine.getItems().get(routine.getItems().size() - 1);
        return convertToRoutineItemDTO(savedItem);
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

        for (int i = 0; i < routine.getItems().size(); i++) {
            routine.getItems().get(i).setOrderIndex(i);
        }

        routineRepository.save(routine);
    }

    // 아이템 순서 변경
    public List<RoutineItemDTO> reorderItems(Long userId, Long routineId, List<OrderChangeDTO> orderChanges) {
        Routine routine = getRoutineWithOwnerCheck(userId, routineId);

        Map<Long, Integer> newPositions = orderChanges.stream()
                .collect(Collectors.toMap(OrderChangeDTO::getItemId, OrderChangeDTO::getNewPosition));

        for (RoutineItem item : routine.getItems()) {
            if (newPositions.containsKey(item.getId())) {
                item.setOrderIndex(newPositions.get(item.getId()));
            }
        }

        routine.getItems().sort(Comparator.comparingInt(RoutineItem::getOrderIndex));

        routineRepository.save(routine);

        return routine.getItems().stream()
                .map(this::convertToRoutineItemDTO)
                .collect(Collectors.toList());
    }

    // 사용자의 모든 루틴 이름 조회
    @Transactional(readOnly = true)
    public List<String> getAllRoutineNamesByUserId(Long userId) {
        User user = getUserById(userId);
        // 기존 코드: List<Routine> routines = routineRepository.findAllByUserId(user);
        List<Routine> routines = routineRepository.findAllByUser(user); // 수정된 코드
        return routines.stream()
                .map(Routine::getName)
                .collect(Collectors.toList());
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }

    private Routine getRoutineWithOwnerCheck(Long userId, Long routineId) {
        Routine routine = routineRepository.findById(routineId)
                .orElseThrow(() -> new IllegalArgumentException("루틴을 찾을 수 없습니다."));

        if (!routine.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 루틴에 접근 권한이 없습니다.");
        }
        return routine;
    }

    private RoutineResponseDTO convertToRoutineResponseDTO(Routine routine) {
        RoutineResponseDTO responseDTO = new RoutineResponseDTO();
        responseDTO.setId(routine.getId());
        responseDTO.setName(routine.getName());

        List<RoutineItemDTO> itemDTOs = routine.getItems().stream()
                .sorted(Comparator.comparingInt(RoutineItem::getOrderIndex))
                .map(this::convertToRoutineItemDTO)
                .collect(Collectors.toList());

        responseDTO.setItems(itemDTOs);

        int totalDuration = routine.getItems().stream()
                .mapToInt(RoutineItem::getDurationMinutes)
                .sum();
        responseDTO.setTotalDurationMinutes(totalDuration);

        return responseDTO;
    }

    private RoutineItemDTO convertToRoutineItemDTO(RoutineItem item) {
        RoutineItemDTO itemDTO = new RoutineItemDTO();
        itemDTO.setId(item.getId());
        itemDTO.setName(item.getName());
        itemDTO.setDurationMinutes(item.getDurationMinutes());
        itemDTO.setFlexibleTime(item.isFlexible());
        itemDTO.setOrderIndex(item.getOrderIndex());
        return itemDTO;
    }

    @Transactional(readOnly = true)
    public List<RoutineNameDTO> getRoutineNamesWithIds(Long userId) {
        User user = getUserById(userId);
        List<Routine> routines = routineRepository.findAllByUser(user);
        return routines.stream()
                .map(routine -> new RoutineNameDTO(routine.getId(), routine.getName()))
                .collect(Collectors.toList());
    }

    // 특정 루틴에 속한 아이템들의 실제 실행 시간을 계산
    @Transactional(readOnly = true)
    public List<CalculatedRoutineItemTime> calculateRoutineItemTimes(Long routineId, LocalDateTime scheduleStartTime) {
        Routine routine = routineRepository.findById(routineId)
                .orElseThrow(() -> new IllegalArgumentException("루틴을 찾을 수 없습니다. ID: " + routineId));

        List<CalculatedRoutineItemTime> calculatedTimes = new ArrayList<>();

        // RoutineItem을 orderIndex 순으로 정렬
        List<RoutineItem> sortedItems = routine.getItems().stream()
                .sorted(Comparator.comparingInt(RoutineItem::getOrderIndex))
                .toList();

        // 전체 루틴 소요 시간 계산
        int totalRoutineDuration = sortedItems.stream()
                .mapToInt(RoutineItem::getDurationMinutes)
                .sum();

        // 루틴 시작 시간 = 스케줄 시작 시간 - 전체 루틴 소요 시간
        LocalDateTime routineStartTime = scheduleStartTime.minusMinutes(totalRoutineDuration);

        log.info("🔍 [RoutineService] 루틴 시간 계산 시작");
        log.info("   ├─ 루틴 ID: {}, 루틴 이름: '{}'", routineId, routine.getName());
        log.info("   ├─ 스케줄 시작 시간: {}", scheduleStartTime);
        log.info("   ├─ 전체 루틴 소요 시간: {}분", totalRoutineDuration);
        log.info("   └─ 계산된 루틴 시작 시간: {}", routineStartTime);

        LocalDateTime currentItemStartTime = routineStartTime;
        int itemIndex = 0;

        for (RoutineItem item : sortedItems) {
            LocalDateTime itemEndTime = currentItemStartTime.plusMinutes(item.getDurationMinutes());

            log.info("   🔹 아이템 #{}: '{}'", itemIndex + 1, item.getName());
            log.info("      ├─ 시작 시간: {}", currentItemStartTime);
            log.info("      ├─ 종료 시간: {}", itemEndTime);
            log.info("      └─ 소요 시간: {}분", item.getDurationMinutes());

            calculatedTimes.add(new CalculatedRoutineItemTime(
                    item.getId(),
                    item.getName(),
                    currentItemStartTime,
                    itemEndTime,
                    item.getDurationMinutes(),
                    routine.getId()
            ));
            currentItemStartTime = itemEndTime; // 다음 아이템의 시작 시간은 현재 아이템의 종료 시간
            itemIndex++;
        }

        log.info("✅ [RoutineService] 루틴 시간 계산 완료 - 총 {}개 아이템", calculatedTimes.size());

        return calculatedTimes;
    }

    // 현재 시간에 해당하는 루틴 아이템을 찾는 메서드
    @Transactional(readOnly = true)
    public String getCurrentRoutineItemName(Long routineId, LocalDateTime scheduleStartTime, LocalDateTime currentTime) {
        List<CalculatedRoutineItemTime> calculatedTimes = calculateRoutineItemTimes(routineId, scheduleStartTime);

        for (CalculatedRoutineItemTime itemTime : calculatedTimes) {
            if (!currentTime.isBefore(itemTime.getStartTime()) && currentTime.isBefore(itemTime.getEndTime())) {
                return itemTime.getRoutineItemName();
            }
        }

        // 현재 시간이 모든 루틴 아이템 시간을 지났다면, 마지막 아이템 반환
        if (!calculatedTimes.isEmpty()) {
            CalculatedRoutineItemTime lastItem = calculatedTimes.get(calculatedTimes.size() - 1);
            if (!currentTime.isBefore(lastItem.getEndTime())) {
                return lastItem.getRoutineItemName();
            }
        }

        return null; // 해당하는 아이템이 없는 경우
    }

    /**
     * 루틴 시작 시간 계산 (첫 번째 아이템의 시작 시간)
     * @param routineId 루틴 ID
     * @param scheduleStartTime 스케줄 시작 시간 (루틴 종료 시간)
     * @return 루틴 시작 시간 (첫 번째 아이템의 시작 시간)
     */
    @Transactional(readOnly = true)
    public LocalDateTime calculateRoutineStartTime(Long routineId, LocalDateTime scheduleStartTime) {
        if (routineId == null || scheduleStartTime == null) {
            return null;
        }

        try {
            List<CalculatedRoutineItemTime> calculatedTimes = calculateRoutineItemTimes(routineId, scheduleStartTime);

            if (!calculatedTimes.isEmpty()) {
                LocalDateTime routineStartTime = calculatedTimes.get(0).getStartTime();
                log.debug("루틴 시작 시간 계산 완료 - Routine ID: {}, 시작 시간: {}", routineId, routineStartTime);
                return routineStartTime;
            }

            log.warn("루틴 아이템이 없습니다 - Routine ID: {}", routineId);
            return null;
        } catch (Exception e) {
            log.error("루틴 시작 시간 계산 실패 - Routine ID: {}, 오류: {}", routineId, e.getMessage(), e);
            return null;
        }
    }
}
