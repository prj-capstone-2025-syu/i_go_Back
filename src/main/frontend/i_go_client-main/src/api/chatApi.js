import api from './axiosConfig'; // axiosConfig에서 api 임포트

// 채팅 메시지 전송
export const sendChatMessage = async (message) => {
    try {
        // session_id 대신 message만 전송 (userId는 백엔드에서 설정)
        const response = await api.post('/chat', { message });
        return response.data;
    } catch (error) {
        console.error('메시지 전송 실패:', error);
        throw error;
    }
};

// AI function call 처리
export const handleAIFunction = async (functionCallObj) => {
    try {
        const response = await api.post('/schedules/ai-function', { function_call: functionCallObj });
        return response.data;
    } catch (error) {
        console.error('AI function 처리 실패:', error);
        throw error;
    }
};
