package com.example.demo.service;

import com.example.demo.entity.schedule.Schedule;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private static final String APPLICATION_NAME = "루틴 매니저";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String CALENDAR_ID = "primary"; // 기본 캘린더 사용
    
    private Credential getCredentials(String userId) {
        // OAuth 인증 로직 구현 중
        return null;
    }

    // 구글 캘린더 클라이언트 생성
    private Calendar getCalendarService(String userId) throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        return new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(userId))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    // 구글 캘린더에 일정 생성
    public String createEvent(Schedule schedule, String userId) throws IOException, GeneralSecurityException {
        Event event = new Event()
                .setSummary(schedule.getTitle())
                .setLocation(schedule.getLocation())
                .setDescription(schedule.getMemo());

        DateTime startDateTime = new DateTime(
                schedule.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        EventDateTime start = new EventDateTime()
                .setDateTime(startDateTime)
                .setTimeZone(ZoneId.systemDefault().getId());
        event.setStart(start);

        DateTime endDateTime = new DateTime(
                schedule.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        EventDateTime end = new EventDateTime()
                .setDateTime(endDateTime)
                .setTimeZone(ZoneId.systemDefault().getId());
        event.setEnd(end);

        // 이벤트 생성
        event = getCalendarService(userId)
                .events()
                .insert(CALENDAR_ID, event)
                .execute();

        return event.getId();
    }

    // 구글 캘린더 일정 수정
    public void updateEvent(Schedule schedule, String userId) throws IOException, GeneralSecurityException {
        Event event = getCalendarService(userId)
                .events()
                .get(CALENDAR_ID, schedule.getGoogleCalendarEventId())
                .execute();

        event.setSummary(schedule.getTitle())
                .setLocation(schedule.getLocation())
                .setDescription(schedule.getMemo());

        DateTime startDateTime = new DateTime(
                schedule.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        EventDateTime start = new EventDateTime()
                .setDateTime(startDateTime)
                .setTimeZone(ZoneId.systemDefault().getId());
        event.setStart(start);

        DateTime endDateTime = new DateTime(
                schedule.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        EventDateTime end = new EventDateTime()
                .setDateTime(endDateTime)
                .setTimeZone(ZoneId.systemDefault().getId());
        event.setEnd(end);

        getCalendarService(userId)
                .events()
                .update(CALENDAR_ID, event.getId(), event)
                .execute();
    }

    // 구글 캘린더 일정 삭제
    public void deleteEvent(String eventId, String userId) throws IOException, GeneralSecurityException {
        getCalendarService(userId)
                .events()
                .delete(CALENDAR_ID, eventId)
                .execute();
    }
}