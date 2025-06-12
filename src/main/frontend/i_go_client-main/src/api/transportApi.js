// src/api/transportApi.js
import axios from 'axios';

// API URL 설정
const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const api = axios.create({
    baseURL: `${API_URL}/api`,
    headers: {
        'Content-Type': 'application/json'
    },
    withCredentials: true
});

// 로컬 스토리지 캐시 키 접두어
const CACHE_PREFIX = 'transport_time_';
const CACHE_DURATION = 60 * 60 * 1000; // 1시간 (밀리초 단위)

// TMAP API 키
const TMAP_API_KEY = process.env.NEXT_PUBLIC_TMAP_API_KEY;
const TMAP_TRANSIT_API_KEY = process.env.NEXT_PUBLIC_TMAP_TRANSIT_API_KEY;

// TMAP API 스크립트 로드 상태
let isTmapLoaded = false;
let tmapLoadPromise = null;

// TMAP API 스크립트 로드 함수
const loadTmapAPI = () => {
    if (typeof window === 'undefined') {
        console.log('🚕 [API:DEBUG] SSR 환경에서는 TMAP API 로드가 불가능합니다');
        return Promise.resolve(false);
    }

    // API 키 확인 - 더 명확한 메시지 출력
    if (!TMAP_API_KEY) {
        console.error('🚕 [API:ERROR] TMAP API 키가 설정되지 않았습니다. 환경 변수를 확인해주세요.');
        return Promise.reject(new Error('TMAP API 키가 설정되지 않았습니다'));
    }

    // 이미 로드 중이면 기존 Promise 반환
    if (tmapLoadPromise) {
        console.log('🚕 [API:DEBUG] TMAP API 이미 로드 중, 기존 Promise 반환');
        return tmapLoadPromise;
    }

    // 이미 로드되었으면 바로 완료
    if (isTmapLoaded && window.Tmapv2) {
        console.log('🚕 [API:DEBUG] TMAP API 이미 로드됨, 즉시 완료');
        return Promise.resolve(true);
    }

    // API 키로 URL을 구성하기 전에 디버그 정보 출력
    const apiKey = TMAP_API_KEY?.substring(0, 4) || 'not-set';
    console.log('🚕 [API:DEBUG] TMAP API 키 확인:', `${apiKey}...`);

    // 새로운 스크립트 로드 방식 (더 간단하고 안정적인 방식)
    tmapLoadPromise = new Promise((resolve, reject) => {
        try {
            // document가 아직 로드되지 않았는지 확인
            if (!document.body) {
                console.error('🚕 [API:ERROR] document.body가 아직 로드되지 않았습니다');
                reject(new Error('DOM이 아직 로드되지 않았습니다'));
                tmapLoadPromise = null;
                return;
            }

            // 기존 스크립트가 이미 있는지 확인
            const existingScript = document.querySelector(
                `script[src*="tmapapi.tmapmobility.com"]`
            );

            if (existingScript) {
                console.log('🚕 [API:DEBUG] TMAP API 스크립트가 이미 DOM에 존재합니다');

                // 이미 로드되었는지 확인
                if (window.Tmapv2) {
                    console.log('🚕 [API:DEBUG] 기존 스크립트에서 Tmapv2 객체 발견');
                    isTmapLoaded = true;
                    resolve(true);
                    return;
                }

                // 기존 스크립트를 제거하고 새로 로드
                existingScript.parentNode.removeChild(existingScript);
                console.log('🚕 [API:DEBUG] 기존 스크립트 제거, 재로드 시도');
            }

            const script = document.createElement('script');
            script.type = 'text/javascript';

            // JSONP 방식 대신 직접 로드 방식으로 변경 (더 안정적)
            script.src = `https://tmapapi.tmapmobility.com/jsv2?version=1&appKey=${TMAP_API_KEY}`;
            script.async = true;
            script.crossOrigin = "anonymous";

            // 스크립트 로드 이벤트 핸들러
            script.onload = () => {
                console.log('🚕 [API:DEBUG] TMAP API 스크립트 로드됨, Tmapv2 객체 확인 중...');

                // 스크립트 로드 후 객체가 사용 가능한지 확인하는 함수
                const checkTmapObject = () => {
                    if (window.Tmapv2) {
                        console.log('🚕 [API:DEBUG] Tmapv2 객체 확인됨, 로드 완료');
                        isTmapLoaded = true;
                        resolve(true);
                        tmapLoadPromise = null;
                    } else {
                        console.log('🚕 [API:DEBUG] Tmapv2 객체가 아직 없음, 100ms 후 재시도...');
                        // 100ms 후에 다시 확인
                        setTimeout(checkTmapObject, 100);
                    }
                };

                // 첫 번째 확인 시작
                checkTmapObject();
            };

            // 에러 핸들러 개선
            script.onerror = (e) => {
                const errorInfo = {
                    message: e.message || '알 수 없는 오류',
                    type: e.type,
                    timestamp: new Date().toISOString()
                };

                console.error('🚕 [API:ERROR] TMAP API 스크립트 로드 실패:', JSON.stringify(errorInfo));

                // 브라우저 콘솔에 추가 디버깅 정보
                console.error('🚕 [API:ERROR] 상세 오류 정보:', e);

                // API 키가 유효한지 테스트
                console.log('🚕 [API:DEBUG] API 키 유효성 테스트 중...');

                // API 키 길이 확인
                if (!TMAP_API_KEY || TMAP_API_KEY.length < 10) {
                    console.error('🚕 [API:ERROR] API 키가 너무 짧거나 없습니다:', TMAP_API_KEY);
                }

                reject(new Error(`TMAP API 스크립트 로드 실패: ${errorInfo.message}`));
                tmapLoadPromise = null;
            };

            // 타임아웃 설정 (10초)
            const timeoutId = setTimeout(() => {
                if (!isTmapLoaded) {
                    console.error('🚕 [API:ERROR] TMAP API 로드 타임아웃 (10초)');
                    reject(new Error('TMAP API 로드 타임아웃'));
                    tmapLoadPromise = null;
                }
            }, 10000);

            console.log('🚕 [API:DEBUG] TMAP API 스크립트를 DOM에 추가, 로드 시작...');
            document.body.appendChild(script);

            // 스크립트 삽입 디버깅
            console.log('🚕 [API:DEBUG] 스크립트 URL:', script.src);
            console.log('🚕 [API:DEBUG] 스크립트 DOM 삽입 완료');

        } catch (err) {
            console.error('🚕 [API:ERROR] 스크립트 DOM 추가 중 오류:', err);
            reject(err);
            tmapLoadPromise = null;
        }
    }).catch(err => {
        tmapLoadPromise = null;
        console.error('🚕 [API:ERROR] TMAP API 로드 실패 (Promise catch):', err);
        throw err;
    });

    return tmapLoadPromise;
};

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

