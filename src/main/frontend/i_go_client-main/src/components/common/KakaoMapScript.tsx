'use client';

import { useEffect } from 'react';

export default function KakaoMapScript() {
    useEffect(() => {
        const checkKakaoAllServices = () => {
            if (
                window.kakao &&
                window.kakao.maps &&
                window.kakao.maps.services &&
                window.kakao.maps.services.Places && // Places 생성자 확인
                window.kakao.maps.services.Status    // Status 객체 확인
            ) {
                console.log('KakaoMapScript: Kakao Maps SDK (including Places and Status services) 로드 성공');
                return true;
            }
            // 상세 누락 정보 로깅
            let missing = "KakaoMapScript Polling: ";
            if (typeof window.kakao === 'undefined') missing += "window.kakao 객체가 없습니다. ";
            else if (typeof window.kakao.maps === 'undefined') missing += "window.kakao.maps 객체가 없습니다. ";
            else if (typeof window.kakao.maps.services === 'undefined') missing += "window.kakao.maps.services 객체가 없습니다. ";
            else {
                if (typeof window.kakao.maps.services.Places === 'undefined') missing += "window.kakao.maps.services.Places 객체가 없습니다. ";
                if (typeof window.kakao.maps.services.Status === 'undefined') missing += "window.kakao.maps.services.Status 객체가 없습니다. ";
            }
            // 실제로 누락된 정보가 있을 때만 로그 출력 (초기 폴링 시점 등)
            if (missing !== "KakaoMapScript Polling: ") {
                console.log(missing.trim());
            }
            return false;
        };

        // 이미 로드되었는지 즉시 확인
        if (checkKakaoAllServices()) {
            return;
        }

        console.log('KakaoMapScript: Kakao Maps SDK (including services) 로드 대기 중... 폴링 시작.');
        const interval = setInterval(() => {
            if (checkKakaoAllServices()) {
                clearInterval(interval);
                // 성공 로그는 checkKakaoAllServices 내부에서 처리
            }
        }, 500); // 폴링 간격은 500ms로 유지하거나 조절 가능

        return () => {
            clearInterval(interval);
            console.log('KakaoMapScript: SDK 폴링 인터벌 정리됨.');
        };
    }, []);

    return null; // 이 컴포넌트는 UI를 렌더링하지 않습니다.
}