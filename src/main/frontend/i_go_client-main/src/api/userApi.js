import api from './axiosConfig'; // axiosConfig에서 api 임포트
import {app} from '@/utils/firebase'; // Firebase 초기화 코드가 있는 파일에서 app을 가져옵니다.
import { getMessaging, getToken } from "firebase/messaging"; // Firebase 메시징

// 현재 사용자 정보 조회
export const getCurrentUser = async () => {
    const response = await api.get('/user/me');
    return response.data;
};

// 로그아웃
export const logout = async () => {
    const response = await api.post('/user/logout');
    return response.data;
};

// 회원 탈퇴
export const deleteAccount = async () => {
    const response = await api.delete('/user/me');
    return response.data;
};

// 사용자 정보 업데이트
export const updateUserInfo = async (userData) => {
    const response = await api.put('/user/me', userData);
    return response.data;
};

// 사용자 알림 설정을 가져오는 함수
export const getNotificationSettings = async () => {
    const response = await api.get('/user/me/settings/notifications'); // api 인스턴스 사용
    return response.data;
};

// 사용자 알림 설정을 업데이트하는 함수
export const updateNotificationSettings = async (settings) => {
    const response = await api.put('/user/me/settings/notifications', settings); // api 인스턴스 사용
    return response.data; // 서버에서 업데이트된 전체 설정을 반환한다고 가정합니다.
};

// FCM 토큰을 서버로 전송하는 함수
export const sendFCMTokenToServer = async (token) => {
    try {
        const response = await api.post('/user/fcm-token', { fcmToken: token });
        console.log('FCM token sent to server successfully:', response.data);
        return response.data;
    } catch (error) {
        console.error('Error sending FCM token to server:', error);
        throw error;
    }
};

// 최근 알림 목록 가져오기
export const getRecentNotifications = async (limit = 7) => {
    const response = await api.get(`/notifications/recent?limit=${limit}`);
    return response.data;
};