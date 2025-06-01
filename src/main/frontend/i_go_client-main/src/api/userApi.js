import axios from 'axios';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const api = axios.create({
    baseURL: `${API_URL}/api`,
    headers: {
        'Content-Type': 'application/json'
    },
    withCredentials: true
}); // 절대경로 -> 무조건 바꿔야함.

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
    const response = await axios.get(`${API_URL}/api/user/me/settings/notifications`, {
        withCredentials: true, // 인증 쿠키를 포함하여 요청
    });
    return response.data;
};

// 사용자 알림 설정을 업데이트하는 함수
export const updateNotificationSettings = async (settings) => {
    const response = await axios.put(`${API_URL}/api/user/me/settings/notifications`, settings, {
        withCredentials: true, // 인증 쿠키를 포함하여 요청
    });
    return response.data; // 서버에서 업데이트된 전체 설정을 반환한다고 가정합니다.
};