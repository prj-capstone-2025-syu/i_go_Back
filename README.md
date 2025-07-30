# I-GO 프로젝트 실행 가이드

## 시스템 요구사항

* **JDK**: 17 이상
* **Gradle**: 7.x 이상

---

## 배포 방식

해당 프로젝트 저장소는 master 브랜치에 코드가 푸시되면 **GitHub Actions**와 **Docker**를 이용하여 **블루-그린 배포**를 통해 **무중단 배포**가 자동으로 이루어집니다.

---

## 백엔드 실행 방법

### 1. 프로젝트 클론 및 설정

1. 프로젝트 클론:

   ```bash
   git clone https://github.com/your-username/i-go-project.git
   cd i-go-project
   ```

2. 환경 설정 파일 작성:
   - `src/main/resources/application.properties` 파일을 생성하고 환경 변수를 설정합니다.
   - 아래 "환경 변수 설정" 섹션을 참고하여 설정하세요.

### 2. 백엔드 실행 (Gradle 사용)

1. Gradle로 빌드 및 실행:

   ```bash
   ./gradlew clean build
   ./gradlew bootRun
   ```

2. JAR 파일 직접 실행:

   ```bash
   java -jar build/libs/demo-0.0.1-SNAPSHOT.jar
   ```

> 기본적으로 백엔드는 `http://localhost:8080`에서 실행됩니다.

---

### 3. IDE에서 실행 (IntelliJ IDEA)

1. IntelliJ IDEA에서 프로젝트 열기
2. Gradle 프로젝트로 임포트
3. 메인 애플리케이션 클래스 (`DemoApplication.java`) 실행

---

## 데이터베이스 연결

### MySQL Workbench로 RDS DB 접속

#### 접속 정보
- **호스트명**: [RDS 엔드포인트 입력]
- **포트**: 3306
- **사용자명**: [데이터베이스 사용자명 입력]
- **비밀번호**: [데이터베이스 비밀번호 입력]
- **데이터베이스**: [스키마명 입력]

#### MySQL Workbench 접속 단계

1. **MySQL Workbench 실행**
   - MySQL Workbench를 실행합니다.

2. **새 연결 생성**
   - 홈 화면에서 "+" 버튼을 클릭하여 새 연결을 생성합니다.

3. **연결 설정**
   - **Connection Name**: IGO_RDS (원하는 이름)
   - **Connection Method**: Standard (TCP/IP)
   - **Hostname**: [RDS 엔드포인트 입력]
   - **Port**: 3306
   - **Username**: [데이터베이스 사용자명]

4. **연결 테스트**
   - "Test Connection" 버튼을 클릭합니다.
   - 비밀번호를 입력한 후 연결이 성공하는지 확인합니다.

5. **연결 저장 및 접속**
   - 연결이 성공하면 "OK"를 클릭하여 설정을 저장합니다.
   - 저장된 연결을 더블클릭하여 데이터베이스에 접속합니다.

---

## 환경 변수 설정 (`application.properties`)

백엔드 프로젝트의 `src/main/resources/` 경로에 `application.properties` 파일을 생성하고, 아래 예시를 참고하여 환경에 맞는 설정값을 입력해야 합니다.


<details>
<summary><strong>전체 환경 변수 예시 및 설명 보기</strong></summary>

