import BottomNav from "@/components/common/bottomNav";
import { Noto_Sans_KR } from "next/font/google";
import "./globals.css";
import "./custom.css";
import type { Metadata } from "next";
import ClientLayout from '@/components/layout/ClientLayout';

export const metadata: Metadata = {
    title: "아이고 - AI 지각방지 솔루션",
    description: "아이고 - Ai 지각방지 솔루션",
    icons: {
        icon: "/imgs/favi-icon.png",
        shortcut: "/imgs/favi-icon.png",
        apple: "/imgs/favi-icon.png",
    },
};

const noto = Noto_Sans_KR({
    subsets: ["latin"],
});

export default function RootLayout({
                                       children,
                                   }: Readonly<{
    children: React.ReactNode;
}>) {
    return (
        <html lang="ko" className="">
        <head>
            {/* 카카오맵 SDK는 KakaoMapScript 컴포넌트에서 동적으로 로드됩니다 */}
        </head>
        <body className={`${noto.className} antialiased`}>
        <ClientLayout>
            {children}
        </ClientLayout>
        </body>
        </html>
    );
}