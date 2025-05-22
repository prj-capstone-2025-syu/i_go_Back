"use client";
import "/src/app/custom.css";

export default function Mypage() {
  return (
    <div>
      <style jsx global>{`
        .top-nav-wrapper {
          display: none !important;
        }
        .bottom-nav-warpper {
          display: none !important;
        }
        .main-wrapper {
          height: 100% !important;
          padding: 0px !important;
        }
      `}</style>
      <div className="splash-wapper">
        <div className="absolute bg-[#fff] z-[999] w-full h-full flex items-center justify-center">
          <img className="w-[200px]" src="/logo.png"></img>
        </div>
      </div>
    </div>
  );
}
