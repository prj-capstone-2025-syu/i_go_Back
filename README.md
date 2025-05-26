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
