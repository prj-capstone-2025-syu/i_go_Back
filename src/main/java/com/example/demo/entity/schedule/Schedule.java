package com.example.demo.entity.schedule;

import com.example.demo.entity.user.User;
import com.example.demo.entity.routine.Routine;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "schedules")
public class Schedule {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String location;

    private String memo;

    private String supplies;

    @Enumerated(EnumType.STRING)
    private Category category;

    private Long routineId;

    private String googleCalendarEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private ScheduleStatus status;

    public enum ScheduleStatus {
        PENDING, IN_PROGRESS, COMPLETED
    }
}