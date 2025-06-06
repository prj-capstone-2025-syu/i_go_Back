"use client";

import React, { FC } from 'react';
import { useNotification } from '@/components/common/NotificationContext';
import NotificationModal from '@/components/common/NotificationModal';

const GlobalNotifications: FC = () => {
    const {
        routineNotificationOpen,
        routineNotificationData,
        hideRoutineNotification
    } = useNotification();

    return (
        <NotificationModal
            isOpen={routineNotificationOpen}
            title={routineNotificationData?.name || ''}
            subtitle={routineNotificationData?.subtitle}
            onClose={hideRoutineNotification}
            actionText="확인"
        />
    );
};

export default GlobalNotifications;