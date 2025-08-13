package com.example.demo.entity.schedule;

import com.example.demo.entity.user.User;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime endTime;

    // 출발지 정보 추가
    private String startLocation;

    // 출발지 좌표
    private Double startX;
    private Double startY;

    // 기존 location은 도착지를 의미
    private String location;

    // 도착지 좌표
    private Double destinationX;
    private Double destinationY;

    private String memo;

    private String supplies;

    @Enumerated(EnumType.STRING)
    private Category category;

    private Long routineId;

    private String googleCalendarEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User user;

    @Enumerated(EnumType.STRING)
    private ScheduleStatus status;

    public enum ScheduleStatus {
        PENDING, IN_PROGRESS, COMPLETED
    }
}