import { getMessaging, getToken, onMessage } from "firebase/messaging";
import { app } from "@/utils/firebase";

// FCM 메시징 객체를 가져오는 함수
export const initMessaging = () => {
  if (typeof window !== 'undefined' && 'Notification' in window) {
    try {
      return getMessaging(app);
    } catch (error) {
      console.error("Firebase messaging initialization error:", error);
      return null;
    }
  }
  return null;
};

// FCM 토큰을 요청하는 함수
export const requestFCMToken = async (vapidKey) => {
  const messaging = initMessaging();
  if (!messaging) return null;

  try {
    const permission = await Notification.requestPermission();
    if (permission !== "granted") {
      console.log("Notification permission denied");
      return null;
    }

    const token = await getToken(messaging, {
      vapidKey: vapidKey,
    });
    return token;
  } catch (error) {
    console.error("Error getting FCM token:", error);
    return null;
  }
};

// 포���라운드 FCM 메시지 수신 처리 함수
export const setupFCMListener = (handleForegroundMessage) => {
  const messaging = initMessaging();
  if (!messaging) return;

  // 포그라운드 메시지 핸들러 등록
  // 이 함수는 앱이 포그라운드 상태일 때만 실행됨
  onMessage(messaging, (payload) => {
    console.log("Foreground message received:", payload);

    // 포그라운드에서 메시지 수신 시 커스텀 처리 함수 호출
    if (handleForegroundMessage) {
      handleForegroundMessage(payload);
    } else {
      // 기본 처리: 브라우저 알림 표시 (포그라운드에서는 자동 표시되지 않음)
      // 이 부분은 백그라운드 상태와 중복 알림이 발생하는 부분
      // 따라서 여기서 알림을 직접 표시하지 않고, UI에 통합된 방식으로 표시하도록 수정
      console.log("No custom handler for foreground message");
    }
  });

  console.log("FCM foreground message listener setup complete");
};

// 커스텀 UI 알림 표시 함수 (포그라운드 상태에서 사용)
export const showCustomNotification = (title, body, onClick) => {
  // 여기에 앱 내 커스텀 알림 UI를 표시하는 코드를 구현
  // 예: Toast 메시지 또는 앱 헤더에 알림 배너 등
  console.log("Showing custom in-app notification:", title, body);

  // 구현 예시: 커스텀 이벤트 발생시키기
  if (typeof window !== 'undefined') {
    const event = new CustomEvent('customNotification', {
      detail: { title, body, onClick }
    });
    window.dispatchEvent(event);
  }
};
