importScripts('https://www.gstatic.com/firebasejs/9.0.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/9.0.0/firebase-messaging-compat.js');

// Initialize the Firebase app in the service worker by passing in the messagingSenderId
const firebaseConfig = {
    apiKey: "AIzaSyAKw4-LxFYGf4Q7D3qIVtgPggLU9HCi4Bc",
    authDomain: "igo-project-56559.firebaseapp.com",
    projectId: "igo-project-56559",
    storageBucket: "igo-project-56559.firebasestorage.app",
    messagingSenderId: "932057891922",
    appId: "1:932057891922:web:d45582c1010db17b1f8b8b",
    measurementId: "G-GBQRRJX8HP"
};

firebase.initializeApp(firebaseConfig);

// Retrieve an instance of Firebase Messaging so that it can handle background messages.
const messaging = firebase.messaging();

// 백그라운드 메시지를 처리하는 핸들러
// 이 코드는 앱이 백그라운드 상태일 때만 실행됨
messaging.onBackgroundMessage((payload) => {
    console.log('[firebase-messaging-sw.js] Received background message ', payload);

    // 포그라운드 상태 확인을 위한 플래그 (payload.data에서 확인)
    // 앱이 포그라운드 상태라면 처리하지 않음
    if (payload.data && payload.data.foreground === 'true') {
        console.log('앱이 포그라운드 상태입니다. 알림을 표시하지 않습니다.');
        return;
    }

    // 백그라운드 상태에서만 알림을 표시
    const notificationTitle = payload.notification.title;
    const notificationOptions = {
        body: payload.notification.body,
        icon: '/logo.png', // 아이콘
        data: {
            url: payload.data && payload.data.url ? payload.data.url : '/'
        }
    };

    self.registration.showNotification(notificationTitle, notificationOptions);
});

