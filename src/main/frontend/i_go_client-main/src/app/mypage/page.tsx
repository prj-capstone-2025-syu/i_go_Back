"use client";
import NavBarMain from "@/components/common/topNavMain";
import Link from "next/link";
import {useEffect, useState} from "react";
import {getCurrentUser, getRecentNotifications} from "@/api/userApi"; // getRecentNotifications 추가

// 알림 데이터 타입을 정의합니다.
interface Notification {
    id: number;
    title: string;
    body: string;
    isRead: boolean;
    createdAt: string; // ISO 8601 형식의 문자열로 가정 (예: "2024-06-02T10:30:00")
    relatedId?: number;
    notificationType?: string;
}

export default function Home() {
    const [user, setUser] = useState({
        id: null,
        email: "",
        nickname: "",
        profileImageUrl: "",
        role: ""
    });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [notifications, setNotifications] = useState<Notification[]>([]);
    const [notificationsLoading, setNotificationsLoading] = useState(true);
    const [notificationsError, setNotificationsError] = useState<string | null>(null);

    useEffect(() => {
        const fetchUserData = async () => {
            try {
                setLoading(true);
                setError(null);
                const userData = await getCurrentUser();
                setUser(userData);
            } catch (err) {
                console.error("사용자 정보 가져오기 실패:", err);
                setError("사용자 정보를 불러오는데 실패했습니다");
            } finally {
                setLoading(false);
            }
        };

        const fetchNotifications = async () => {
            try {
                setNotificationsLoading(true);
                setNotificationsError(null);
                const recentNotifications = await getRecentNotifications(7); // 최근 7개 알림
                setNotifications(recentNotifications);
            } catch (err) {
                console.error("최근 알림 가져오기 실패:", err);
                setNotificationsError("최근 알림을 불러오는데 실패했습니다.");
            } finally {
                setNotificationsLoading(false);
            }
        };

        fetchUserData();
        fetchNotifications();
    }, []);

    const formatDateTime = (dateTimeString: string) => {
        const date = new Date(dateTimeString);
        const year = date.getFullYear();
        const month = (date.getMonth() + 1).toString().padStart(2, '0');
        const day = date.getDate().toString().padStart(2, '0');
        const hours = date.getHours().toString().padStart(2, '0');
        const minutes = date.getMinutes().toString().padStart(2, '0');
        return `${year}-${month}-${day} ${hours}:${minutes}`;
    };

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
                            <img className="w-[24px]" src="/icon/edit.svg" alt="edit"/>
                        </Link>
                    )}

                    <Link
                        className="hover:opacity-[0.7] border-[1px] p-[20px] border-[#dfdfdf] rounded-[6px] bg-[#fff] w-full shadow-sm flex justify-between items-center"
                        href="/mypage/routine"
                    >
                        <p className="text-[18px] font-[500] text-[#01274F] leading-[130%] line-clamp-1">
                            나의 루틴 설정하기
                        </p>
                        <img className="w-[24px]" src="/icon/setting.svg" alt="setting"/>
                    </Link>
                    {/* 알람 목록 */}
                    <div className="flex justify-between items-end w-full mb-[0px] px-[5px]">
                        <p className="text-[#01274F] text-[19px] font-[700] tracking-[-0.4px]">
                            최근 알람
                        </p>
                    </div>
                    <div
                        className="w-full bg-[#fff] p-[15px] rounded-[6px] shadow-[0px_0px_5px_rgba(0,0,0,0.2)] mb-[22px]">
                        {notificationsLoading ? (
                            <p className="text-center p-4">알림을 불러오는 중...</p>
                        ) : notificationsError ? (
                            <p className="text-center p-4 text-red-500">{notificationsError}</p>
                        ) : notifications.length === 0 ? (
                            <p className="text-center p-4 text-[#777]">최근 알림이 없습니다.</p>
                        ) : (
                            notifications.map((notification) => (
                                <Link
                                    href="#" // 임시로 # 처리
                                    key={notification.id}
                                    className="flex items-start  flex-col  justify-between gap-x-[10px] gap-y-[4px] w-full bg-[#fff] border-b-[1px] border-[#dfdfdf] py-[7px] px-[10px] lg:px-[20px] hover:bg-[#dfdfdf] last:!border-[0px] first:!pt-[0px] last:!pb-[0px]"
                                >
                                    <p className="w-full text-[#383838] text-[13px] line-clamp-2 pr-[15px]">
                                        {notification.title || notification.body || "알림 내용 없음"}
                                    </p>
                                    <div className="flex flex-row gap-x-[3px] md:gap-x-[15px] xl:gap-x-[65px]">
                                        <p className="w-full text-[#777] text-[13px] pr-[5px] min-w-[68px] text-left md:text-center whitespace-nowrap">
                                            {formatDateTime(notification.createdAt)}
                                        </p>
                                    </div>
                                </Link>
                            ))
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}