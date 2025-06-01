// Import the functions you need from the SDKs you need
import { initializeApp } from "firebase/app";
import { getAnalytics } from "firebase/analytics";
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

// Initialize Firebase
const app = initializeApp(firebaseConfig);
// const analytics = getAnalytics(app); // 필요하다면 주석 해제

export { app }; // 초기화된 app 객체를 내보냅니다.