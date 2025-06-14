import api from './axiosConfig'; // axiosConfig에서 api 임포트

// 로컬 스토리지 캐시 키 접두어
const CACHE_PREFIX = 'transport_time_';
const CACHE_DURATION = 60 * 60 * 1000; // 1시간 (밀리초 단위)

// 캐시에서 이동시간 데이터 가져오기
const getFromCache = (cacheKey) => {
    if (typeof window === 'undefined') return null;

    try {
        const cachedData = localStorage.getItem(cacheKey);
        if (cachedData) {
            const { timestamp, data } = JSON.parse(cachedData);
            // 캐시 유효성 검사 (1시간)
            if (Date.now() - timestamp < CACHE_DURATION) {
                return data;
            }
        }
    } catch (error) {
        console.error('캐시 데이터 가져오기 실패:', error);
    }
    return null;
};

// 캐시에 이동시간 데이터 저장
const saveToCache = (cacheKey, data) => {
    if (typeof window === 'undefined') return;

    try {
        const cacheData = {
            timestamp: Date.now(),
            data: data
        };
        localStorage.setItem(cacheKey, JSON.stringify(cacheData));
    } catch (error) {
        console.error('캐시 데이터 저장 실패:', error);
    }
};

// 모든 이동수단의 시간 계산 (도보, 자차, 대중교통)
export const calculateAllTransportTimes = async (startX, startY, endX, endY) => {
    // 입력 인자 검증
    if (!startX || !startY || !endX || !endY) {
        console.error('좌표값이 없습니다. 출발지와 도착지 좌표가 필요합니다.');
        return {
            walking: null,
            driving: null,
            transit: null
        };
    }

    // 좌표값을 항상 숫자로 변환 (문자열이 들어올 경우 대비)
    const coordinates = {
        startX: parseFloat(startX),
        startY: parseFloat(startY),
        endX: parseFloat(endX),
        endY: parseFloat(endY)
    };

    // 숫자 변환 후 유효성 검증
    if (isNaN(coordinates.startX) || isNaN(coordinates.startY) ||
        isNaN(coordinates.endX) || isNaN(coordinates.endY)) {
        console.error('유효하지 않은 좌표값입니다:', { startX, startY, endX, endY });
        return {
            walking: null,
            driving: null,
            transit: null
        };
    }

    // 캐시 키 생성
    const cacheKey = `${CACHE_PREFIX}${coordinates.startX}_${coordinates.startY}_${coordinates.endX}_${coordinates.endY}`;

    // 캐시에서 데이터 확인
    const cachedData = getFromCache(cacheKey);
    if (cachedData) {
        console.log('캐시된 이동시간 데이터 사용:', cachedData);
        return cachedData;
    }

    // 결과 객체 초기화
    const results = {
        walking: null,
        driving: null,
        transit: null
    };

    try {
        console.log('이동시간 계산 시작:', coordinates);

        // 백엔드 API를 통한 이동시간 계산
        try {
            console.log('서버 API 호출 - 요청 데이터:', coordinates);

            // 서버 요청에 타임아웃 설정
            const response = await api.post('/transport-time', coordinates, {
                timeout: 10000 // 10초 타임아웃
            });

            // 응답 데이터 유효성 검증
            if (response.status === 200 && response.data) {
                console.log('서버 API 응답:', response.data);

                // 필드별로 null 체크를 하여 안전하게 할당
                if (response.data.walking !== undefined) results.walking = response.data.walking;
                if (response.data.driving !== undefined) results.driving = response.data.driving;
                if (response.data.transit !== undefined) results.transit = response.data.transit;

                // 결과 캐싱 (적어도 하나의 값이 있는 경우에만)
                if (results.walking !== null || results.driving !== null || results.transit !== null) {
                    saveToCache(cacheKey, results);
                    return results;
                }
            } else {
                console.warn('서버 API 응답은 성공했지만 데이터가 없거나 유효하지 않습니다');
            }
        } catch (apiError) {
            console.error('서버 API 호출 실패:',
                apiError?.response?.status || '상태 코드 없음',
                apiError?.response?.data || '데이터 없음',
                apiError?.message || '오류 메시지 없음');

            // 서버 오류 상세 로깅 (디버깅용)
            if (apiError?.response) {
                console.error('응답 상세:', {
                    status: apiError.response.status,
                    statusText: apiError.response.statusText,
                    headers: apiError.response.headers,
                    data: apiError.response.data
                });
            }
        }

        // 모든 API 호출이 실패한 경우 기본값 사용
        console.log('서버 API 호출 실패, 기본값 사용');
        const fallbackTimes = {
            walking: 15,    // 도보 15분
            driving: 5,     // 자가용 5분
            transit: 10     // 대중교통 10분
        };

        results.walking = fallbackTimes.walking;
        results.driving = fallbackTimes.driving;
        results.transit = fallbackTimes.transit;

        // 임시 폴백 데이터 캐시 (짧은 시간)
        try {
            const temporaryCacheData = {
                timestamp: Date.now() - (CACHE_DURATION - 10 * 60 * 1000), // 10분만 유효
                data: results
            };
            localStorage.setItem(cacheKey, JSON.stringify(temporaryCacheData));
        } catch (cacheError) {
            console.error('임시 캐시 저장 실패:', cacheError);
        }
    } catch (error) {
        console.error('이동시간 계산 중 예상치 못한 오류:', error);
    }

    return results;
};