// 도보 이동시간 계산 (TMAP API 직접 호출)
const getWalkingTime = (startX, startY, endX, endY) => {
    return new Promise(async (resolve, reject) => {
        try {
            // API 로드 확인
            const isLoaded = await loadTmapAPI();
            if (!isLoaded || !window.Tmapv2) {
                throw new Error('TMAP API 로드 실패');
            }

            const tData = new window.Tmapv2.TData();

            tData.getRoutePlanForPeopleJson(
                startX, startY, endX, endY,
                "traoptimal",
                function(result) {
                    const data = JSON.parse(result.responseText);
                    const totalTimeInSeconds = data.features[0].properties.totalTime;
                    const timeInMinutes = Math.ceil(totalTimeInSeconds / 60);
                    resolve(timeInMinutes);
                },
                function(error) {
                    console.error('도보 경로 계산 실패:', error);
                    reject(error);
                }
            );
        } catch (error) {
            console.error('도보 이동시간 계산 오류:', error);
            reject(error);
        }
    });
};

// 자차 이동시간 계산 (TMAP API 직접 호출)
const getDrivingTime = (startX, startY, endX, endY) => {
    return new Promise(async (resolve, reject) => {
        try {
            // API 로드 확인
            const isLoaded = await loadTmapAPI();
            if (!isLoaded || !window.Tmapv2) {
                throw new Error('TMAP API 로드 실패');
            }

            const tData = new window.Tmapv2.TData();

            tData.getRoutePlanJson(
                startX, startY, endX, endY,
                "traoptimal",
                function(result) {
                    const data = JSON.parse(result.responseText);
                    const totalTimeInSeconds = data.features[0].properties.totalTime;
                    const timeInMinutes = Math.ceil(totalTimeInSeconds / 60);
                    resolve(timeInMinutes);
                },
                function(error) {
                    console.error('자차 경로 계산 실패:', error);
                    reject(error);
                }
            );
        } catch (error) {
            console.error('자차 이동시간 계산 오류:', error);
            reject(error);
        }
    });
};

