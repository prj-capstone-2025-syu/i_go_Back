package com.example.demo.dto.schedule;

import com.example.demo.dto.routine.CalculatedRoutineItemTime;
import com.example.demo.entity.schedule.Schedule;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleWithRoutineResponse {
    private Schedule schedule;
    private LocalDateTime routineStartTime;  // 루틴 시작 시간
    private LocalDateTime routineEndTime;    // 루틴 종료 시간 (마지막 아이템 종료)
    private List<CalculatedRoutineItemTime> routineItems;  // 각 루틴 아이템의 시간 정보
}

