import axios from 'axios';

//TODO: 백엔드 URL 설정
//const API_URL = process.env.NEXT_PUBLIC_API_URL || 'https://igo.ai.kr';
const API_URL = 'http://localhost:8080'; // userApi.js에서 가져옴

const api = axios.create({
    baseURL: `${API_URL}/api`,
    headers: {
        'Content-Type': 'application/json'
    },
    withCredentials: true
});

// --- 인터셉터 로직 ---
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(); // 성공 시, 쿠키에 설정된 새 토큰으로 재시도하도록 resolve
    }
  });
  failedQueue = [];
};

api.interceptors.response.use(
  response => {
    return response;
  },
  async error => {
    const originalRequest = error.config;

    if (error.response && error.response.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then(() => {
            return api(originalRequest);
          })
          .catch(err => {
            return Promise.reject(err);
          });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        await api.post('/auth/refresh-token');
        processQueue(null);
        return api(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError);
        console.error('Unable to refresh token:', refreshError);
        if (typeof window !== 'undefined') {
          window.location.href = '/greeting';
        }
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }
    return Promise.reject(error);
  }
);
// --- 인터셉터 로직 끝 ---

export default api;

