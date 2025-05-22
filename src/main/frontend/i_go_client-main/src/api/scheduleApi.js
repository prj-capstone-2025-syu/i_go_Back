// src/api/scheduleApi.js
import axios from 'axios';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

// 기본 axios 인스턴스 설정
const api = axios.create({
    baseURL: API_URL,
    withCredentials: true, // 쿠키 전송을 위해 필요
    headers: {
        'Content-Type': 'application/json'
    }
});

// 일정 생성
export const createSchedule = async (scheduleData) => {
    try {
        const response = await api.post('/api/schedules', scheduleData);
        return response.data;
    } catch (error) {
        console.error('일정 생성 실패:', error);
        throw error;
    }
};

// 일정 조회 (날짜 범위)
export const getSchedules = async (start, end) => {
    try {
        const response = await api.get('/api/schedules', {
            params: { start, end }
        });
        return response.data;
    } catch (error) {
        console.error('일정 조회 실패:', error);
        throw error;
    }
};

// 일정 삭제
export const deleteSchedule = async (scheduleId) => {
    try {
        const response = await api.delete(`/api/schedules/${scheduleId}`);
        return response.data;
    } catch (error) {
        console.error('일정 삭제 실패:', error);
        throw error;
    }
};