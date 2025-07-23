importScripts('https://www.gstatic.com/firebasejs/9.6.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/9.6.0/firebase-messaging-compat.js');

// Initialize the Firebase app in the service worker by passing in the messagingSenderId
// TODO: Firebase 프로젝트 설정에서 실제 `messagingSenderId`로 교체하세요.
firebase.initializeApp({
    apiKey: "AIzaSyAKw4-LxFYGf4Q7D3qIVtgPggLU9HCi4Bc",
    authDomain: "igo-project-56559.firebaseapp.com",
    projectId: "igo-project-56559",
    storageBucket: "igo-project-56559.firebasestorage.app",
    messagingSenderId: "932057891922",
    appId: "1:932057891922:web:d45582c1010db17b1f8b8b",
    measurementId: "G-GBQRRJX8HP"
});

// Retrieve an instance of Firebase Messaging so that it can handle background messages.
const messaging = firebase.messaging();

// 백그라운드 메시지 핸들러
messaging.onBackgroundMessage((payload) => {
    console.log('백그라운드 메시지 수신:', payload);

    // Firebase가 자동으로 알림을 표시하므로 수동으로 표시하지 않음
    if (payload.notification) {
        console.log('Firebase가 자동으로 알림을 표시합니다.');
        // 추가 로직이 필요한 경우에만 여기서 처리
        return; // 수동 알림 표시 안함
    }

    // data-only 메시지인 경우에만 수동으로 알림 표시
    if (payload.data && !payload.notification) {
        const notificationTitle = payload.data.title || 'IGO 알림';
        const notificationOptions = {
            body: payload.data.body || '새로운 알림이 있습니다.',
            icon: '/logo.png',
            badge: '/logo.png',
            data: payload.data,
            tag: 'igo-notification', // 중복 알림 방지
            renotify: false
        };

        return self.registration.showNotification(notificationTitle, notificationOptions);
    }
});