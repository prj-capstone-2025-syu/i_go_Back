import api from './axiosConfig'; // axiosConfig에서 api 임포트

// 일정 생성
export const createSchedule = async (scheduleData) => {
    try {
        const response = await api.post('/schedules', scheduleData);
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

        const response = await api.get(`/schedules?${params.toString()}`);
        return response.data;
    } catch (error) {
        console.error('일정 조회 중 오류 발생:', error);
        throw error;
    }
};

// 일정 삭제
export const deleteSchedule = async (scheduleId) => {
    try {
        const response = await api.delete(`/schedules/${scheduleId}`);
        return response.data;
    } catch (error) {
        console.error('일정 삭제 실패:', error);
        throw error;
    }
};

// 특정 일정 조회
export const getScheduleById = async (scheduleId) => {
    try {
        const response = await api.get(`/schedules/${scheduleId}`);
        return response.data;
    } catch (error) {
        console.error('일정 조회 실패:', error);
        throw error;
    }
};

// 일정 수정
export const updateSchedule = async (scheduleId, scheduleData) => {
    try {
        const response = await api.put(`/schedules/${scheduleId}`, scheduleData);
        return response.data;
    } catch (error) {
        console.error('일정 수정 실패:', error);
        throw error;
    }
};

// 다가오는 일정 조회 (3개)
export const getUpcomingSchedules = async (limit = 3) => {
    try {
        const params = new URLSearchParams({
            limit: limit.toString()
        });
        const response = await api.get(`/schedules/upcoming?${params.toString()}`);
        return response.data;
    } catch (error) {
        console.error('다가오는 일정 조회 중 오류 발생:', error);
        throw error;
    }
};

//진행 중 일정 조회
// 진행 중인 가장 최근 일정 1개 조회 API
export const getLatestInProgressSchedule = async () => {
    try {
        const response = await api.get(`/schedules/in-progress/latest`);
        return response.data; // ScheduleType | null (백엔드에서 일정이 없으면 null 반환)
    } catch (error) {
        console.error('진행 중인 일정 조회 중 오류 발생:', error);
        // 백엔드에서 일정이 없을 때 null을 반환하도록 처리
        if (error.response && error.response.status === 404) { // 404도 "없음"으로 간주
            return null;
        }
        throw error;
    }
};