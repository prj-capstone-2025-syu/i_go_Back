import axios from 'axios';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const api = axios.create({
    baseURL: `${API_URL}/api`,
    headers: {
        'Content-Type': 'application/json'
    },
    withCredentials: true
});

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