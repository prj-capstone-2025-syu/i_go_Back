"use client";

import React, { FC } from 'react';
import BottomNav from "@/components/common/bottomNav";
import { NotificationProvider } from '@/components/common/NotificationContext';
import GlobalNotifications from '@/components/common/GlobalNotifications';

interface ClientLayoutProps {
    children: React.ReactNode;
}

const ClientLayout: FC<ClientLayoutProps> = ({ children }) => {
    return (
        <NotificationProvider>
            <div className="w-full h-[100dvh] bg-[#dfdfdf]">
                <div
                    id="layout-wrapper"
                    className="relative h-full lg:w-[95%] w-full lg:max-w-[450px] flex flex-col items-center justify-between font-custom duration-500 layout-load preload lg:ml-[50%] mx-auto"
                >
                    <main className="text-[#383838] relative main-wrapper w-full h-[calc(100%-68px)] shadow-xl bg-[#f5f6f7] pt-[48px]">
                        {children}
                    </main>
                    <BottomNav />
                    <div className="absolute top-[42%] left-[-100%] animate-bounce">
                        <img className="w-[200px]" src="/logo.png" alt="로고" />
                    </div>
                </div>
            </div>
            <GlobalNotifications />
        </NotificationProvider>
    );
};

export default ClientLayout;
