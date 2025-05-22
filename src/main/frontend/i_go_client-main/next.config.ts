import type { NextConfig } from "next";

const nextConfig = {
  reactStrictMode: true, // 기존 설정 유지 또는 필요에 따라 설정
  // 여기에 eslint 설정을 추가합니다.
  eslint: {
    // 경고: 프로덕션 빌드 중 ESLint 오류를 무시합니다.
    // 이는 임시방편으로만 사용하고, 가능한 한 빨리 오류를 해결해야 합니다.
    ignoreDuringBuilds: true,
  },
};

export default nextConfig;
