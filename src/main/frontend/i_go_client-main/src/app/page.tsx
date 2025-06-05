"use client"; // 상태(useState)와 라우터(useRouter)를 사용하므로 클라이언트 컴포넌트로 명시
import React, { useState, useEffect } from "react";
import AOS from "aos";
import "aos/dist/aos.css";
import NavBarMain from "@/components/common/topNavMain";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { format } from "date-fns";
import { getUpcomingSchedules, getLatestInProgressSchedule } from "@/api/scheduleApi";
import { getRoutineById } from "@/api/routineApi"; // 루틴 정보를 가져오기 위한 API 함수
import { sendFCMTokenToServer } from "@/api/userApi"; // FCM 토큰 전송 함수 임포트
import { getMessaging, getToken, onMessage } from "firebase/messaging"; // Firebase 메시징 임포트
import { app } from "@/utils/firebase"; // Firebase 앱 임포트

// 타입 정의
interface ScheduleType {
  id: number;
  title: string;
  startTime: string; // ISO string
  endTime: string;   // ISO string
  location: string | null;
  memo: string | null;
  supplies: string | null;
  category: string;
  routineId: number | null;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';
}

interface RoutineInfo { // getRoutineById 응답을 위한 간략한 타입
  id: number;
  name: string;
  totalDurationMinutes?: number; // 필요에 따라 사용
}

interface RoutineItem {
  id: number;
  name: string;
  durationMinutes: number;
}

