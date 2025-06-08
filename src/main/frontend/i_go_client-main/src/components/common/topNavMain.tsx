"use client";

import Link from "next/link";
import { useState, useEffect } from "react";

interface NavBarProps {
  link: string;
}

const NavBar = ({ link }: NavBarProps) => {
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  // 페이지 로드시 토큰 확인
  useEffect(() => {
    // 쿠키에서 토큰 확인
    const hasToken = document.cookie.includes("access_token");
    setIsAuthenticated(hasToken);
  }, []);

  // 인증 상태에 따라 네비게이션 처리
  const handleNavClick = (
    e: React.MouseEvent<HTMLAnchorElement>,
    path: string
  ) => {
    if (!isAuthenticated) {
      e.preventDefault(); // 인증되지 않은 경우 기본 동작 방지
      return false;
    }
  };

  return (
    <div className="absolute top-0 left-0 bg-[#fff] flex items-center justify-between w-full py-[14px] max-h-[50px] bg-[#fff] shadow-sm px-[20px]">
      <Link
        href="/"
        className={`hover:opacity-[0.75] btn-history-back ${
          !isAuthenticated ? "pointer-events-none" : ""
        }`}
        onClick={(e) => handleNavClick(e, "/")}
      >
        <img src="/logo.png" className="w-[50px]" />
      </Link>

      <a
        href={link}
        className={!isAuthenticated ? "pointer-events-none" : ""}
        onClick={(e) => handleNavClick(e, link)}
      >
        <img src="/icon/setting.svg" />
      </a>
    </div>
  );
};

export default NavBar;
