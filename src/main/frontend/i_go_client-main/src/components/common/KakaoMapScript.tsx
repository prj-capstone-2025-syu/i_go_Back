'use client';

import { useEffect, useState } from 'react';

export default function KakaoMapScript() {
    const [isLoaded, setIsLoaded] = useState(false);

    const loadKakaoMapAPI = () => {
        return new Promise<void>((resolve, reject) => {
            // 이미 로드된 경우 바로 resolve
            if (
                window.kakao &&
                window.kakao.maps &&
                window.kakao.maps.services &&
                window.kakao.maps.services.Places &&
                window.kakao.maps.services.Status
            ) {
                console.log('KakaoMapScript: SDK가 이미 로드되어 있습니다.');
                setIsLoaded(true);
                resolve();
                return;
            }

            // 이미 스크립트가 추가되어 있는지 확인
            const existingScript = document.getElementById('kakao-maps-sdk-dynamic');
            if (existingScript) {
                console.log('KakaoMapScript: 스크립트 태그가 이미 있습니다. 로딩 대기 중...');
                // 이미 스크립트 태그가 있는 경우, kakao.maps.load 대기
                const waitForKakao = setInterval(() => {
                    if (
                        window.kakao &&
                        window.kakao.maps &&
                        window.kakao.maps.services &&
                        window.kakao.maps.services.Places &&
                        window.kakao.maps.services.Status
                    ) {
                        clearInterval(waitForKakao);
                        console.log('KakaoMapScript: 기존 스크립트에서 SDK 로드 완료');
                        setIsLoaded(true);
                        resolve();
                    }
                }, 300);
                return;
            }

            const kakaoApiKey = process.env.NEXT_PUBLIC_KAKAO_API_KEY;
            if (!kakaoApiKey) {
                const error = new Error('카카오 API 키가 설정되지 않았습니다.');
                console.error('KakaoMapScript:', error);
                reject(error);
                return;
            }

            // 새로 스크립트 생성 및 추가
            const script = document.createElement('script');
            script.id = 'kakao-maps-sdk-dynamic';
            script.async = true;
            script.src = `//dapi.kakao.com/v2/maps/sdk.js?appkey=${kakaoApiKey}&libraries=services,clusterer,drawing&autoload=false`;

            script.onload = () => {
                console.log('KakaoMapScript: 스크립트 로드 완료, kakao.maps.load 호출');
                window.kakao.maps.load(() => {
                    if (
                        window.kakao.maps.services &&
                        window.kakao.maps.services.Places &&
                        window.kakao.maps.services.Status
                    ) {
                        console.log('KakaoMapScript: Services 라이브러리 로드 성공');
                        setIsLoaded(true);
                        resolve();
                    } else {
                        const error = new Error('Services 라이브러리 로드 실패');
                        console.error('KakaoMapScript:', error);
                        reject(error);
                    }
                });
            };

            script.onerror = () => {
                const error = new Error('카카오맵 스크립트 로드 실패');
                console.error('KakaoMapScript:', error);
                reject(error);
            };

            document.head.appendChild(script);
            console.log('KakaoMapScript: 동적 스크립트 태그 추가됨');
        });
    };

    useEffect(() => {
        loadKakaoMapAPI()
            .then(() => {
                console.log('KakaoMapScript: 카카오맵 API 초기화 완료');
            })
            .catch((error) => {
                console.error('KakaoMapScript: 카카오맵 API 로드 실패:', error);
            });

        // cleanup
        return () => {
            console.log('KakaoMapScript: 컴포넌트 언마운트');
        };
    }, []);

    return null; // 이 컴포넌트는 UI를 렌더링하지 않습니다.
}