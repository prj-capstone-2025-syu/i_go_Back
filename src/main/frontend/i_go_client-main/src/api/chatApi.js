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

export const sendChatMessage = async (message) => {
    try {
        const response = await api.post('/chat', { message });
        return response.data;
    } catch (error) {
        console.error('채팅 메시지 전송 실패:', error);
        throw error;
    }
}; 