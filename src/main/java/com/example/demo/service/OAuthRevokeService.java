package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class OAuthRevokeService {
    private static final Logger logger = Logger.getLogger(OAuthRevokeService.class.getName());
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Google OAuth 액세스 토큰을 취소합니다.
     *
     * @param accessToken 취소할 Google 액세스 토큰
     * @return 취소 성공 여부
     */
    public boolean revokeGoogleToken(String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) {
            logger.warning("액세스 토큰이 비어있어 취소할 수 없습니다.");
            return false;
        }

        try {
            String revokeEndpoint = "https://accounts.google.com/o/oauth2/revoke";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("token", accessToken);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

            restTemplate.postForEntity(revokeEndpoint, request, String.class);
            logger.info("Google 액세스 토큰 취소 성공");
            return true;
        } catch (RestClientException e) {
            logger.warning("Google 액세스 토큰 취소 실패: " + e.getMessage());
            return false;
        }
    }
}
