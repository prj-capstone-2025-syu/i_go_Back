"use client";

import React, { FC } from 'react';

interface NotificationModalProps {
    isOpen: boolean;
    title: string;
    subtitle?: string;
    onClose: () => void;
    actionLink?: string;
    actionText?: string;
}

const NotificationModal: FC<NotificationModalProps> = ({
    isOpen,
    title,
    subtitle = "루틴 시간입니다.",
    onClose,
    actionText = "확인"
}) => {
    if (!isOpen) return null;

    return (
        <>
            <div className="fixed top-0 left-0 z-[99] w-full h-[100dvh] bg-[rgba(217,217,217,0.85)]"
                 onClick={onClose}></div>
            <div className="fixed left-[50%] top-[55%] translate-x-[-50%] translate-y-[-50%] w-[85%] max-w-[335px] bg-[#fff] p-[20px] z-[100] rounded-[8px] shadow-md">
                <p className="text-[18px] text-[#5C5C5C] font-[400] text-center">
                    <span className="font-[700]">{title}</span> {subtitle}
                </p>
                <div className="my-[10px] w-full h-[1px] bg-[#dfdfdf]"></div>
                <button
                    className="w-full text-[#383838] text-[16px] font-[500] tracking-[-0.8px] leading-[155%] line-clamp-1 text-center"
                    onClick={onClose}
                >
                    {actionText}
                </button>
            </div>
        </>
    );
};

export default NotificationModal;