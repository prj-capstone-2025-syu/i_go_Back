package com.example.demo.dto.notification;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettingsDto {
    private Boolean notificationsEnabled;
    private Boolean notifyTodaySchedule;
    private Boolean notifyNextSchedule;
    private Boolean notifyRoutineProgress;
    private Boolean notifySupplies;
    private Boolean notifyUnexpectedEvent;
    private Boolean notifyAiFeature;
}