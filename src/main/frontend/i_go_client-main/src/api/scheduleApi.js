// src/api/scheduleApi.js
import axios from 'axios';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const api = axios.create({
    baseURL: `${API_URL}/api`,
    headers: {
        'Content-Type': 'application/json'
    },
    withCredentials: true
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
        // 날짜 형식이 ISO 8601 형태인지 확인
        console.log("요청 시작 날짜:", start);
        console.log("요청 종료 날짜:", end);

        const params = new URLSearchParams({
            start: start,
            end: end
        });

        const response = await api.get(`/api/schedules?${params.toString()}`);
        return response.data;
    } catch (error) {
        console.error('일정 조회 중 오류 발생:', error);
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

// 특정 일정 조회
export const getScheduleById = async (scheduleId) => {
    try {
        const response = await api.get(`/api/schedules/${scheduleId}`);
        return response.data;
    } catch (error) {
        console.error('일정 조회 실패:', error);
        throw error;
    }
};

// 일정 수정
export const updateSchedule = async (scheduleId, scheduleData) => {
    try {
        const response = await api.put(`/api/schedules/${scheduleId}`, scheduleData);
        return response.data;
    } catch (error) {
        console.error('일정 수정 실패:', error);
        throw error;
    }
};