import BottomNav from "@/components/common/bottomNav";
import { Noto_Sans_KR } from "next/font/google";
import "./globals.css";
import "./custom.css";
import type { Metadata } from "next";
import Script from "next/script";
import KakaoMapScript from "@/components/common/KakaoMapScript";

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
    const kakaoApiKey = process.env.NEXT_PUBLIC_KAKAO_API_KEY;

    return (
        <html lang="ko" className="">
        <body className={`${noto.className} antialiased`}>
        {/* 카카오 맵 API 스크립트 추가 - 이벤트 핸들러 제거 */}
        {kakaoApiKey && (
            <Script
                id="kakao-maps-sdk"
                src={`//dapi.kakao.com/v2/maps/sdk.js?appkey=${kakaoApiKey}&libraries=services,clusterer,drawing`}
                strategy="beforeInteractive"
            />
        )}
        <KakaoMapScript />

        <div className="w-full h-[100dvh] bg-[#dfdfdf]">
            <div
                id="layout-wrapper"
                className="relative h-full lg:w-[95%] w-full lg:max-w-[450px] flex flex-col items-center justify-between font-custom duration-500 layout-load preload lg:ml-[50%] mx-auto"
            >
                <main className="text-[#383838] relative main-wrapper w-full h-[calc(100%-68px)]  shadow-xl bg-[#f5f6f7] pt-[48px]">
                    {children}
                </main>
                <BottomNav />
                <div className="absolute top-[42%] left-[-100%] animate-bounce">
                    <img className="w-[200px]" src="/logo.png" alt="logo" />
                </div>
            </div>
        </div>
        </body>
        </html>
    );
}