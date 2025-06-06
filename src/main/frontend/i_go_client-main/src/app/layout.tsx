import BottomNav from "@/components/common/bottomNav";
import { Noto_Sans_KR } from "next/font/google";
import "./globals.css";
import "./custom.css";
import type { Metadata } from "next"; // Metadata 타입 임포트

import ClientLayout from '@/components/layout/ClientLayout';

// Metadata 객체 export
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
        <body className={`${noto.className} antialiased`}>
            <ClientLayout>
                {children}
            </ClientLayout>
        </body>
        </html>
    );
}