/*
// 대중교통 시간만 별도로 계산 (사용자가 명시적으로 요청할 경우)
export const getTransitTime = async (startX, startY, endX, endY) => {
    if (!startX || !startY || !endX || !endY) {
        console.error('좌표값이 없습니다.');
        return null;
    }

    const cacheKey = `${CACHE_PREFIX}transit_${startX}_${startY}_${endX}_${endY}`;

    // 캐시 확인
    const cachedData = getFromCache(cacheKey);
    if (cachedData) {
        return cachedData;
    }

    // 서버 API 호출
    try {
        const response = await api.post('/transit-time', {
            startX: parseFloat(startX),
            startY: parseFloat(startY),
            endX: parseFloat(endX),
            endY: parseFloat(endY)
        });

        if (response.status === 200 && response.data) {
            const transitTime = response.data;
            saveToCache(cacheKey, transitTime);
            return transitTime;
        }
    } catch (error) {
        console.error('대중교통 이동시간 계산 실패:', error);

        // API 호출 제한 오류인 경우
        if (error.response && error.response.status === 429) {
            console.warn('대중교통 API 일일 호출 한도에 도달했습니다.');
        }
    }

    return null;
};

// API 상태 확인 (호출 제한 등을 체크)
export const checkTransitApiStatus = async () => {
    try {
        const response = await api.get('/transit-api-status');
        return response.data;
    } catch (error) {
        console.error('API 상태 확인 실패:', error);
        return { available: false };
    }
};

// 주소를 좌표로 변환하는 간단한 함수 (카카오맵 API 사용)
export const getCoordinateFromAddress = async (address) => {
    if (!address) return null;

    try {
        // 서버 측에서 주소->좌표 변환 API 호출
        const response = await api.get('/geocode', {
            params: { address }
        });

        if (response.status === 200 && response.data) {
            // 서버에서 좌표값이 유효한지 확인
            if (response.data.x && response.data.y) {
                return {
                    x: response.data.x, // 경도
                    y: response.data.y  // 위도
                };
            } else {
                console.warn('주소->좌표 변환: 서버에서 유효한 좌표를 반환하지 않았습니다', response.data);
            }
        }
    } catch (error) {
        console.error('주소->좌표 변환 실패:', error);
    }

    // 실패 시 null 반환 (하드코딩된 좌표 대신)
    console.warn('주소->좌표 변환에 실패했습니다. null 반환');
    return null;
};
*/
