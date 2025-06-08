package com.example.demo.service;

import com.example.demo.entity.schedule.Schedule;
import com.example.demo.repository.UserRepository;
import com.example.demo.entity.user.User;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class GoogleCalendarService {
    private static final Logger logger = LoggerFactory.getLogger(GoogleCalendarService.class);
    private static final String APPLICATION_NAME = "IGO Calendar";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String CALENDAR_ID = "primary";

    private final UserRepository userRepository;

    private Credential getCredentials(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        String accessToken = user.getGoogleAccessToken();
        if (accessToken == null || accessToken.isEmpty()) {
            throw new IllegalArgumentException("Google Access Token이 없습니다.");
        }

        // 토큰 만료 확인
        if (user.getGoogleTokenExpiresAt() != null &&
                user.getGoogleTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Google Access Token이 만료되었습니다.");
        }

        logger.info("사용자 ID {}의 Google Access Token을 성공적으로 로드했습니다.", userId);
        return new GoogleCredential().setAccessToken(accessToken);
    }

    private Calendar getCalendarService(Long userId) throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = getCredentials(userId);
        return new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public String createEvent(Schedule schedule, Long userId) throws IOException, GeneralSecurityException {
        Calendar service = getCalendarService(userId);
        Event event = new Event()
                .setSummary(schedule.getTitle())
                .setLocation(schedule.getLocation())
                .setDescription(schedule.getMemo());

        // 한국 시간대 명시적 지정
        ZoneId koreaZoneId = ZoneId.of("Asia/Seoul");

        // LocalDateTime을 Asia/Seoul 시간대로 해석하여 변환
        DateTime startDateTime = new DateTime(
                schedule.getStartTime().atZone(koreaZoneId).toInstant().toEpochMilli());

        EventDateTime start = new EventDateTime()
                .setDateTime(startDateTime)
                .setTimeZone("Asia/Seoul"); // 명시적으로 KST 지정

        event.setStart(start);

        // 종료 시간도 동일한 방식으로 처리
        if (schedule.getEndTime() != null) {
            DateTime endDateTime = new DateTime(
                    schedule.getEndTime().atZone(koreaZoneId).toInstant().toEpochMilli());
            EventDateTime end = new EventDateTime()
                    .setDateTime(endDateTime)
                    .setTimeZone("Asia/Seoul");
            event.setEnd(end);
        } else {
            // 종료 시간 기본값 설정
            DateTime endDateTime = new DateTime(
                    schedule.getStartTime().plusHours(1).atZone(koreaZoneId).toInstant().toEpochMilli());
            EventDateTime end = new EventDateTime()
                    .setDateTime(endDateTime)
                    .setTimeZone("Asia/Seoul");
            event.setEnd(end);
        }

        // 디버깅 로그 추가
        logger.info("시간 정보 - 원본: {}, Google Calendar 전송: {}, 시간대: Asia/Seoul",
                schedule.getStartTime(),
                startDateTime.toStringRfc3339());

        event = service.events().insert(CALENDAR_ID, event).execute();
        logger.info("Google Calendar 이벤트 생성 성공. Event ID: {}, User ID: {}", event.getId(), userId);
        return event.getId();
    }

    public void updateEvent(Schedule schedule, Long userId) throws IOException, GeneralSecurityException {
        Calendar service = getCalendarService(userId);
        Event event = service.events().get(CALENDAR_ID, schedule.getGoogleCalendarEventId()).execute();

        event.setSummary(schedule.getTitle());
        event.setLocation(schedule.getLocation());
        event.setDescription(schedule.getMemo());

        // 한국 시간대 명시적 지정 (createEvent와 동일하게 적용)
        ZoneId koreaZoneId = ZoneId.of("Asia/Seoul");

        // LocalDateTime을 Asia/Seoul 시간대로 해석하여 변환
        DateTime startDateTime = new DateTime(
                schedule.getStartTime().atZone(koreaZoneId).toInstant().toEpochMilli());
        EventDateTime start = new EventDateTime()
                .setDateTime(startDateTime)
                .setTimeZone("Asia/Seoul"); // 명시적으로 KST 지정
        event.setStart(start);

        if (schedule.getEndTime() != null) {
            DateTime endDateTime = new DateTime(
                    schedule.getEndTime().atZone(koreaZoneId).toInstant().toEpochMilli());
            EventDateTime end = new EventDateTime()
                    .setDateTime(endDateTime)
                    .setTimeZone("Asia/Seoul");
            event.setEnd(end);
        } else {
            DateTime endDateTime = new DateTime(
                    schedule.getStartTime().plusHours(1).atZone(koreaZoneId).toInstant().toEpochMilli());
            EventDateTime end = new EventDateTime()
                    .setDateTime(endDateTime)
                    .setTimeZone("Asia/Seoul");
            event.setEnd(end);
        }

        // 디버깅을 위한 로그 추가
        logger.info("이벤트 업데이트 - 원본 시작: {}, 변환 후(Asia/Seoul): {}",
                schedule.getStartTime(),
                startDateTime.toStringRfc3339());

        service.events().update(CALENDAR_ID, event.getId(), event).execute();
        logger.info("Google Calendar 이벤트 업데이트 성공. Event ID: {}, User ID: {}", event.getId(), userId);
    }

    public void deleteEvent(String eventId, Long userId) throws IOException, GeneralSecurityException {
        Calendar service = getCalendarService(userId);
        service.events().delete(CALENDAR_ID, eventId).execute();
        logger.info("Google Calendar 이벤트 삭제 성공. Event ID: {}, User ID: {}", eventId, userId);
    }
}