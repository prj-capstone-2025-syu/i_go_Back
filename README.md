# I-GO 프로젝트 실행 가이드

## 시스템 요구사항

* **JDK**: 17 이상
* **Gradle**: 7.x 이상
* **Node.js**: 18.x 이상
* **npm** 또는 **yarn**

---

## 백엔드 실행 방법

### 1. 프로젝트 클론 및 설정

1. 프로젝트 클론:

   ```bash
   git clone https://github.com/your-username/i-go-project.git
   cd i-go-project
   ```

2. `.properties` 백엔드 파일 이동 (src/main/resources/application.properties) 및, 로컬 환경 설정 (주석 확인)

### 2. 백엔드 실행 (Gradle 사용)

1. Gradle로 빌드 및 실행:

   ```bash
   ./gradlew clean build
   ./gradlew bootRun
   ```

2. JAR 파일 직접 실행:

   ```bash
   java -jar build/libs/i-go-project-0.0.1-SNAPSHOT.jar
   ```

> 기본적으로 백엔드는 `http://localhost:8080`에서 실행됩니다.

---

### 3. IDE에서 실행 (IntelliJ IDEA)

1. IntelliJ IDEA에서 프로젝트 오픈
2. Gradle 프로젝트로 임포트
3. 메인 애플리케이션 클래스 (`IGoApplication.java`) 실행

---

## 프론트엔드 실행 방법

### 1. 프론트엔드 디렉토리로 이동

```bash
cd src/main/frontend/i_go_client-main
```

### 2. 의존성 설치

```bash
npm install
# 또는
yarn
```

### 3. 개발 서버 실행

```bash
npm run dev
# 또는
yarn dev
```

> 브라우저에서 `http://localhost:3000`에 접속


## MySQL Workbench로 RDS DB 접속 방법

### 접속 정보

- **호스트명**: [호스트명 입력]
- **포트**: 3306
- **사용자명**: [호스트ID 입력]
- **비밀번호**: [호스트PW 입력]
- **데이터베이스**: [DB이름 입력]

## MySQL Workbench 접속 방법

1. **MySQL Workbench 실행**
   - MySQL Workbench를 실행합니다.

2. **새 연결 생성**
   - 홈 화면에서 "+" 버튼을 클릭하여 새 연결을 생성합니다.

3. **연결 설정**
   - **Connection Name**: IGO_RDS (원하는 이름)
   - **Connection Method**: Standard (TCP/IP)
   - **Hostname**: [호스트명 입력]
   - **Port**: 3306
   - **Username**: root

4. **연결 테스트**
   - "Test Connection" 버튼을 클릭합니다.
   - 비밀번호 입력란에 `rootroot`를 입력한 후 연결이 성공하는지 확인합니다.

5. **연결 저장**
   - 연결이 성공하면 "OK"를 클릭하여 설정을 저장합니다.

6. **데이터베이스 사용**
   - 저장된 연결을 더블클릭하여 접속합니다.
   - 접속 후 `user_schema` 데이터베이스를 사용합니다.
---