// 대중교통 이동시간 계산 (TMAP Transit API 직접 호출)
const getTransitTimeDirectly = async (startX, startY, endX, endY) => {
    try {
        const requestData = {
            startX: startX.toString(),
            startY: startY.toString(),
            endX: endX.toString(),
            endY: endY.toString(),
            count: 1,
            format: "json"
        };

        const response = await fetch('https://apis.openapi.sk.com/transit/routes/sub', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'appKey': TMAP_TRANSIT_API_KEY
            },
            body: JSON.stringify(requestData)
        });

        if (!response.ok) {
            throw new Error(`대중교통 API 오류: ${response.status}`);
        }

        const data = await response.json();

        // 에러 코드 확인
        if (data.metaData.code !== 0) {
            console.warn('대중교통 API 응답 에러:', data.metaData.message);
            return null;
        }

        const totalTimeInSeconds = data.metaData.plan.itineraries[0].totalTime;
        const timeInMinutes = Math.ceil(totalTimeInSeconds / 60);
        return timeInMinutes;
    } catch (error) {
        console.error('대중교통 API 호출 실패:', error);
        return null;
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

        // 클라이언트 API 직접 호출 시도
        if (typeof window !== 'undefined') {
            try {
                // TMAP API 로드
                await loadTmapAPI();

                // 병렬로 모든 이동수단 시간 계산
                const [walkingTime, drivingTime, transitTime] = await Promise.allSettled([
                    getWalkingTime(coordinates.startX, coordinates.startY, coordinates.endX, coordinates.endY),
                    getDrivingTime(coordinates.startX, coordinates.startY, coordinates.endX, coordinates.endY),
                    getTransitTimeDirectly(coordinates.startX, coordinates.startY, coordinates.endX, coordinates.endY)
                ]);

                // 결과 설정
                results.walking = walkingTime.status === 'fulfilled' ? walkingTime.value : null;
                results.driving = drivingTime.status === 'fulfilled' ? drivingTime.value : null;
                results.transit = transitTime.status === 'fulfilled' ? transitTime.value : null;

                console.log('클라이언트 API 직접 호출 결과:', results);

                // 결과 캐싱
                if (results.walking !== null || results.driving !== null || results.transit !== null) {
                    saveToCache(cacheKey, results);
                    return results;
                }
            } catch (clientError) {
                console.error('클라이언트 API 직접 호출 실패:', clientError?.message || clientError);
                // 클라이언트 직접 호출 실패 시 서버 API 호출로 폴백
            }
        }

        // 백엔드 API를 통한 이동시간 계산 (폴백)
        try {
            console.log('서버 API 폴백 호출 - 요청 데이터:', coordinates);

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
        console.log('모든 API 호출 실패, 기본값 사용');
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

    // 클라이언트 직접 API 호출 시도
    if (typeof window !== 'undefined' && TMAP_TRANSIT_API_KEY) {
        try {
            const transitTime = await getTransitTimeDirectly(startX, startY, endX, endY);
            if (transitTime !== null) {
                saveToCache(cacheKey, transitTime);
                return transitTime;
            }
        } catch (clientError) {
            console.error('대중교통 클라이언트 API 호출 실패:', clientError);
        }
    }

    // 서버 API 호출 (폴백)
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
            return {
                x: response.data.x, // 경도
                y: response.data.y  // 위도
            };
        }
    } catch (error) {
        console.error('주소->좌표 변환 실패:', error);
    }

    // 실패시 기본 좌표값 (서울시청)
    return {
        x: 126.9779692,
        y: 37.5662952
    };
};