```properties
# 애플리케이션 기본 설정
spring.application.name=IGO
spring.security.user.name=user                    # 기본 보안 사용자명
spring.security.user.password=1234                # 기본 보안 비밀번호

# JPA/Hibernate 설정
spring.jpa.hibernate.ddl-auto=update              # 테이블 자동 생성/업데이트 (create, update, validate, create-drop)
spring.jpa.show-sql=true                          # SQL 쿼리 로그 출력
spring.jpa.properties.hibernate.format_sql=true   # SQL 쿼리 포맷팅

# 서버 설정
server.address=0.0.0.0                            # 모든 네트워크 인터페이스에서 접근 허용
server.port=8080                                  # 서버 포트 설정
server.forward-headers-strategy=NATIVE            # 프록시 헤더 처리 전략

# Thymeleaf 템플릿 엔진 설정
spring.thymeleaf.cache=false                      # 개발 시 템플릿 캐시 비활성화

# 데이터베이스 연결 설정
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.url=jdbc:mysql://[RDS_ENDPOINT]:3306/[DATABASE_NAME]?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
spring.datasource.username=[DB_USERNAME]          # 데이터베이스 사용자명
spring.datasource.password=[DB_PASSWORD]          # 데이터베이스 비밀번호

# JWT 토큰 설정
jwt.secret=[JWT_SECRET_KEY]                       # JWT 서명용 비밀키 (충분히 복잡한 문자열)
jwt.refresh=14400000                              # 리프레시 토큰 만료시간 (밀리초)
jwt.expiration-hours=3600000                      # 액세스 토큰 만료시간 (밀리초)

# Google OAuth2 설정
spring.security.oauth2.client.registration.google.client-id=[GOOGLE_CLIENT_ID]
spring.security.oauth2.client.registration.google.client-secret=[GOOGLE_CLIENT_SECRET]
spring.security.oauth2.client.registration.google.scope=email,profile,https://www.googleapis.com/auth/calendar,https://www.googleapis.com/auth/calendar.events
spring.security.oauth2.client.registration.google.redirect-uri=https://igo.ai.kr/login/oauth2/code/google

# Google OAuth2 제공자 설정
spring.security.oauth2.client.provider.google.authorization-uri=https://accounts.google.com/o/oauth2/v2/auth
spring.security.oauth2.client.provider.google.token-uri=https://www.googleapis.com/oauth2/v4/token
spring.security.oauth2.client.provider.google.user-info-uri=https://www.googleapis.com/oauth2/v3/userinfo
spring.security.oauth2.client.provider.google.user-name-attribute=sub

# 프론트엔드 URL 설정
frontend.url=https://igo.ai.kr                    # CORS 및 리다이렉션용 프론트엔드 URL

# 로깅 설정
logging.level.org.springframework.web=DEBUG       # Spring Web 로그 레벨
logging.level.org.springframework.security=DEBUG  # Spring Security 로그 레벨

# 외부 API 설정
exaone.api.url=[EXAONE AI API ENDPOINT]           # EXAONE AI API 엔드포인트

# Firebase 설정
firebase.key.path=firebase/igo-project-56559-firebase-adminsdk-fbsvc-ddd43a3897.json  # Firebase 서비스 계정 키 파일 경로

# T맵 API 설정
tmap.appkey=[TMAP_APP_KEY]                        # T맵 API 키
tmap.transit.appkey=[TMAP_TRANSIT_KEY]            # T맵 대중교통 API 키

# Spring Boot Actuator 헬스체크 설정 (블루-그린 배포용)
management.endpoints.web.exposure.include=health  # 헬스체크 엔드포인트 노출
management.endpoint.health.show-details=always    # 헬스체크 상세 정보 표시
```

### 설정값 변경 가이드

1. **`[RDS_ENDPOINT]`**: AWS RDS MySQL 인스턴스의 엔드포인트를 입력
2. **`[DATABASE_NAME]`**: 사용할 데이터베이스(스키마) 이름
3. **`[DB_USERNAME]`, `[DB_PASSWORD]`**: 데이터베이스 접속 계정 정보
4. **`[JWT_SECRET_KEY]`**: JWT 토큰 서명용 비밀키 (최소 256비트 권장)
5. **`[GOOGLE_CLIENT_ID]`, `[GOOGLE_CLIENT_SECRET]`**: Google OAuth2 애플리케이션 정보
6. **`[TMAP_APP_KEY]`, `[TMAP_TRANSIT_KEY]`**: T맵 API 서비스 키

</details>


---
