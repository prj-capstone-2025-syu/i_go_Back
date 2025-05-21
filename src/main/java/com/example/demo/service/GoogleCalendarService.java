package com.example.demo.service;

import com.example.demo.entity.schedule.Schedule;
import com.example.demo.repository.UserRepository; // UserRepository import
import com.example.demo.entity.user.User; // User 엔티티 import
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails; // UserDetails import
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleCalendarService.class);
    private static final String APPLICATION_NAME = "IGO";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String CALENDAR_ID = "primary";

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final UserRepository userRepository; // UserRepository 주입

    private Credential getCredentials(Long userId) {
        // userId로 사용자 정보(이메일) 직접 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    logger.error("ID가 {}인 사용자를 찾을 수 없습니다.", userId);
                    return new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId);
                });

        String userEmail = user.getEmail();
        if (userEmail == null || userEmail.isEmpty()) {
            logger.error("사용자 ID {}의 이메일이 없거나 비어 있습니다.", userId);
            throw new IllegalStateException("사용자 이메일이 없습니다.");
        }

        logger.info("UserRepository에서 userId {}에 대한 이메일 '{}' 조회 성공", userId, userEmail);

        // 이메일로 직접 OAuth2AuthorizedClient 조회
        String clientRegistrationId = "google";
        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                clientRegistrationId, userEmail);

        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            String errorMessage = String.format(
                    "Google API에 접근하기 위한 OAuth2 Authorized Client 또는 Access Token을 찾을 수 없습니다. " +
                            "사용자 이메일: %s, Client ID: %s. " +
                            "사용자가 Google로 로그인했는지, 필요한 scope가 부여되었는지 확인하세요. (User ID: %s)",
                    userEmail, clientRegistrationId, userId
            );
            logger.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }

        logger.info("Google API Access Token 성공적으로 로드됨. 이메일: {}, Client ID: {} (User ID: {})",
                userEmail, clientRegistrationId, userId);

        String accessTokenValue = authorizedClient.getAccessToken().getTokenValue();
        return new GoogleCredential().setAccessToken(accessTokenValue);
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

        DateTime startDateTime = new DateTime(schedule.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        EventDateTime start = new EventDateTime().setDateTime(startDateTime).setTimeZone(ZoneId.systemDefault().getId());
        event.setStart(start);

        // endTime이 null이 아닐 경우에만 설정
        if (schedule.getEndTime() != null) {
            DateTime endDateTime = new DateTime(schedule.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            EventDateTime end = new EventDateTime().setDateTime(endDateTime).setTimeZone(ZoneId.systemDefault().getId());
            event.setEnd(end);
        } else {
            // endTime이 없으면 시작 시간과 동일하게 설정하거나, 구글 캘린더 기본값 사용 (예: 1시간)
            // 여기서는 시작 시간과 동일하게 설정 (종일 일정이 아닌 경우 수정 필요)
            EventDateTime end = new EventDateTime().setDateTime(startDateTime).setTimeZone(ZoneId.systemDefault().getId());
            event.setEnd(end);
            logger.warn("Schedule의 endTime이 null입니다. Google Calendar Event의 endTime을 startTime과 동일하게 설정합니다. (User DB ID: {}, Schedule Title: {})", userId, schedule.getTitle());
        }


        event = service.events().insert(CALENDAR_ID, event).execute();
        logger.info("Google Calendar 이벤트 생성 성공. Event ID: {}, User DB ID: {}", event.getId(), userId);
        return event.getId();
    }

    public void updateEvent(Schedule schedule, Long userId) throws IOException, GeneralSecurityException {
        Calendar service = getCalendarService(userId);
        Event event = service.events().get(CALENDAR_ID, schedule.getGoogleCalendarEventId()).execute();

        event.setSummary(schedule.getTitle())
                .setLocation(schedule.getLocation())
                .setDescription(schedule.getMemo());

        DateTime startDateTime = new DateTime(schedule.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        event.setStart(new EventDateTime().setDateTime(startDateTime).setTimeZone(ZoneId.systemDefault().getId()));

        if (schedule.getEndTime() != null) {
            DateTime endDateTime = new DateTime(schedule.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            event.setEnd(new EventDateTime().setDateTime(endDateTime).setTimeZone(ZoneId.systemDefault().getId()));
        } else {
            event.setEnd(new EventDateTime().setDateTime(startDateTime).setTimeZone(ZoneId.systemDefault().getId()));
            logger.warn("Schedule의 endTime이 null입니다. Google Calendar Event의 endTime을 startTime과 동일하게 설정합니다. (User DB ID: {}, Schedule Title: {})", userId, schedule.getTitle());
        }

        service.events().update(CALENDAR_ID, event.getId(), event).execute();
        logger.info("Google Calendar 이벤트 업데이트 성공. Event ID: {}, User DB ID: {}", event.getId(), userId);
    }

    public void deleteEvent(String eventId, Long userId) throws IOException, GeneralSecurityException {
        Calendar service = getCalendarService(userId);
        service.events().delete(CALENDAR_ID, eventId).execute();
        logger.info("Google Calendar 이벤트 삭제 성공. Event ID: {}, User DB ID: {}", eventId, userId);
    }
}