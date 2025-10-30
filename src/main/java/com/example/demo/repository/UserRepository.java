package com.example.demo.repository;

import com.example.demo.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List; // List import 추가
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByOauthId(String oauthId);
    boolean existsByNickname(String nickname);

    /**
     * FCM 토큰으로 사용자를 조회합니다. (결과가 1개 이하일 것으로 기대될 때 사용)
     * @param fcmToken 검색할 FCM 토큰
     * @return 해당 토큰을 가진 User Optional 객체
     */
    Optional<User> findByFcmToken(String fcmToken); // 기존 메소드 (에러 유발 가능성 있음)

    /**
     * [추가] FCM 토큰으로 모든 사용자를 조회합니다. (중복 토큰 처리용)
     * @param fcmToken 검색할 FCM 토큰
     * @return 해당 토큰을 가진 모든 User 리스트
     */
    List<User> findAllByFcmToken(String fcmToken); // <<< 여러 결과를 받을 메소드 추가

    List<User> findAllByAppFcmToken(String appFcmToken);

}