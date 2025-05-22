"use client";
import NavBarMain from "@/components/common/topNavMain";
import Link from "next/link";
import {useEffect, useState} from "react";
import {getCurrentUser} from "@/api/userApi";

export default function Home() {
    const [user, setUser] = useState({
        id: null,
        email: "",
        nickname: "",
        profileImageUrl: "",
        role: ""
    });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        const fetchUserData = async () => {
            try {
                setLoading(true);
                const userData = await getCurrentUser();
                setUser(userData);
            } catch (err) {
                console.error("사용자 정보 가져오기 실패:", err);
                setError("사용자 정보를 불러오는데 실패했습니다");
            } finally {
                setLoading(false);
            }
        };

        fetchUserData();
    }, []);

    return (
        <div className="flex flex-col w-full h-full">
            <NavBarMain link="setting"></NavBarMain>
            <div className="w-full max-h-full overflow-y-auto">
                <div className="flex flex-col items-center justify-start p-[20px] w-full h-auto gap-y-[15px]">
                    {loading ? (
                        <div className="flex justify-center items-center w-full p-[20px]">
                            <p>로딩 중...</p>
                        </div>
                    ) : error ? (
                        <div className="flex justify-center items-center w-full p-[20px]">
                            <p className="text-red-500">{error}</p>
                        </div>
                    ) : (
                        <Link
                            className="hover:opacity-[0.7] border-[1px] p-[20px] border-[#dfdfdf] rounded-[6px] bg-[#fff] w-full shadow-sm flex items-center jutify-start gap-x-[12px]"
                            href="mypage-edit"
                        >
                            <div
                                className="w-[80px] aspect-square rounded-full bg-[#dfdfdf]"
                                style={user.profileImageUrl ? {
                                    backgroundImage: `url(${user.profileImageUrl})`,
                                    backgroundSize: 'cover',
                                    backgroundPosition: 'center'
                                } : {}}
                            ></div>
                            <div className="w-full flex flex-col gap-y-[1px]">
                <span className="text-[18px] font-[500] text-[#01274F] leading-[130%] line-clamp-1">
                  {user.nickname || "사용자"}
                </span>
                                <span
                                    className="text-[15px] font-[500] text-[#01274F] leading-[150%] line-clamp-1 tracking-[-0.8px]">
                  나의 한마디 : 아자아자 화이팅!!
                </span>
                                <span
                                    className="text-[15px] font-[500] text-[#01274F] leading-[130%] line-clamp-1 tracking-[-0.8px]">
                  {user.email || "이메일 정보 없음"}
                </span>
                            </div>
                            <img className="w-[24px]" src="/icon/edit.svg"></img>
                        </Link>
                    )}

                    <Link
                        className="hover:opacity-[0.7] border-[1px] p-[20px] border-[#dfdfdf] rounded-[6px] bg-[#fff] w-full shadow-sm flex justify-between items-center"
                        href="/mypage/routine"
                    >
                        <p className="text-[18px] font-[500] text-[#01274F] leading-[130%] line-clamp-1">
                            나의 루틴 설정하기
                        </p>
                        <img className="w-[24px]" src="/icon/setting.svg"></img>
                    </Link>
                    {/* 알람 목록 */}
                    <div className="flex justify-between items-end w-full mb-[0px] px-[5px]">
                        <p className="text-[#01274F] text-[19px] font-[700] tracking-[-0.4px]">
                            최근 알람
                        </p>
                    </div>
                    <div
                        className="w-full bg-[#fff] p-[15px] rounded-[6px] shadow-[0px_0px_5px_rgba(0,0,0,0.2)] mb-[22px]">
                        <Link
                            href="#"
                            className="flex items-start  flex-col  justify-between gap-x-[10px] gap-y-[4px] w-full bg-[#fff] border-b-[1px] border-[#dfdfdf] py-[7px] px-[10px] lg:px-[20px] hover:bg-[#dfdfdf] last:!border-[0px] first:!pt-[0px] last:!pb-[0px]"
                        >
                            <p className="w-full text-[#383838] text-[13px] line-clamp-3 pr-[15px]">
                                제목은 3줄까지만 표시됩니다.
                            </p>
                            <div className="flex flex-row gap-x-[3px] md:gap-x-[15px] xl:gap-x-[65px]">
                                <p className="w-full text-[#777] text-[13px] pr-[5px] min-w-[68px] text-left md:text-center whitespace-nowrap">
                                    YYYY-MM-DD TT:MM
                                </p>
                            </div>
                        </Link>
                        <Link
                            href="#"
                            className="flex items-start  flex-col  justify-between gap-x-[10px] gap-y-[4px] w-full bg-[#fff] border-b-[1px] border-[#dfdfdf] py-[7px] px-[10px] lg:px-[20px] hover:bg-[#dfdfdf] last:!border-[0px] first:!pt-[0px] last:!pb-[0px]"
                        >
                            <p className="w-full text-[#383838] text-[13px] line-clamp-3 pr-[15px]">
                                제목은 3줄까지만 표시됩니다. 제목은 3줄까지만 표시됩니다.제목은
                                3줄까지만 표시됩니다.제목은 3줄까지만 표시됩니다.제목은
                                3줄까지만 표시됩니다.
                            </p>
                            <div className="flex flex-row gap-x-[3px] md:gap-x-[15px] xl:gap-x-[65px]">
                                <p className="w-full text-[#777] text-[13px] pr-[5px] min-w-[68px] text-left md:text-center whitespace-nowrap">
                                    YYYY-MM-DD TT:MM
                                </p>
                            </div>
                        </Link>
                        <Link
                            href="#"
                            className="flex items-start  flex-col  justify-between gap-x-[10px] gap-y-[4px] w-full bg-[#fff] border-b-[1px] border-[#dfdfdf] py-[7px] px-[10px] lg:px-[20px] hover:bg-[#dfdfdf] last:!border-[0px] first:!pt-[0px] last:!pb-[0px]"
                        >
                            <p className="w-full text-[#383838] text-[13px] line-clamp-3 pr-[15px]">
                                제목은 3줄까지만 표시됩니다.
                            </p>
                            <div className="flex flex-row gap-x-[3px] md:gap-x-[15px] xl:gap-x-[65px]">
                                <p className="w-full text-[#777] text-[13px] pr-[5px] min-w-[68px] text-left md:text-center whitespace-nowrap">
                                    YYYY-MM-DD TT:MM
                                </p>
                            </div>
                        </Link>
                        <Link
                            href="#"
                            className="flex items-start  flex-col  justify-between gap-x-[10px] gap-y-[4px] w-full bg-[#fff] border-b-[1px] border-[#dfdfdf] py-[7px] px-[10px] lg:px-[20px] hover:bg-[#dfdfdf] last:!border-[0px] first:!pt-[0px] last:!pb-[0px]"
                        >
                            <p className="w-full text-[#383838] text-[13px] line-clamp-3 pr-[15px]">
                                제목은 3줄까지만 표시됩니다.
                            </p>
                            <div className="flex flex-row gap-x-[3px] md:gap-x-[15px] xl:gap-x-[65px]">
                                <p className="w-full text-[#777] text-[13px] pr-[5px] min-w-[68px] text-left md:text-center whitespace-nowrap">
                                    YYYY-MM-DD TT:MM
                                </p>
                            </div>
                        </Link>
                        <Link
                            href="#"
                            className="flex items-start  flex-col  justify-between gap-x-[10px] gap-y-[4px] w-full bg-[#fff] border-b-[1px] border-[#dfdfdf] py-[7px] px-[10px] lg:px-[20px] hover:bg-[#dfdfdf] last:!border-[0px] first:!pt-[0px] last:!pb-[0px]"
                        >
                            <p className="w-full text-[#383838] text-[13px] line-clamp-3 pr-[15px]">
                                제목은 3줄까지만 표시됩니다.
                            </p>
                            <div className="flex flex-row gap-x-[3px] md:gap-x-[15px] xl:gap-x-[65px]">
                                <p className="w-full text-[#777] text-[13px] pr-[5px] min-w-[68px] text-left md:text-center whitespace-nowrap">
                                    YYYY-MM-DD TT:MM
                                </p>
                            </div>
                        </Link>
                        <Link
                            href="#"
                            className="flex items-start  flex-col  justify-between gap-x-[10px] gap-y-[4px] w-full bg-[#fff] border-b-[1px] border-[#dfdfdf] py-[7px] px-[10px] lg:px-[20px] hover:bg-[#dfdfdf] last:!border-[0px] first:!pt-[0px] last:!pb-[0px]"
                        >
                            <p className="w-full text-[#383838] text-[13px] line-clamp-3 pr-[15px]">
                                제목은 3줄까지만 표시됩니다.
                            </p>
                            <div className="flex flex-row gap-x-[3px] md:gap-x-[15px] xl:gap-x-[65px]">
                                <p className="w-full text-[#777] text-[13px] pr-[5px] min-w-[68px] text-left md:text-center whitespace-nowrap">
                                    YYYY-MM-DD TT:MM
                                </p>
                            </div>
                        </Link>
                        <Link
                            href="#"
                            className="flex items-start  flex-col  justify-between gap-x-[10px] gap-y-[4px] w-full bg-[#fff] border-b-[1px] border-[#dfdfdf] py-[7px] px-[10px] lg:px-[20px] hover:bg-[#dfdfdf] last:!border-[0px] first:!pt-[0px] last:!pb-[0px]"
                        >
                            <p className="w-full text-[#383838] text-[13px] line-clamp-3 pr-[15px]">
                                제목은 3줄까지만 표시됩니다.
                            </p>
                            <div className="flex flex-row gap-x-[3px] md:gap-x-[15px] xl:gap-x-[65px]">
                                <p className="w-full text-[#777] text-[13px] pr-[5px] min-w-[68px] text-left md:text-center whitespace-nowrap">
                                    YYYY-MM-DD TT:MM
                                </p>
                            </div>
                        </Link>
                    </div>
                </div>
            </div>
        </div>
    );
}
