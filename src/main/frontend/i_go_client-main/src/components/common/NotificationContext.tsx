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

    // 루틴 알림을 위한 상태
    const [routineNotificationOpen, setRoutineNotificationOpen] = useState<boolean>(false);
    const [routineNotificationData, setRoutineNotificationData] = useState<{
        name: string;
        subtitle?: string;
    } | null>(null);

    // 마지막으로 체크한 시간을 저장
    const lastCheckRef = useRef<Date>(new Date());

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

        // 10초마다 스케줄 체크 (개발용, 실제는 1분 정도가 적절)
        const intervalId = setInterval(checkSchedules, 10000);
        checkSchedules(); // 초기 로드 시 한번 체크

        return () => clearInterval(intervalId);
    }, [checkedItems]);

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