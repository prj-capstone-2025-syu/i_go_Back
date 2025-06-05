// Import the functions you need from the SDKs you need
import { initializeApp } from "firebase/app";
import { getMessaging, getToken } from 'firebase/messaging';
// TODO: Add SDKs for Firebase products that you want to use
// https://firebase.google.com/docs/web/setup#available-libraries

// Your web app's Firebase configuration
// For Firebase JS SDK v7.20.0 and later, measurementId is optional
const firebaseConfig = {
    apiKey: "AIzaSyAKw4-LxFYGf4Q7D3qIVtgPggLU9HCi4Bc",
    authDomain: "igo-project-56559.firebaseapp.com",
    projectId: "igo-project-56559",
    storageBucket: "igo-project-56559.firebasestorage.app",
    messagingSenderId: "932057891922",
    appId: "1:932057891922:web:d45582c1010db17b1f8b8b",
    measurementId: "G-GBQRRJX8HP"
};

// Firebase 초기화
const app = initializeApp(firebaseConfig);

// Firebase Cloud Messaging 초기화 (브라우저 환경에서만)
let messaging = null;
if (typeof window !== 'undefined') {
    try {
        messaging = getMessaging(app);
    } catch (error) {
        console.error('Firebase Messaging 초기화 실패:', error);
    }
}

export { app, messaging };