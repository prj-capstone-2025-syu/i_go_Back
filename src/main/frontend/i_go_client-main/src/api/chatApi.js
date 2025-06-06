import axios from 'axios';
//TODO: 백엔드 URL 설정
//const API_URL = process.env.NEXT_PUBLIC_API_URL || 'https://igo.ai.kr';
const API_URL ='http://localhost:8080';

const api = axios.create({
    baseURL: `${API_URL}/api`,
    headers: {
        'Content-Type': 'application/json'
    },
    withCredentials: true
});

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
