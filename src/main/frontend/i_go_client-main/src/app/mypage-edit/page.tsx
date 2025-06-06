"use client"; // 클라이언트 컴포넌트로 명시

import NavBarMain from "@/components/common/topNavMain";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation"; // next/navigation에서 useRouter 임포트
import { getCurrentUser, logout, deleteAccount } from "@/api/userApi"; // userApi 사용
import LogoutPopup from "@/components/common/LogoutPopup";
import WithdrawPopup from "@/components/common/WithdrawPopup";

export default function MypageEdit() {
  const [user, setUser] = useState({
    email: "",
    nickname: "",
    profileImageUrl: "", // 프로필 이미지 URL도 상태에 추가 (필요시 사용)
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null); // 에러 상태 타입 명시
  const [showLogoutPopup, setShowLogoutPopup] = useState(false);
  const [showWithdrawPopup, setShowWithdrawPopup] = useState(false);
  const router = useRouter();

  useEffect(() => {
    const fetchUserData = async () => {
      try {
        setLoading(true);
        const userData = await getCurrentUser();
        if (userData) {
          setUser({
            email: userData.email || "이메일 정보 없음",
            nickname: userData.nickname || "사용자",
            profileImageUrl: userData.profileImageUrl || "",
          });
        } else {
          setError("사용자 정보를 가져올 수 없습니다.");
        }
      } catch (err: any) { // 에러 타입 명시
        console.error("사용자 정보 가져오기 실패:", err);
        setError(
            err.response?.data?.message ||
            "사용자 정보를 불러오는데 실패했습니다."
        );
        // 인증되지 않은 사용자의 경우 로그인 페이지로 리디렉션 고려
        if (err.response?.status === 401) {
          router.push("/login");
        }
      } finally {
        setLoading(false);
      }
    };

    fetchUserData();
  }, [router]); // router를 의존성 배열에 추가 (페이지 이동 후 재요청 방지 목적은 아님)

  // 로그아웃 팝업 표시
  const openLogoutPopup = () => {
    setShowLogoutPopup(true);
  };

  // 회원탈퇴 팝업 표시
  const openWithdrawPopup = () => {
    setShowWithdrawPopup(true);
  };

  // 팝업 닫기
  const closePopups = () => {
    setShowLogoutPopup(false);
    setShowWithdrawPopup(false);
  };

  const handleLogout = async () => {
    try {
      await logout();
      // 쿠키제거
      router.push("/"); // 홈으로 이동 또는 로그인 페이지로 이동
      // window.location.href = '/'; // 또는 전체 새로고침
    } catch (err) {
      console.error("로그아웃 실패:", err);
      alert("로그아웃 중 오류가 발생했습니다.");
    } finally {
      closePopups();
    }
  };

  const handleDeleteAccount = async () => {
    try {
      await deleteAccount();
      router.push("/"); // 홈으로 이동 또는 안내 페이지로 이동
      // window.location.href = '/'; // 또는 전체 새로고침
    } catch (err) {
      console.error("회원탈퇴 실패:", err);
      alert("회원탈퇴 중 오류가 발생했습니다.");
    } finally {
      closePopups();
    }
  };

  if (loading) {
    return (
        <div className="flex flex-col w-full h-full">
          <NavBarMain link="setting"></NavBarMain>
          <div className="flex justify-center items-center w-full h-full p-[20px]">
            <p>로딩 중...</p>
          </div>
        </div>
    );
  }

  if (error) {
    return (
        <div className="flex flex-col w-full h-full">
          <NavBarMain link="setting"></NavBarMain>
          <div className="flex justify-center items-center w-full h-full p-[20px]">
            <p className="text-red-500">{error}</p>
            {/* 로그인 페이지로 이동하는 버튼 등을 추가할 수 있습니다. */}
            <button onClick={() => router.push('/login')} className="mt-4 p-2 bg-blue-500 text-white rounded">
              로그인 페이지로 이동
            </button>
          </div>
        </div>
    );
  }

  return (
      <div className="flex flex-col w-full h-full">
        <NavBarMain link="setting"></NavBarMain>
        <div className="w-full max-h-full overflow-y-auto">
          <div className="flex flex-col items-center justify-start p-[20px] w-full h-auto gap-y-[15px]">
            {/* 사용자 정보 표시 */}
            <div
                className="border-[1px] p-[20px] border-[#dfdfdf] rounded-[6px] bg-[#fff] w-full shadow-sm flex items-center justify-start gap-x-[12px]"
            >
              <div
                  className="w-[80px] aspect-square rounded-full bg-[#dfdfdf] bg-cover bg-center"
                  style={user.profileImageUrl ? { backgroundImage: `url(${user.profileImageUrl})` } : {}}
              ></div>
              <div className="w-full flex flex-col gap-y-[1px]">
              <span className="text-[18px] font-[500] text-[#01274F] leading-[130%] line-clamp-1">
                {user.nickname}
              </span>
                <span className="text-[15px] font-[500] text-[#01274F] leading-[150%] line-clamp-1 tracking-[-0.8px]">
                나의 한마디 : 아자아자 화이팅!! {/* 이 부분은 User DTO에 필드 추가 필요 */}
              </span>
                <span className="text-[15px] font-[500] text-[#01274F] leading-[130%] line-clamp-1 tracking-[-0.8px]">
                {user.email}
              </span>
              </div>
            </div>

            {/* IGO 설정 링크 - 기존 유지 */}
            <Link
                className="hover:opacity-[0.7] border-[1px] p-[20px] border-[#dfdfdf] rounded-[6px] bg-[#fff] w-full shadow-sm flex justify-between items-center"
                href="/setting" // 실제 설정 페이지 경로로 수정 필요
            >
              <p className="text-[18px] font-[500] text-[#01274F] leading-[130%] line-clamp-1">
                IGO 설정
              </p>
              <img className="w-[24px]" src="/icon/setting.svg" alt="setting icon" />
            </Link>

            <div className="w-full flex gap-x-[15px]">
              {/* 로그아웃 버튼 */}
              <button
                  onClick={openLogoutPopup}
                  className="hover:opacity-[0.7] border-[1px] p-[20px] border-[#dfdfdf] rounded-[6px] bg-[#fff] w-full shadow-sm flex justify-center items-center"
              >
                <p className="text-[17px] font-[500] text-[#01274F] leading-[130%] line-clamp-1">
                  로그아웃
                </p>
              </button>
              {/* 회원탈퇴 버튼 */}
              <button
                  onClick={openWithdrawPopup}
                  className="hover:opacity-[0.7] border-[1px] p-[20px] border-[#dfdfdf] rounded-[6px] bg-[#fff] w-full shadow-sm flex justify-center items-center"
              >
                <p className="text-[17px] font-[500] text-[#ff2f01] leading-[130%] line-clamp-1">
                  회원탈퇴
                </p>
              </button>
            </div>
          </div>
        </div>

        {/* 로그아웃 팝업 */}
        <LogoutPopup
          isOpen={showLogoutPopup}
          onConfirm={handleLogout}
          onCancel={closePopups}
        />

        {/* 회원탈퇴 팝업 */}
        <WithdrawPopup
          isOpen={showWithdrawPopup}
          onConfirm={handleDeleteAccount}
          onCancel={closePopups}
        />
      </div>
  );
}
