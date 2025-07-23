"use client";

import React, { createContext, useContext, useState, useEffect, useRef } from 'react';
import { getUpcomingSchedules, getLatestInProgressSchedule } from '@/api/scheduleApi';
import { getRoutineById } from '@/api/routineApi';

interface NotificationContextType {
    isOpen: boolean;
    notificationData: any | null;
    showNotification: (data: any) => void;
    hideNotification: () => void;
    routineNotificationOpen: boolean;
    routineNotificationData: {
        name: string;
        subtitle?: string;
    } | null;
    hideRoutineNotification: () => void;
}

const NotificationContext = createContext<NotificationContextType | undefined>(undefined);

export const NotificationProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [isOpen, setIsOpen] = useState<boolean>(false);
    const [notificationData, setNotificationData] = useState<any | null>(null);
    const [checkedRoutines, setCheckedRoutines] = useState<string[]>([]);
    const [checkedItems, setCheckedItems] = useState<Set<string>>(new Set());
    // 토큰 존재 여부 확인을 위한 상태
    const [hasToken, setHasToken] = useState<boolean>(false);

    // 루틴 알림을 위한 상태
    const [routineNotificationOpen, setRoutineNotificationOpen] = useState<boolean>(false);
    const [routineNotificationData, setRoutineNotificationData] = useState<{
        name: string;
        subtitle?: string;
    } | null>(null);

    // 마지막으로 체크한 시간을 저장
    const lastCheckRef = useRef<Date>(new Date());

    // 토큰 체크 로직
    useEffect(() => {
        const checkToken = () => {
            // 로컬 스토리지 또는 쿠키에서 access_token 확인
            const token = localStorage.getItem('access_token') ||
                          document.cookie.split('; ').find(row => row.startsWith('access_token='))?.split('=')[1];
            setHasToken(!!token);
        };

        checkToken();

        // 스토리지/쿠키 변경 감지를 위한 이벤트 리스너
        window.addEventListener('storage', checkToken);
        return () => window.removeEventListener('storage', checkToken);
    }, []);

    const showNotification = (data: any) => {
        setNotificationData(data);
        setIsOpen(true);
    };

    const hideNotification = () => {
        setIsOpen(false);
        setTimeout(() => setNotificationData(null), 300);
    };

    const showRoutineNotification = (name: string, subtitle?: string) => {
        setRoutineNotificationData({ name, subtitle });
        setRoutineNotificationOpen(true);
    };

    const hideRoutineNotification = () => {
        setRoutineNotificationOpen(false);
        setTimeout(() => setRoutineNotificationData(null), 300);
    };

    // 루틴 알림 체크 로직
    useEffect(() => {
        // 토큰이 없으면 알림 체크를 실행하지 않음
        if (!hasToken) {
            console.log('토큰이 없어 알림 체크를 건너뜁니다.');
            return;
        }

        const checkSchedules = async () => {
            try {
                const inProgressSchedule = await getLatestInProgressSchedule();

                if (inProgressSchedule && inProgressSchedule.routineId) {
                    const routineDetails = await getRoutineById(inProgressSchedule.routineId);

                    if (routineDetails) {
                        const now = new Date();
                        const scheduleStartTime = new Date(inProgressSchedule.startTime);

                        // 루틴 첫 시작 알림 (한 번만)
                        const scheduleKey = `schedule-${inProgressSchedule.id}`;
                        if (!checkedItems.has(scheduleKey) &&
                            now.getTime() - scheduleStartTime.getTime() < 5 * 60000) { // 5분 이내 시작된 경우만
                            showRoutineNotification(routineDetails.name);
                            setCheckedItems(prev => new Set(prev).add(scheduleKey));
                        }

                        // 각 루틴 아이템의 시작 시간 계산 및 알림
                        let accumulatedMinutes = 0;

                        for (const item of routineDetails.items) {
                            const itemStartTime = new Date(scheduleStartTime.getTime() + accumulatedMinutes * 60000);
                            const itemKey = `item-${inProgressSchedule.id}-${item.name}-${itemStartTime.getTime()}`;

                            // 지난 체크 이후 시작된 아이템이고 아직 알림을 보내지 않은 경우
                            if (itemStartTime > lastCheckRef.current &&
                                itemStartTime <= now &&
                                !checkedItems.has(itemKey)) {
                                showRoutineNotification(item.name, "시작 시간입니다.");
                                setCheckedItems(prev => new Set(prev).add(itemKey));
                                break; // 가장 최근 아이템만 알림
                            }

                            accumulatedMinutes += item.durationMinutes;
                        }
                    }
                }

                // 마지막 체크 시간 업데이트
                lastCheckRef.current = new Date();
            } catch (error) {
                console.error('알림 체크 중 오류 발생:', error);
            }
        };

        // 60초마다 스케줄 체크
        const intervalId = setInterval(checkSchedules, 60000);
        checkSchedules(); // 초기 로드 시 한번 체크

        console.log('토큰이 확인되어 알림 체크를 시작합니다.');

        return () => clearInterval(intervalId);
    }, [checkedItems, hasToken]); // hasToken 의존성 추가

    // Android FCM 토큰 처리를 위한 함수
    const sendAndroidFCMTokenToServer = async (token: string) => {
        try {
            const accessToken = localStorage.getItem('access_token') ||
                              document.cookie.split('; ').find(row => row.startsWith('access_token='))?.split('=')[1];

            if (!accessToken) {
                console.log('인증 토큰이 없어 FCM 토큰 전송을 건너뜁니다.');
                return;
            }

            // axios를 사용하여 API 호출 (기존 구조 활용)
            const api = (await import('@/api/axiosConfig')).default;

            const response = await api.post('/user/fcm-token', {
                fcmToken: token,
                platform: 'android'
            });

            if (response.status === 200) {
                console.log('Android FCM 토큰이 서버로 전송되었습니다:', token);
                console.log('서버 응답:', response.data);
            }
        } catch (error) {
            console.error('FCM 토큰 전송 중 오류:', error);

            // TypeScript 타입 가드를 사용한 안전한 error 처리
            if (error && typeof error === 'object' && 'response' in error) {
                const axiosError = error as { response: { data: any; status: number } };
                console.error('서버 오류 응답:', axiosError.response.data);
                console.error('오류 상태 코드:', axiosError.response.status);
            }
        }
    };

    return (
        <NotificationContext.Provider
            value={{
                isOpen,
                notificationData,
                showNotification,
                hideNotification,
                routineNotificationOpen,
                routineNotificationData,
                hideRoutineNotification
            }}
        >
            {children}
        </NotificationContext.Provider>
    );
};

export const useNotification = () => {
    const context = useContext(NotificationContext);
    if (context === undefined) {
        throw new Error('useNotification must be used within a NotificationProvider');
    }
    return context;
};