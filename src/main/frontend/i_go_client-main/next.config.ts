import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true, // 기존 설정 유지 또는 필요에 따라 설정

  // eslint 설정
  eslint: {
    // 경고: 프로덕션 빌드 중 ESLint 오류를 무시합니다.
    // 이는 임시방편으로만 사용하고, 가능한 한 빨리 오류를 해결해야 합니다.
    ignoreDuringBuilds: true,
  },

  // devIndicators 설정 추가
  devIndicators: false, // Dev Tools UI 완전히 비활성화
};

export default nextConfig;
