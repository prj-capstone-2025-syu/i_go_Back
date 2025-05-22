"use client";

import Link from "next/link";
interface NavBarProps {
  link: string;
}

const NavBar = ({ link }: NavBarProps) => {
  return (
    <div className="absolute top-0 left-0 bg-[#fff] flex items-center justify-between w-full py-[14px] max-h-[50px] bg-[#fff] shadow-sm px-[20px]">
      <Link href="/" className="hover:opacity-[0.75] btn-history-back">
        <img src="/logo.png" className="w-[50px]" />
      </Link>

      <a href={link}>
        <img src="/icon/setting.svg" />
      </a>
    </div>
  );
};

export default NavBar;
