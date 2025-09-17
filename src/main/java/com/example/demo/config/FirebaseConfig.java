package com.example.demo.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;

@Component
@Profile("!test")  // 테스트 프로파일이 아닐 때만 활성화
public class FirebaseConfig {

    @Value("${firebase.key.path}")
    private String firebaseKeyPath;

    @PostConstruct
    public void initialize() {
        try {
            // Firebase 키 파일 경로가 비어있으면 초기화하지 않음
            if (firebaseKeyPath == null || firebaseKeyPath.trim().isEmpty()) {
                System.out.println("Firebase key path is empty, skipping Firebase initialization");
                return;
            }

            InputStream serviceAccount = new ClassPathResource(firebaseKeyPath).getInputStream();

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Firebase 초기화 실패", e);
        }
    }
}