import api from './axiosConfig'; // axiosConfig에서 api 임포트

// 루틴 생성 함수
export const createRoutine = async (routineData) => {
    // 백엔드의 RoutineRequestDTO 형식에 맞게 페이로드 구성
    const requestPayload = {
        name: routineData.title, // 루틴 이름
        items: routineData.items.map(item => ({
            name: item.name,
            durationMinutes: parseInt(item.time), // 문자열 시간을 정수형으로 변환
            isFlexibleTime: false // 기본값으로 false 설정, 필요시 프론트에서 입력받도록 수정 가능
        }))
    };

    // 단일 API 호출로 루틴과 아이템들을 함께 생성
    // POST /api/routines
    const response = await api.post('/routines', requestPayload);
    return response.data; // 백엔드에서 반환하는 RoutineResponseDTO
};

// 루틴 목록 조회 함수
export const getRoutines = async () => {
    // GET /api/routines
    const response = await api.get('/routines');
    return response.data;
};

// 특정 루틴 조회
export const getRoutineById = async (routineId) => {
    const response = await api.get(`/routines/${routineId}`);
    return response.data;
};

// 루틴 수정
export const updateRoutine = async (routineId, routineData) => {
    const requestPayload = {
        name: routineData.title,
        items: routineData.items.map(item => ({
            name: item.name,
            durationMinutes: parseInt(item.time),
            isFlexibleTime: false
        }))
    };

    const response = await api.put(`/routines/${routineId}`, requestPayload);
    return response.data;
};

// 루틴 삭제
export const deleteRoutine = async (routineId) => {
    const response = await api.delete(`/routines/${routineId}`);
    return response.data;
};

// 루틴 이름만 조회
export const getRoutineNames = async () => {
    const response = await api.get('/routines/names');
    return response.data;
};