export default function Home() {
  const [keyword, setKeyword] = useState("");
  const router = useRouter();

// 중복 선언 제거 후 깔끔하게 정리
  const [upcomingSchedules, setUpcomingSchedules] = useState<ScheduleType[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [nearestSchedule, setNearestSchedule] = useState<ScheduleType | null>(null);
  const [inProgressSchedule, setInProgressSchedule] = useState<ScheduleType | null>(null);
  const [scheduleStatusInfo, setScheduleStatusInfo] = useState<{ text: string; color: string; fontWeight?: string } | null>(null);
  const [routineName, setRoutineName] = useState<string | null>(null);
  const [currentRoutineDetails, setCurrentRoutineDetails] = useState<RoutineInfo | null>(null);
  const scheduleToUse = inProgressSchedule || nearestSchedule;
  const [currentTime, setCurrentTime] = useState(new Date());

  // 페이지 로드시 토큰 확인
  useEffect(() => {
    // 쿠키에서 토큰 확인
    const hasToken = document.cookie.includes('access_token');

    setIsAuthenticated(hasToken);

    if (!hasToken) {
      // 토큰이 없으면 /greeting 페이지로 리다이렉트
      if (window.location.pathname !== "/greeting") {
        localStorage.setItem("redirectPath", window.location.pathname + window.location.search);
      }
      router.push('/greeting');
    }
  }, [router]);

  // 1초마다 현재 시간 업데이트 (컴포넌트 최상위 레벨로 이동)
  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentTime(new Date());
    }, 1000); // 1초마다 현재 시간 업데이트
    return () => clearInterval(timer);
  }, []);

  // FCM 토큰 요청 및 서버 전송 로직
  useEffect(() => {
    if (typeof window !== 'undefined' && 'Notification' in window && isAuthenticated) {
      const messaging = getMessaging(app);

      const requestPermissionAndToken = async () => {
        try {
          const permission = await Notification.requestPermission();
          if (permission === "granted") {
            console.log("Notification permission granted.");
            // Firebase 콘솔에서 가져온 VAPID 키를 사용해야 합니다.
            // 프로젝트 설정 > 클라우드 메시징 > 웹 푸시 인증서 > 웹 구성의 키 쌍
            const currentToken = await getToken(messaging, {
              vapidKey: "BK6gC7kpp7i9gv1WMQuWsW_487xmyfsXWtE0DERzOUunoCWN3fzoJ0JwP3BIL_d4pYGcjlGxhjjmD59-0UGzoug", // 여기에 실제 VAPID 키를 입력하세요.
            });
            if (currentToken) {
              console.log("FCM Token:", currentToken);
              await sendFCMTokenToServer(currentToken);
              console.log("FCM token sent to server.");

              // 포그라운드 메시지 핸들러는 등록하지만 알림은 표시하지 않음
              onMessage(messaging, (payload) => {
                // 포그라운드 메시지 수신 시 콘솔에만 기록하고 알림 표시는 하지 않음
                console.log("Foreground message received:", payload);
                // 백그라운드에서만 알림이 표시되도록 함
              });
            } else {
              console.log("No registration token available. Request permission to generate one.");
            }
          } else {
            console.log("Unable to get permission to notify.");
          }
        } catch (error) {
          console.error("An error occurred while retrieving token. ", error);
        }
      };

      requestPermissionAndToken();
    }
  }, [isAuthenticated]); // isAuthenticated 상태가 true일 때만 실행

  useEffect(() => {
    AOS.init();
  }, []);

  // 다가오는 일정 데이터 로드 - 인증 확인 후에만 실행
  useEffect(() => {
    if (isAuthenticated) {
      const fetchSchedules = async () => {
        setIsLoading(true);
        try {
          const data: ScheduleType[] = await getUpcomingSchedules();

          // API 응답이 이미 정렬되어 있지 않다면 startTime 기준으로 정렬
          const sortedSchedules = data.sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime());

          setUpcomingSchedules(sortedSchedules);
        } catch (err: any) { // error 타입을 any로 명시

          if (err.isAxiosError && err.response?.status === 401) { // Axios 에러인 경우
            router.push("/greeting");
          }
        } finally {
          setIsLoading(false);
        }
      };
      fetchSchedules();
    } else {
      setUpcomingSchedules([]); // 인증되지 않으면 빈 배열로 설정
      setIsLoading(false);
    }
  }, [isAuthenticated, router]); // isAuthenticated 상태가 변경될 때만 실행

  // 1. Set nearest schedule from upcomingSchedules
  useEffect(() => {
    if (upcomingSchedules.length > 0) {
      const nearest = upcomingSchedules[0];
      setNearestSchedule(nearest);
    } else {
      setNearestSchedule(null);
    }
  }, [upcomingSchedules]);


  useEffect(() => {
    if (nearestSchedule) {
      const now = new Date();
      const startTime = new Date(nearestSchedule.startTime);
      const diffMinutes = (startTime.getTime() - now.getTime()) / (1000 * 60);

      if (diffMinutes < 0) {
        setScheduleStatusInfo({ text: "헉! 지각입니다!!", color: "#ff2f01" }); // 빨간색
      } else if (diffMinutes <= 5) {
        setScheduleStatusInfo({ text: "곧 시작!", color: "#10B981", fontWeight: "700" }); // 녹색, 굵게
      } else if (diffMinutes <= 60) {
        setScheduleStatusInfo({ text: `약 ${Math.round(diffMinutes)}분 후`, color: "#0080FF" }); // 파란색
      } else {
        setScheduleStatusInfo({ text: `${format(startTime, "HH:mm")} 시작`, color: "#383838" }); // 기본 색상 (회색 계열)
      }
    } else {
      setScheduleStatusInfo(null);
    }
  }, [nearestSchedule]);

  // 3. Fetch routine name when nearestSchedule (and its routineId) changes
  useEffect(() => {
    if (nearestSchedule && nearestSchedule.routineId) {
      const fetchRoutineName = async () => {
        try {
          const routineData: RoutineInfo = await getRoutineById(nearestSchedule.routineId);
          setRoutineName(routineData.name);
        } catch (error: any) {
          setRoutineName(null);
        }
      };
      fetchRoutineName();
    } else {
      setRoutineName(null);
    }
  }, [nearestSchedule]);

  // 일정 데이터 로딩
  useEffect(() => {
    if (isAuthenticated) {
      setIsLoading(true);

      // 두 API를 병렬로 호출하여 데이터 로딩 최적화
      Promise.all([
        getUpcomingSchedules(3),
        getLatestInProgressSchedule()
      ])
          .then(([upcomingData, inProgressData]) => {
            setUpcomingSchedules(upcomingData || []);
            setInProgressSchedule(inProgressData); // null 또는 Schedule 객체
            setIsLoading(false);
          })
          .catch(error => {
            setIsLoading(false);
          });
    }
  }, [isAuthenticated]);

  // 가장 가까운 일정 설정
  useEffect(() => {
    if (upcomingSchedules && upcomingSchedules.length > 0) {
      setNearestSchedule(upcomingSchedules[0]);
    } else {
      setNearestSchedule(null);
    }
  }, [upcomingSchedules]);

  // 일정 상태 정보 계산
  useEffect(() => {
    // 진행 중인 일정이나 다가오는 일정이 없으면 정보 없음
    if (!nearestSchedule && !inProgressSchedule) {
      setScheduleStatusInfo(null);
      return;
    }


    // 현재 표시할 일정 (진행 중 > 다가오는 순으로 우선순위)
    const scheduleToUse = inProgressSchedule || nearestSchedule;
    const now = new Date();
    const startTime = new Date(scheduleToUse!.startTime);

    // 상태 메시지 설정
    if (scheduleToUse === inProgressSchedule) {
      // 진행 중인 일정 표시
      setScheduleStatusInfo({
        text: "진행 중",
        color: "#FF6A00",
        fontWeight: "bold"
      });
    } else {
      // 다가오는 일정 표시
      const diffMinutes = Math.floor((startTime.getTime() - now.getTime()) / (1000 * 60));

      if (diffMinutes < 0) {
        // 지각
        setScheduleStatusInfo({
          text: "헉! 지각입니다!!",
          color: "#FF3B30",
          fontWeight: "bold"
        });
      } else if (diffMinutes < 5) {
        // 곧 시작
        setScheduleStatusInfo({
          text: "곧 시작!",
          color: "#FF6A00",
          fontWeight: "bold"
        });
      } else if (diffMinutes < 60) {
        // x분 후
        setScheduleStatusInfo({
          text: `${diffMinutes}분 후`,
          color: "#007AFF"
        });
      } else {
        // HH:mm 시작
        setScheduleStatusInfo({
          text: `${format(startTime, 'HH:mm')} 시작`,
          color: "#8E8E93"
        });
      }
    }
  }, [nearestSchedule, inProgressSchedule]);

  // 루틴 이름 로드
  useEffect(() => {
    const scheduleToUse = inProgressSchedule || nearestSchedule; // scheduleToUse를 useEffect 내부에서 정의

    if (scheduleToUse && scheduleToUse.routineId) {
      getRoutineById(scheduleToUse.routineId)
          .then((data: RoutineInfo) => { // API 응답 타입을 RoutineInfo로 명시
            setRoutineName(data.name); // 기존 루틴 이름 설정 유지
            setCurrentRoutineDetails(data); // 루틴 상세 정보 설정
          })
          .catch(error => {
            setRoutineName(null);
            setCurrentRoutineDetails(null);
          });
    } else {
      setRoutineName(null);
      setCurrentRoutineDetails(null);
    }
  }, [inProgressSchedule, nearestSchedule]); // 의존성 배열은 기존과 동일하게 유지

  useEffect(() => {
    // 1분마다 현재 시간을 업데이트
    const timerId = setInterval(() => {
      setCurrentTime(new Date());
    }, 60000); // 60000ms = 1분

    // 컴포넌트가 언마운트될 때 인터벌을 정리합니다.
    return () => clearInterval(timerId);
  }, []);

  // 날짜 포맷팅 함수 - 타입 명시 추가
  const formatDateTime = (dateTimeString: string) => {
    try {
      const date = new Date(dateTimeString);
      const formatted = format(date, "yyyy-MM-dd HH:mm");
      return formatted;
    } catch (error) {
      return "날짜 정보 없음";
    }
  };

  const handleSearchSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault(); // 기본 폼 제출 동작 방지

    if (keyword.trim()) {
      const encodedKeyword = encodeURIComponent(keyword.trim());
      const chatUrl = `/chat?keyword=${encodedKeyword}`;
      // /chat 페이지로 이동하면서 keyword를 쿼리 파라미터로 전달
      router.push(chatUrl);
    }
  };

  return (
      <div className="flex flex-col w-full h-full">
        <NavBarMain link="/mypage"></NavBarMain>
        <div className="w-full max-h-full overflow-y-auto">
          <div className="flex flex-col items-center justify-start p-[20px] w-full h-auto">
            {/* 프롬프트 입력창 */}
            <form
                className="relative w-full 2xl:max-w-[781px] mb-[15px]"
                onSubmit={handleSearchSubmit}
            >
              <input
                  type="text"
                  className="bg-[#fff] !outline-none border-[1px] border-[#DFDFDF] shadow-[0px_0px_5px_rgba(0,0,0,0.2)] rounded-[6px] pr-[38px] pl-[15px] py-[12px] w-full font-[400] text-[15px] leading-[20px] text-[#383838] placeholder:!text-[#949494] focus:border-[#01274F]"
                  placeholder="아이고 AI - 무엇을 도와드릴까요?"
                  value={keyword}
                  onChange={(e) => {
                    console.log("🔍 [Search Debug] 입력값 변경:", e.target.value);
                    setKeyword(e.target.value);
                  }}
              />
              <div className="absolute flex right-[11px] !top-[50%] !translate-y-[-50%]">
                <button className="p-[4px]" type="submit">
                  <svg
                      xmlns="http://www.w3.org/2000/svg"
                      width="16"
                      height="16"
                      fill="none"
                  >
                    <path
                        fill="#01274F"
                        d="M13.267 13.984 8.883 9.6a3.673 3.673 0 0 1-1.166.675 4.125 4.125 0 0 1-1.417.242c-1.2 0-2.217-.417-3.05-1.25C2.417 8.434 2 7.428 2 6.25s.417-2.183 1.25-3.016c.833-.834 1.844-1.25 3.033-1.25 1.178 0 2.18.416 3.009 1.25.827.833 1.241 1.838 1.241 3.016A4.156 4.156 0 0 1 9.6 8.884L14 13.25l-.733.734ZM6.283 9.517c.9 0 1.667-.32 2.3-.958a3.16 3.16 0 0 0 .95-2.309c0-.9-.316-1.67-.95-2.308a3.119 3.119 0 0 0-2.3-.958c-.91 0-1.686.319-2.325.958A3.146 3.146 0 0 0 3 6.25c0 .9.32 1.67.958 2.309a3.166 3.166 0 0 0 2.325.958Z"
                    ></path>
                  </svg>
                </button>
              </div>
            </form>

            {/* 🔍 DEBUG: 현재 상태 표시 */}
            {/*{process.env.NODE_ENV === 'development' && (
                <div className="w-full bg-gray-100 p-4 mb-4 rounded border text-xs">
                  <h3 className="font-bold mb-2">🔍 DEBUG INFO</h3>
                  <p><strong>인증 상태:</strong> {isAuthenticated ? '✅ 인증됨' : '❌ 미인증'}</p>
                  <p><strong>로딩 상태:</strong> {isLoading ? '⏳ 로딩중' : '✅ 완료'}</p>
                  <p><strong>일정 개수:</strong> {upcomingSchedules.length}개</p>
                  <p><strong>가장 가까운 일정:</strong> {nearestSchedule?.title || '없음'}</p>
                  <p><strong>일정 상태:</strong> {scheduleStatusInfo?.text || '없음'}</p>
                  <p><strong>루틴 이름:</strong> {routineName || '없음'}</p>
                </div>
            )}
*/}
            {/* 진행중인 일정 섹션 */}
            <div
                data-aos="fade-up"
                data-aos-easing="ease-in-out"
                data-aos-duration="400"
                data-aos-once="true"
                data-aos-delay="200"
                className="flex justify-between items-end w-full mb-[8px] px-[5px]"
            >
              <p className="text-[#01274F] text-[19px] font-[700] tracking-[-0.4px]">
                진행중인 일정
              </p>
              {scheduleStatusInfo && (
                  <p>
                <span
                    className={`schedule-status text-[16px] tracking-[-0.4px]`}
                    style={{ color: scheduleStatusInfo.color, fontWeight: scheduleStatusInfo.fontWeight || '600' }}
                >
                  {scheduleStatusInfo.text}
                </span>
                  </p>
              )}
            </div>

            {/*{isLoading && !nearestSchedule && (
                <div className="w-full bg-[#fff] p-[15px] rounded-[6px] shadow-[0px_0px_5px_rgba(0,0,0,0.2)] mb-[22px] text-center">
                  <p className="text-[#383838]">일정 정보를 불러오는 중입니다...</p>
                </div>
            )}*/}

            {!isLoading && !nearestSchedule && (
                <div
                    data-aos="fade-up"
                    data-aos-easing="ease-in-out"
                    data-aos-duration="400"
                    data-aos-once="true"
                    data-aos-delay="200"
                    className="w-full bg-[#fff] p-[15px] rounded-[6px] shadow-[0px_0px_5px_rgba(0,0,0,0.2)] mb-[22px] text-center"
                >
                  <p className="text-[#383838] text-[17px] font-[500]">가장 가까운 일정이 없습니다.</p>
                </div>
            )}

            {nearestSchedule && (
                <div
                    data-aos="fade-up"
                    data-aos-easing="ease-in-out"
                    data-aos-duration="400"
                    data-aos-once="true"
                    data-aos-delay="200"
                    className="w-full bg-[#fff] p-[15px] rounded-[6px] shadow-[0px_0px_5px_rgba(0,0,0,0.2)] mb-[22px]"
                >
                  <div className="flex justify-between items-center w-full mb-[8px] ">
                    <p className="text-[#383838] text-[17px] font-[500] tracking-[-0.4px] leading-[155%] line-clamp-1">
                      {nearestSchedule.title}
                    </p>
                    <div className="flex items-center gap-x-[1px]">
                      <img src="/icon/clock.svg" alt="clock icon" />
                      <p className="text-[#0080FF] text-[16px] font-[500] tracking-[-0.4px] leading-[160%]">
                        {format(new Date(nearestSchedule.startTime), "HH:mm")}
                      </p>
                    </div>
                  </div>
                  {nearestSchedule.location && (
                      <p className="text-[#383838] text-[15px] font-[400] tracking-[-0.1px] leading-[160%] line-clamp-1">
                        장소 : <span>{nearestSchedule.location}</span>
                      </p>
                  )}
                  {routineName && (
                      <p className="text-[#383838] text-[15px] font-[400] tracking-[-0.1px] leading-[160%] line-clamp-1">
                        루틴 :{" "}
                        <span className="font-[500] tracking-[-0.4px] bg-[#0080FF] text-[#fff] px-[7px] rounded-[10px] leading-[120%]">
                    {routineName}
                  </span>
                      </p>
                  )}
                  {nearestSchedule.supplies && (
                      <p className="text-[#383838] text-[15px] font-[400] tracking-[-0.1px] leading-[160%]">
                        준비물 : <span>{nearestSchedule.supplies}</span>
                      </p>
                  )}
                  {nearestSchedule.memo && (
                      <p className="text-[#383838] text-[15px] font-[400] tracking-[-0.1px] leading-[160%]">
                        메모 : <span>{nearestSchedule.memo}</span>
                      </p>
                  )}
                  <div className="my-[10px] w-full h-[1px] bg-[#dfdfdf]"></div>
                  <p className="text-[#383838] text-[16px] font-[500] tracking-[-0.8px] leading-[155%] line-clamp-1 mb-[7px]">
                    실시간 예상 소요시간
                  </p>
                  <div className=" grid grid-cols-3">
                    <div className="h-auto flex gap-x-[5px] items-center justify-center">
                      <svg
                          width="22"
                          height="22"
                          viewBox="0 0 22 22"
                          fill="none"
                          xmlns="http://www.w3.org/2000/svg"
                          xlinkHref="http://www.w3.org/1999/xlink"
                      >
                        <rect width="22" height="22" fill="url(#pattern0_298_2791)" />
                        <defs>
                          <pattern
                              id="pattern0_298_2791"
                              patternContentUnits="objectBoundingBox"
                              width="1"
                              height="1"
                          >
                            <use
                                xlinkHref="#image0_298_2791"
                                transform="scale(0.00195312)"
                            />
                          </pattern>
                          <image
                              id="image0_298_2791"
                              width="512"
                              height="512"
                              preserveAspectRatio="none"
                              xlinkHref="data:image/png;base64,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"
                          />
                        </defs>
                      </svg>

                      <p className="group-hover:!text-[#fff] text-[#01274F] text-[14px] font-[500] tracking-[-0.8px] leading-[110%]">
                        30분
                      </p>
                    </div>
                    <div className="h-auto flex gap-x-[5px] items-center justify-center">
                      <svg
                          width="22"
                          height="22"
                          viewBox="0 0 22 22"
                          fill="none"
                          xmlns="http://www.w3.org/2000/svg"
                          xlinkHref="http://www.w3.org/1999/xlink"
                      >
                        <rect width="22" height="22" fill="url(#pattern0_298_2792)" />
                        <defs>
                          <pattern
                              id="pattern0_298_2792"
                              patternContentUnits="objectBoundingBox"
                              width="1"
                              height="1"
                          >
                            <use
                                xlinkHref="#image0_298_2792"
                                transform="scale(0.00195312)"
                            />
                          </pattern>
                          <image
                              id="image0_298_2792"
                              width="512"
                              height="512"
                              preserveAspectRatio="none"
                              xlinkHref="data:image/png;base64,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"
                          />
                        </defs>
                      </svg>

                      <p className="group-hover:!text-[#fff] text-[#01274F] text-[14px] font-[500] tracking-[-0.8px] leading-[110%]">
                        1시간 5분
                      </p>
                    </div>
                    <div className="h-auto flex gap-x-[5px] items-center justify-center">
                      <svg
                          width="22"
                          height="22"
                          viewBox="0 0 22 22"
                          fill="none"
                          xmlns="http://www.w3.org/2000/svg"
                          xlinkHref="http://www.w3.org/1999/xlink"
                      >
                        <rect width="22" height="22" fill="url(#pattern0_298_2793)" />
                        <defs>
                          <pattern
                              id="pattern0_298_2793"
                              patternContentUnits="objectBoundingBox"
                              width="1"
                              height="1"
                          >
                            <use
                                xlinkHref="#image0_298_2793"
                                transform="scale(0.00195312)"
                            />
                          </pattern>
                          <image
                              id="image0_298_2793"
                              width="512"
                              height="512"
                              preserveAspectRatio="none"
                              xlinkHref="data:image/png;base64,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"
                          />
                        </defs>
                      </svg>

                      <p className="group-hover:!text-[#fff] text-[#01274F] text-[14px] font-[500] tracking-[-0.8px] leading-[110%]">
                        5시간 10분
                      </p>
                    </div>
                  </div>
                  <Link
                      href="https://map.kakao.com/?map_type=TYPE_MAP&target=car&rt=%2C%2C537811%2C927642&rt1=%EC%82%BC%EC%9C%A1%EB%8C%80%ED%95%99%EA%B5%90&rt2=%EA%B0%95%EB%82%A8%EC%97%AD2%EB%B2%88%EC%B6%9C%EA%B5%AC&rtIds=%2C&rtTypes=%2C"
                      target="_blank"
                      className="px-[5px] py-[10px] border-[#dfdfdf] border-[1px] rounded-[5px] hover:opacity-[0.7] w-full flex items-center justify-center gap-x-[5px] mt-[10px]"
                  >
                    <svg
                        width="22"
                        height="22"
                        viewBox="0 0 22 22"
                        fill="none"
                        xmlns="http://www.w3.org/2000/svg"
                        xlinkHref="http://www.w3.org/1999/xlink"
                    >
                      <rect
                          width="22"
                          height="22"
                          fill="url(#pattern0_298_2746dafasdfa)"
                      />
                      <defs>
                        <pattern
                            id="pattern0_298_2746dafasdfa"
                            patternContentUnits="objectBoundingBox"
                            width="1"
                            height="1"
                        >
                          <use
                              xlinkHref="#image0_298_2746"
                              transform="translate(-3.68301 -1.77451) scale(0.00326797)"
                          />
                        </pattern>
                        <image
                            id="image0_298_2746"
                            width="2560"
                            height="1440"
                            preserveAspectRatio="none"
                            xlinkHref=""
                        />
                      </defs>
                    </svg>
                    <p className="text-[#383838] text-[14px] tracking-[-0.2px] font-[400]">
                      TMAP 빠른 길찾기
                    </p>
                  </Link>
                  <div className="mt-[20px] mb-[13px] w-full h-[1px] bg-[#dfdfdf]"></div>
                  <div className="flex justify-between items-center w-full mb-[8px] ">
                    <p className="text-[#383838] text-[16px] font-[500] tracking-[-0.8px] leading-[155%] line-clamp-1 mb-[7px]">
                      {currentRoutineDetails ? currentRoutineDetails.name : (routineName || "루틴 정보 로딩 중...")}
                    </p>
                  </div>
                  <div className="flex flex-col gap-[9px]">
                    {currentRoutineDetails && currentRoutineDetails.items && currentRoutineDetails.items.length > 0 && scheduleToUse && scheduleToUse.startTime ? (
                        (() => { // 즉시 실행 함수 (IIFE)를 사용하여 변수 스코프 및 로직 그룹화
                          let accumulatedDurationMinutes = 0;
                          const scheduleStartTimeDate = new Date(scheduleToUse.startTime);

                          // 스케줄 시작 시간이 유효한지 확인
                          if (isNaN(scheduleStartTimeDate.getTime())) {
                            console.error("Invalid schedule start time:", scheduleToUse.startTime);
                            return <p className="text-center text-red-500 py-2">스케줄 시작 시간이 유효하지 않습니다.</p>;
                          }

                          return currentRoutineDetails.items.map((item, index) => {
                            // 현재 아이템의 시작 시간 계산 (이전 아이템들의 소요시간 누적)
                            const itemStartTime = new Date(scheduleStartTimeDate.getTime() + accumulatedDurationMinutes * 60000);

                            const currentItemDuration = item.durationMinutes;

                            // 현재 아이템의 종료 시간 계산
                            const itemEndTime = new Date(itemStartTime.getTime() + currentItemDuration * 60000);

                            // 다음 아이템의 시작 시간 계산을 위해 현재 아이템의 소요시간을 누적
                            accumulatedDurationMinutes += currentItemDuration;

                            let itemStatus: "완료" | "진행 중" | "대기 중" = "대기 중";
                            let itemIcon = "⏭️"; // 대기 중 아이콘
                            let itemBgColor = "bg-[#0080FF]/40"; // 대기 중 배경색

                            if (currentTime >= itemEndTime) {
                              itemStatus = "완료";
                              itemIcon = "✅";
                              itemBgColor = "bg-green-500"; // 완료 시 배경색
                            } else if (currentTime >= itemStartTime && currentTime < itemEndTime) {
                              itemStatus = "진행 중";
                              itemIcon = "⌛"; // 진행 중 아이콘
                              itemBgColor = "bg-[#0080FF]"; // 진행 중 배경색
                            }
                            // '대기 중' 상태는 위 조건에 해당하지 않으면 기본값으로 유지=

                            return (
                                <div key={item.id} className={`w-full h-auto px-[10px] py-[5px] ${itemBgColor} rounded-[6px] flex items-center justify-between transition-colors duration-300 ease-in-out`}>
                                  <p className="text-[#fff] text-[14px] font-[600] tracking-[-0.5px] leading-[102%] line-clamp-1">
                                    {item.name}
                                  </p>
                                  <div className="flex items-center gap-x-[1px]">
                                    <span className="text-lg">{itemIcon}</span> {/* 아이콘 크기 조절 */}
                                    <span className="text-[#fff] text-[15px] font-[600] tracking-[-0.8px] leading-[102%] line-clamp-1 ml-1">
              {item.durationMinutes}분
            </span>
                                    <span className="text-[#fff] text-[15px] font-[600] tracking-[-0.8px] leading-[102%] line-clamp-1">
              &nbsp;{itemStatus}
            </span>
                                  </div>
                                </div>
                            );
                          });
                        })() // IIFE 호출
                    ) : scheduleToUse && scheduleToUse.routineId ? (
                        <p className="text-center text-gray-500 py-2">루틴 아이템을 불러오는 중이거나 아이템이 없습니다.</p>
                    ) : (
                        <p className="text-center text-gray-500 py-2">선택된 루틴이 없습니다.</p>
                    )}
                  </div>
                </div>
            )}

            {/* 다가오는 일정 */}
            <div
                data-aos="fade-up"
                data-aos-easing="ease-in-out"
                data-aos-duration="400"
                data-aos-once="true"
                data-aos-delay="400"
                className="flex justify-between items-end w-full mb-[8px] px-[5px]"
            >
              <p className="text-[#01274F] text-[19px] font-[700] tracking-[-0.4px]">
                다가오는 일정
              </p>
              <Link href="/calendar" className="flex items-center gap-x-[2px]">
                <p className="text-[#01274F] text-[14px] font-[400] tracking-[-0.4px] leading-[110%]">
                  더보기
                </p>
                <svg
                    width="22"
                    height="22"
                    viewBox="0 0 22 22"
                    fill="none"
                    xmlns="http://www.w3.org/2000/svg"
                >
                  <path
                      d="M11 8.06675V11.0001M11 11.0001V13.9334M11 11.0001H13.9333M11 11.0001L8.06665 11.0001M3.66666 15.5834C3.66665 17.1022 4.89787 18.3334 6.41666 18.3334H15.5833C17.1021 18.3334 18.3333 17.1022 18.3333 15.5834V6.41675C18.3333 4.89797 17.1021 3.66675 15.5833 3.66675H6.41666C4.89788 3.66675 3.66666 4.89797 3.66666 6.41675L3.66666 15.5834Z"
                      stroke="#01274F"
                      strokeLinecap="round"
                  />
                </svg>
              </Link>
            </div>
            <div
                data-aos="fade-up"
                data-aos-easing="ease-in-out"
                data-aos-duration="400"
                data-aos-once="true"
                data-aos-delay="400"
                className="w-full bg-[#fff] p-[15px] rounded-[6px] shadow-[0px_0px_5px_rgba(0,0,0,0.2)] mb-[22px]"
            >
              {isLoading ? (
                  <p className="text-center py-2">일정을 불러오는 중...</p>
              ) : upcomingSchedules.filter(schedule => new Date(schedule.startTime) > new Date()).length > 0 ? (
                  upcomingSchedules
                      .filter(schedule => new Date(schedule.startTime) > new Date()) // 현재 시간 이후의 일정만 필터링
                      .map((schedule) => {
                        console.log("📅 [Render Debug] 필터링 후 일정 렌더링:", schedule.title, schedule.id);
                        return (
                            <Link
                                key={schedule.id}
                                href={`/calendar/edit?id=${schedule.id}`}
                                className="flex items-start flex-col justify-between gap-x-[10px] gap-y-[4px] w-full bg-[#fff] border-b-[1px] border-[#dfdfdf] py-[7px] px-[10px] lg:px-[20px] hover:bg-[#dfdfdf] last:!border-[0px] first:!pt-[0px] last:!pb-[0px]"
                            >
                              <p className="w-full text-[#383838] text-[13px] line-clamp-1 pr-[15px]">
                                {schedule.title}
                              </p>
                              <div className="flex flex-row gap-x-[3px] md:gap-x-[15px] xl:gap-x-[65px]">
                                <p className="w-full text-[#777] text-[13px] pr-[5px] min-w-[68px] text-left md:text-center whitespace-nowrap">
                                  {formatDateTime(schedule.startTime)}
                                </p>
                              </div>
                            </Link>
                        );
                      })
              ) : (
                  <p className="text-center py-2">다가오는 일정이 없습니다.</p>
              )}
            </div>

            {/* 날씨 */}
            <div
                data-aos="fade-up"
                data-aos-easing="ease-in-out"
                data-aos-duration="400"
                data-aos-once="true"
                data-aos-delay="600" // 애니메이션 딜레이 추가
                className="flex justify-between items-end w-full mb-[8px] px-[5px]"
            >
              <p className="text-[#01274F] text-[19px] font-[700] tracking-[-0.4px]">
                날씨
              </p>
              <Link
                  href="https://www.kma.go.kr/w/iframe/dfs.do"
                  target="_blank"
                  className="flex items-center gap-x-[2px]"
              >
                <p className="text-[#01274F] text-[14px] font-[400] tracking-[-0.4px] leading-[110%]">
                  더보기
                </p>
                <svg
                    width="22"
                    height="22"
                    viewBox="0 0 22 22"
                    fill="none"
                    xmlns="http://www.w3.org/2000/svg"
                >
                  <path
                      d="M8.90809 16.4423L8.05786 15.592L13.625 10.0248L8.05786 4.45759L8.90809 3.60736L15.3255 10.0248L8.90809 16.4423Z"
                      fill="#01274F"
                  />
                </svg>
              </Link>
            </div>
            <iframe
                data-aos="fade-up"
                data-aos-easing="ease-in-out"
                data-aos-duration="400"
                data-aos-once="true"
                data-aos-delay="600" // 애니메이션 딜레이 추가
                src="https://www.kma.go.kr/w/iframe/dfs.do"
                className="w-full bg-[#fff] px-[10px] rounded-[6px] shadow-[0px_0px_5px_rgba(0,0,0,0.2)] mb-[22px] aspect-[38/93]"
            ></iframe>
          </div>
        </div>
      </div>
  );
}



