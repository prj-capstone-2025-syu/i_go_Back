"use client";
import React, { useState, useEffect, useCallback, FC } from "react";
import AOS from "aos";
import "aos/dist/aos.css";
import NavBarMain from "@/components/common/topNavMain";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { format } from "date-fns";
import { getUpcomingSchedules, getLatestInProgressSchedule } from "@/api/scheduleApi";
import { getRoutineById } from "@/api/routineApi";
import { sendFCMTokenToServer } from "@/api/userApi";
import { getMessaging, getToken, onMessage } from "firebase/messaging";
import { app } from "@/utils/firebase";
import { calculateAllTransportTimes } from "@/api/transportApi"; // 이동시간 API 추가

// 타입 정의
interface ScheduleType {
  id: number;
  title: string;
  startTime: string;
  endTime: string;
  location: string | null;
  memo: string | null;
  supplies: string | null;
  category: string;
  routineId: number | null;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';
  startX?: number | null; // 출발지 X 좌표 추가
  startY?: number | null; // 출발지 Y 좌표 추가
  destinationX?: number | null; // 목적지 X 좌표 추가
  destinationY?: number | null; // 목적지 Y 좌표 추가
  startLocation?: string | null; // 출발지 명칭 추가
}

interface RoutineInfo {
  id: number;
  name: string;
  totalDurationMinutes?: number;
  items?: RoutineItem[];
}

interface RoutineItem {
  id: number;
  name: string;
  durationMinutes: number;
}

// TMap 스크립트 로딩 상태 관리
let isMapScriptLoading = false;
let isMapScriptLoaded = false;
let mapScriptCallbacks: Function[] = [];
let loadRetries = 0;
const MAX_RETRIES = 3;

// TMap 스크립트 로드 함수
const loadTMapScript = () => {
  return new Promise((resolve, reject) => {
    if (isMapScriptLoaded) {
      console.log("TMap API 이미 로드됨");
      resolve(true);
      return;
    }

    if (isMapScriptLoading) {
      console.log("TMap API 로드 중... 콜백 등록");
      mapScriptCallbacks.push(resolve);
      return;
    }

    // 로딩 상태로 변경
    isMapScriptLoading = true;

    // API 키 확인
    const apiKey = process.env.NEXT_PUBLIC_TMAP_API_KEY;
    if (!apiKey) {
      console.error("TMap API 키가 설정되지 않았습니다.");
      reject(new Error("TMap API 키가 설정되지 않았습니다."));
      isMapScriptLoading = false;
      return;
    }

    console.log("TMap API 로드 시작...");

    // 프로토콜 명시적으로 지정 (https)
    const mapScript = document.createElement("script");
    mapScript.src = `https://tmapapi.tmapmobility.com/jsv2?version=1&appKey=${apiKey}`;
    mapScript.async = true;
    mapScript.crossOrigin = "anonymous"; // CORS 문제 해결 시도

    mapScript.onload = () => {
      console.log("TMap API 로드 성공!");
      isMapScriptLoaded = true;
      isMapScriptLoading = false;
      loadRetries = 0; // 성공 시 재시도 카운터 초기화
      resolve(true);
      mapScriptCallbacks.forEach(callback => callback(true));
      mapScriptCallbacks = [];
    };

    mapScript.onerror = (error) => {
      console.error("TMap 스크립트 로드 실패:", error);
      isMapScriptLoading = false;

      // 재시도 로직
      if (loadRetries < MAX_RETRIES) {
        loadRetries++;
        console.log(`TMap API 로드 재시도 (${loadRetries}/${MAX_RETRIES})...`);
        // 0.5초 후 재시도
        setTimeout(() => {
          document.head.removeChild(mapScript);
          loadTMapScript().then(resolve).catch(reject);
        }, 500);
        return;
      }

      // 최대 재시도 횟수 초과
      reject(new Error(`TMap 스크립트 로드 실패 (${loadRetries} 회 시도)`));
      mapScriptCallbacks.forEach(callback => callback(false));
      mapScriptCallbacks = [];
    };

    document.head.appendChild(mapScript);
  });
};

// React 컴포넌트
const Home: FC = () => {
  const [keyword, setKeyword] = useState("");
  const router = useRouter();

  const [upcomingSchedules, setUpcomingSchedules] = useState<ScheduleType[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isTokenChecked, setIsTokenChecked] = useState(false); // 토큰 확인 완료 상태 추가
  const [nearestSchedule, setNearestSchedule] = useState<ScheduleType | null>(null);
  const [inProgressSchedule, setInProgressSchedule] = useState<ScheduleType | null>(null);
  const [scheduleStatusInfo, setScheduleStatusInfo] = useState<{ text: string; color: string; fontWeight?: string } | null>(null);
  const [routineName, setRoutineName] = useState<string | null>(null);
  const [currentRoutineDetails, setCurrentRoutineDetails] = useState<RoutineInfo | null>(null);
  const [currentTime, setCurrentTime] = useState(new Date());
  // 스케줄 데이터 준비 상태를 관리하는 새로운 state
  const [scheduleDataReady, setScheduleDataReady] = useState(false);
  // 이동시간 정보를 관리하는 state 추가
  const [transportTimes, setTransportTimes] = useState<{
    driving: number | null;
    transit: number | null;
    walking: number | null;
  }>({
    driving: null,
    transit: null,
    walking: null
  });
  // 이동시간 로딩 상태
  const [isTransportLoading, setIsTransportLoading] = useState(false);
  // 이동시간 애니메이션을 위한 상태
  const [showTransportTimes, setShowTransportTimes] = useState(false);
  // 비대면 일정 여부 확인을 위한 상태 추가
  const [isRemoteEvent, setIsRemoteEvent] = useState(false);

  // 1분마다 자동 리프레시를 위한 상태
  const [refreshToken, setRefreshToken] = useState(0);

  // 현재 표시할 일정을 결정하는 함수 (진행 중인 일정 > 다가오는 일정 순)
  const getCurrentSchedule = useCallback(() => {
    return inProgressSchedule || nearestSchedule;
  }, [inProgressSchedule, nearestSchedule]);

  // 페이지 로드시 토큰 확인
  useEffect(() => {
    // 쿠키에서 토큰 확인
    const hasToken = document.cookie.includes('access_token');

    setIsAuthenticated(hasToken);
    setIsTokenChecked(true); // 토큰 확인 완료

    if (!hasToken) {
      // 토큰이 없으면 /greeting 페이지로 리다이렉트
      if (window.location.pathname !== "/greeting") {
        localStorage.setItem("redirectPath", window.location.pathname + window.location.search);
      }
      router.push('/greeting');
    }
  }, [router]);

  // FCM 토큰 요청 및 서버 전송 로직
  useEffect(() => {
    if (typeof window !== 'undefined' && 'Notification' in window && isAuthenticated) {
      const messaging = getMessaging(app);

      const requestPermissionAndToken = async () => {
        try {
          const permission = await Notification.requestPermission();
          if (permission === "granted") {
            console.log("Notification permission granted.");
            // Firebase 콘솔에서 가져온 VAPID키
            // 프로젝트 설정 > 클라우드 메시징 > 웹 푸시 인증서 > 웹 구성의 키 쌍
            const currentToken = await getToken(messaging, {
              vapidKey: "BK6gC7kpp7i9gv1WMQuWsW_487xmyfsXWtE0DERzOUunoCWN3fzoJ0JwP3BIL_d4pYGcjlGxhjjmD59-0UGzoug",
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
  }, [isAuthenticated]);

  useEffect(() => {
    AOS.init();
  }, []);

  // 일정 초기 데이터 로딩
  useEffect(() => {
    if (isAuthenticated) {
      setIsLoading(true);
      setScheduleDataReady(false); // 데이터 로딩 시작 시 준비 상태 초기화

      // 두 API를 병렬로 호출하여 데이터 로딩 최적화
      Promise.all([
        getUpcomingSchedules(3),
        getLatestInProgressSchedule()
      ])
          .then(([upcomingData, inProgressData]) => {
            // 다가오는 일정 설정
            const sortedSchedules = (upcomingData || []).sort((a: ScheduleType, b: ScheduleType) =>
                new Date(a.startTime).getTime() - new Date(b.startTime).getTime()
            );
            setUpcomingSchedules(sortedSchedules);

            // 가장 가까운 일정 설정 (진행 중인 일정이 아닌 것 중에서)
            const nearestUpcoming = sortedSchedules.find((schedule: ScheduleType) =>
                schedule.status !== 'IN_PROGRESS' && new Date(schedule.startTime) > new Date()
            ) || null;
            setNearestSchedule(nearestUpcoming);

            // 진행 중인 일정 설정
            setInProgressSchedule(inProgressData);

            setIsLoading(false);
            // 데이터 로딩이 완료되고, 필요한 정보가 모두 준비되었을 때 준비 상태를 true로 설정
            setTimeout(() => {
              setScheduleDataReady(true);
            }, 100); // 약간의 지연을 두어 상태 업데이트 순서 보장
          })
          .catch(error => {
            setIsLoading(false);
            setScheduleDataReady(true); // 에러 발생 시에도 로딩 상태 완료로 처리
          });
    }
  }, [isAuthenticated]);

  // 진행 중인 일정과 루틴 정보만 업데이트하는 함수
  const refreshTimeAndRoutineInfo = useCallback(async () => {
    if (!isAuthenticated) return;

    try {
      console.log("🔄 1분마다 시간과 루틴 정보 새로고침...");

      // 현재 시간 업데이트
      setCurrentTime(new Date());

      // 진행 중인 일정 업데이트
      const inProgressData = await getLatestInProgressSchedule();
      setInProgressSchedule(inProgressData);

      // 다가오는 일정도 새로 가져와서 업데이트
      const upcomingData = await getUpcomingSchedules(3);
      const sortedSchedules = (upcomingData || []).sort((a: ScheduleType, b: ScheduleType) =>
          new Date(a.startTime).getTime() - new Date(b.startTime).getTime()
      );
      setUpcomingSchedules(sortedSchedules);

      // 가장 가까운 일정 재설정 (진행 중인 일정이 아닌 것 중에서)
      const nearestUpcoming = sortedSchedules.find((schedule: { status: string; startTime: string | number | Date; }) =>
          schedule.status !== 'IN_PROGRESS' && new Date(schedule.startTime) > new Date()
      ) || null;
      setNearestSchedule(nearestUpcoming);

    } catch (error) {
      console.error("Error refreshing time and routine info:", error);
    }
  }, [isAuthenticated]);

  // 1분마다 시간과 루틴 정보 업데이트
  useEffect(() => {
    if (!isAuthenticated) return;

    // 페이지 로드 시 초기 업데이트
    refreshTimeAndRoutineInfo();

    // 1분 간격으로 반복 업데이트
    const intervalId = setInterval(() => {
      setRefreshToken(prev => prev + 1);
    }, 60000); // 60000ms = 1분

    return () => clearInterval(intervalId);
  }, [isAuthenticated, refreshTimeAndRoutineInfo]);

  // refreshToken이 변경될 때마다 시간과 루틴 정보 업데이트
  useEffect(() => {
    if (refreshToken > 0) {
      refreshTimeAndRoutineInfo();
    }
  }, [refreshToken, refreshTimeAndRoutineInfo]);

  // 일정 상태 정보 계산
  useEffect(() => {
    const currentSchedule = getCurrentSchedule();

    // 현재 표시할 일정이 없으면 정보 없음
    if (!currentSchedule) {
      setScheduleStatusInfo(null);
      return;
    }

    const now = new Date();
    const startTime = new Date(currentSchedule.startTime);

    // 상태 메시지 설정
    if (currentSchedule.status === 'IN_PROGRESS' || currentSchedule === inProgressSchedule) {
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
          text: `${diffMinutes}분 후 시작!`,
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
  }, [inProgressSchedule, nearestSchedule, currentTime, refreshToken, getCurrentSchedule]);

  // 루틴 이름과 세부 정보 로드
  useEffect(() => {
    const currentSchedule = getCurrentSchedule();

    if (currentSchedule && currentSchedule.routineId) {
      getRoutineById(currentSchedule.routineId)
          .then((data: RoutineInfo) => {
            setRoutineName(data.name);
            setCurrentRoutineDetails(data);
          })
          .catch(error => {
            setRoutineName(null);
            setCurrentRoutineDetails(null);
          });
    } else {
      setRoutineName(null);
      setCurrentRoutineDetails(null);
    }
  }, [inProgressSchedule, nearestSchedule, refreshToken, getCurrentSchedule]);

  // 장소 정보가 있을 경우 이동시간 가져오기
  useEffect(() => {
    const currentSchedule = getCurrentSchedule();

    if (currentSchedule) {
      // 애니메이션 초기 상태 설정
      setShowTransportTimes(false);

      // 비대면 일정인지 확인
      if (currentSchedule.location?.toLowerCase() === '비대면') {
        // 비대면 일정으로 설정
        setIsRemoteEvent(true);
        console.log('🚦 [DEBUG] 비대면 일정 확인됨', currentSchedule.title);
        setIsTransportLoading(false);
        return;
      } else {
        // 대면 일정으로 설정
        setIsRemoteEvent(false);
      }

      // 출발지와 목적지 좌표가 있는지 확인
      const hasStartCoords = currentSchedule.startX != null && currentSchedule.startY != null;
      const hasDestCoords = currentSchedule.destinationX != null && currentSchedule.destinationY != null;

      console.log('🚦 [DEBUG] 일정 좌표 정보:', {
        scheduleId: currentSchedule.id,
        title: currentSchedule.title,
        hasStartCoords,
        hasDestCoords,
        startX: currentSchedule.startX,
        startY: currentSchedule.startY,
        destX: currentSchedule.destinationX,
        destY: currentSchedule.destinationY,
        location: currentSchedule.location
      });

      // 위치 정보가 있는 경우에만 이동시간 계산
      if ((hasStartCoords && hasDestCoords) || (currentSchedule.location)) {
        setIsTransportLoading(true);
        console.log('🚦 [DEBUG] 이동시간 계산 시작...');

        // 이동시간 계산 함수
        const calculateTimes = async () => {
          try {
            let startX, startY, endX, endY;

            // 1. 출발지 좌표 설정
            if (hasStartCoords) {
              // 일정에 저장된 출발지 좌표 사용
              startX = currentSchedule.startX;
              startY = currentSchedule.startY;
              console.log("🚦 [DEBUG] 일정의 출발지 좌표 사용:", startX, startY);
            } else {
              // 사용자 현재 위치 사용 (허용한 경우)
              try {
                console.log("🚦 [DEBUG] 사용자 위치 정보 요청 중...");
                const position = await new Promise<GeolocationPosition>((resolve, reject) => {
                  if (typeof navigator !== 'undefined' && navigator.geolocation) {
                    navigator.geolocation.getCurrentPosition(resolve, reject, {
                      enableHighAccuracy: true,
                      timeout: 5000,
                      maximumAge: 0
                    });
                  } else {
                    reject(new Error('위치 정보를 사용할 수 없습니다.'));
                  }
                });

                startX = position.coords.longitude;
                startY = position.coords.latitude;
                console.log("🚦 [DEBUG] 현재 사용자 위치 사용:", startX, startY);
              } catch (error) {
                console.warn("🚦 [DEBUG] 위치 정보 가져오기 실패, 기본값 사용:", error);
                // 서울시청 기본 좌표
                startX = 126.9779692;
                startY = 37.5662952;
                console.log("🚦 [DEBUG] 기본 출발지 좌표(서울시청) 사용:", startX, startY);
              }
            }

            // 2. 도착지 좌표 설정
            if (hasDestCoords) {
              // 일정에 저장된 목적지 좌표 사용
              endX = currentSchedule.destinationX;
              endY = currentSchedule.destinationY;
              console.log("🚦 [DEBUG] 일정의 목적지 좌표 사용:", endX, endY);
            } else if (currentSchedule.location) {
              // 목적지 좌표가 없지만 장소명은 있는 경우
              console.log("🚦 [DEBUG] 목적지 좌표 없음, 기본값(강남역) 사용");
              // 강남역 기본 좌표
              endX = 127.0495556;
              endY = 37.5032500;
              console.log("🚦 [DEBUG] 기본 목적지 좌표(강남역) 사용:", endX, endY);
            } else {
              // 목적지 정보가 전혀 없는 경우
              console.log("🚦 [DEBUG] 목적지 정보 없음, 계산 취소");
              setIsTransportLoading(false);
              return;
            }

            console.log("🚦 [DEBUG] 최종 이동시간 계산 좌표:", { startX, startY, endX, endY });

            // 3. TransportApi를 통해 이동시간 계산
            console.log("🚦 [DEBUG] calculateAllTransportTimes() 호출 시작");
            const startTime = performance.now();
            const times = await calculateAllTransportTimes(startX, startY, endX, endY);
            const endTime = performance.now();
            console.log(`🚦 [DEBUG] calculateAllTransportTimes() 완료 (${(endTime - startTime).toFixed(2)}ms)`, times);

            setTransportTimes({
              driving: times.driving,
              transit: times.transit,
              walking: times.walking
            });

            // 4. 데이터 로딩 완료 후 애니메이션 효과 표시
            setTimeout(() => {
              setIsTransportLoading(false);
              setShowTransportTimes(true); // 애니메이션 표시 활성화
              console.log("🚦 [DEBUG] 이동시간 표시 애니메이션 활성화");
            }, 500);

          } catch (error) {
            console.error("🚦 [ERROR] 이동시간 계산 중 오류:", error);
            setIsTransportLoading(false);
          }
        };

        // 이동시간 계산 실행
        calculateTimes();
      } else {
        console.log('🚦 [DEBUG] 좌표 정보 부족으로 이동시간 계산 건너뜀');
      }
    }
  }, [getCurrentSchedule]);

  // Tmap 링크 생성 함수
  const generateTmapDirectionLink = (currentSchedule: ScheduleType | null) => {
    if (!currentSchedule) return "#";

    // 출발지와 목적지 좌표가 있는지 확인
    const hasStartCoords = currentSchedule.startX != null && currentSchedule.startY != null;
    const hasDestCoords = currentSchedule.destinationX != null && currentSchedule.destinationY != null;

    if (hasStartCoords && hasDestCoords) {
      return `https://map.kakao.com/?map_type=TYPE_MAP&target=car&rt=${currentSchedule.startY},${currentSchedule.startX},${currentSchedule.destinationY},${currentSchedule.destinationX}&rt1=${encodeURIComponent(currentSchedule.startLocation || '출발지')}&rt2=${encodeURIComponent(currentSchedule.location || '도착지')}&rtIds=,&rtTypes=,`;
    }

    // 좌표가 없으면 기본 링크 반환
    return "https://map.kakao.com/?map_type=TYPE_MAP&target=car";
  };

  // 이동 시간 포맷팅 함수 추가
  const formatTransportTime = (minutes: number | null): string => {
    if (minutes === null) return "-";
    if (minutes < 60) {
      return `${minutes}분`;
    } else {
      const hours = Math.floor(minutes / 60);
      const mins = minutes % 60;
      return `${hours}시간 ${mins > 0 ? mins + '분' : ''}`;
    }
  };

  // 날짜 포맷팅 함수
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
    event.preventDefault();

    if (keyword.trim()) {
      const encodedKeyword = encodeURIComponent(keyword.trim());
      const chatUrl = `/chat?keyword=${encodedKeyword}`;
      router.push(chatUrl);
    }
  };

  // 현재 표시할 일정 가져오기
  const currentSchedule = getCurrentSchedule();

  return (
      <div className="flex flex-col w-full h-full">
        {/* NavBarMain은 항상 표시되도록 수정 */}
        <NavBarMain link="/mypage"></NavBarMain>

        {(!isTokenChecked || !isAuthenticated) ? (
            // 토큰 확인 중이거나 인증되지 않은 경우 메인 콘텐츠 표시하지 않음
            null
        ) : (
            <>
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
                    {scheduleStatusInfo && scheduleDataReady && (
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

                  {/* 일정이 없는 경우(로딩 완료 후) */}
                  {scheduleDataReady && !isLoading && !currentSchedule && (
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

                  {/* 일정이 있는 경우(로딩 완료 후) */}
                  {scheduleDataReady && currentSchedule && (
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
                            {currentSchedule.title}
                          </p>
                          <div className="flex items-center gap-x-[1px]">
                            <img src="/icon/clock.svg" alt="clock icon" />
                            <p className="text-[#0080FF] text-[16px] font-[500] tracking-[-0.4px] leading-[160%]">
                              {format(new Date(currentSchedule.startTime), "HH:mm")}
                            </p>
                          </div>
                        </div>
                        {currentSchedule.location && (
                            <p className="text-[#383838] text-[15px] font-[400] tracking-[-0.1px] leading-[160%] line-clamp-1">
                              장소 : <span>{currentSchedule.location}</span>
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
                        {currentSchedule.supplies && (
                            <p className="text-[#383838] text-[15px] font-[400] tracking-[-0.1px] leading-[160%]">
                              준비물 : <span>{currentSchedule.supplies}</span>
                            </p>
                        )}
                        {currentSchedule.memo && (
                            <p className="text-[#383838] text-[15px] font-[400] tracking-[-0.1px] leading-[160%]">
                              메모 : <span>{currentSchedule.memo}</span>
                            </p>
                        )}
                        <div className="my-[10px] w-full h-[1px] bg-[#dfdfdf]"></div>
                        <p className="text-[#383838] text-[16px] font-[500] tracking-[-0.8px] leading-[155%] line-clamp-1 mb-[7px]">
                          실시간 예상 소요시간
                        </p>
                        {isRemoteEvent ? (
                          // 비대면 일정일 경우 메시지 표시
                          <div className="flex items-center justify-center py-3 px-4 bg-gray-50 rounded-md">
                            <span className="text-blue-600 mr-2 text-lg">💻</span>
                            <p className="text-[#383838] text-[15px] font-medium">
                              비대면 일정입니다. 이동시간이 필요하지 않습니다.
                            </p>
                          </div>
                        ) : (
                          // 대면 일정일 경우 이동시간 표시
                          <>
                            <div className="grid grid-cols-3">
                              <div className="h-auto flex gap-x-[5px] items-center justify-center">
                                <svg
                                    width="22"
                                    height="22"
                                    viewBox="0 0 22 22"
                                    fill="none"
                                    xmlns="http://www.w3.org/2000/svg"
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
                                        xlinkHref=""
                                    />
                                  </defs>
                                </svg>

                                <p className={`group-hover:!text-[#fff] text-[#01274F] text-[14px] font-[500] tracking-[-0.8px] leading-[110%] transition-opacity duration-500 ${showTransportTimes ? 'opacity-100' : 'opacity-0'}`}>
                                  {!isTransportLoading && transportTimes.driving !== null ? formatTransportTime(transportTimes.driving) : ""}
                                </p>
                                {isTransportLoading && (
                                    <span className="inline-block w-5 h-5 border-2 border-t-transparent border-[#01274F] rounded-full animate-spin"></span>
                                )}
                              </div>
                              <div className="h-auto flex gap-x-[5px] items-center justify-center">
                                <svg
                                    width="22"
                                    height="22"
                                    viewBox="0 0 22 22"
                                    fill="none"
                                    xmlns="http://www.w3.org/2000/svg"
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
                                        xlinkHref=""
                                    />
                                  </defs>
                                </svg>

                                <p className={`group-hover:!text-[#fff] text-[#01274F] text-[14px] font-[500] tracking-[-0.8px] leading-[110%] transition-opacity duration-500 ${showTransportTimes ? 'opacity-100' : 'opacity-0'}`}>
                                  {!isTransportLoading && transportTimes.transit !== null ? formatTransportTime(transportTimes.transit) : ""}
                                </p>
                                {isTransportLoading && (
                                    <span className="inline-block w-5 h-5 border-2 border-t-transparent border-[#01274F] rounded-full animate-spin"></span>
                                )}
                              </div>
                              <div className="h-auto flex gap-x-[5px] items-center justify-center">
                                <svg
                                    width="22"
                                    height="22"
                                    viewBox="0 0 22 22"
                                    fill="none"
                                    xmlns="http://www.w3.org/2000/svg"
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
                                        xlinkHref=""
                                    />
                                  </defs>
                                </svg>

                                <p className={`group-hover:!text-[#fff] text-[#01274F] text-[14px] font-[500] tracking-[-0.8px] leading-[110%] transition-opacity duration-500 ${showTransportTimes ? 'opacity-100' : 'opacity-0'}`}>
                                  {!isTransportLoading && transportTimes.walking !== null ? formatTransportTime(transportTimes.walking) : ""}
                                </p>
                                {isTransportLoading && (
                                    <span className="inline-block w-5 h-5 border-2 border-t-transparent border-[#01274F] rounded-full animate-spin"></span>
                                )}
                              </div>
                            </div>
                            <Link
                                href={generateTmapDirectionLink(currentSchedule)}
                                target="_blank"
                                className="px-[5px] py-[10px] border-[#dfdfdf] border-[1px] rounded-[5px] hover:opacity-[0.7] w-full flex items-center justify-center gap-x-[5px] mt-[10px]"
                            >
                              <svg
                                  width="22"
                                  height="22"
                                  viewBox="0 0 22 22"
                                  fill="none"
                                  xmlns="http://www.w3.org/2000/svg"
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
                          </>
                        )}
                        <div className="mt-[20px] mb-[13px] w-full h-[1px] bg-[#dfdfdf]"></div>
                        <div className="flex justify-between items-center w-full mb-[8px]">
                          <p className="text-[#383838] text-[17px] font-[600] tracking-[-0.4px] leading-[110%] line-clamp-1">
                            설정된 루틴
                          </p>
                          <div className="flex items-center gap-x-[1px]">
                          <span className="text-[#01274f] text-[15px] font-[600] tracking-[-0.8px] leading-[102%] line-clamp-1">
                            {currentRoutineDetails ?
                                `${currentRoutineDetails.name} (${currentRoutineDetails.totalDurationMinutes ||
                                (currentRoutineDetails.items ?
                                    currentRoutineDetails.items.reduce((sum, item) => sum + item.durationMinutes, 0) : 0)}분)`
                                : (routineName ? `${routineName} (로딩 중...)` : "루틴 없음")
                            }
                          </span>
                          </div>
                        </div>
                        <div className="flex flex-col gap-[9px]">
                          {currentRoutineDetails && currentRoutineDetails.items && currentRoutineDetails.items.length > 0 && currentSchedule && currentSchedule.startTime ? (
                              (() => {
                                let accumulatedDurationMinutes = 0;
                                const scheduleStartTimeDate = new Date(currentSchedule.startTime);

                                if (isNaN(scheduleStartTimeDate.getTime())) {
                                  console.error("Invalid schedule start time:", currentSchedule.startTime);
                                  return <p className="text-center text-red-500 py-2">스케줄 시작 시간이 유효하지 않습니다.</p>;
                                }

                                return currentRoutineDetails.items.map((item, index) => {
                                  const itemStartTime = new Date(scheduleStartTimeDate.getTime() + accumulatedDurationMinutes * 60000);
                                  const currentItemDuration = item.durationMinutes;
                                  const itemEndTime = new Date(itemStartTime.getTime() + currentItemDuration * 60000);
                                  accumulatedDurationMinutes += currentItemDuration;

                                  let itemStatus: "완료" | "진행 중" | "대기 중" = "대기 중";
                                  let itemIcon = "⏭️";
                                  let itemBgColor = "bg-[#0080FF]/40";

                                  if (currentTime >= itemEndTime) {
                                    itemStatus = "완료";
                                    itemIcon = "✅";
                                    itemBgColor = "bg-[#888]";
                                  } else if (currentTime >= itemStartTime && currentTime < itemEndTime) {
                                    itemStatus = "진행 중";
                                    itemIcon = "⌛";
                                    itemBgColor = "bg-[#0080FF]";
                                  }

                                  return (
                                      <div key={item.id} className={`w-full h-auto px-[10px] py-[5px] ${itemBgColor} rounded-[6px] flex items-center justify-between transition-colors duration-300 ease-in-out`}>
                                        <p className="text-[#fff] text-[14px] font-[600] tracking-[-0.5px] leading-[102%] line-clamp-1">
                                          {item.name}
                                        </p>
                                        <div className="flex items-center gap-x-[1px]">
                                          <span className="text-lg">{itemIcon}</span>
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
                              })()
                          ) : currentSchedule && currentSchedule.routineId ? (
                              <p className="text-center text-gray-500 py-2"> </p>
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
                    ) : upcomingSchedules.filter(schedule =>
                        schedule.status !== 'IN_PROGRESS' && new Date(schedule.startTime) > new Date()
                    ).length > 0 ? (
                        upcomingSchedules
                            .filter(schedule =>
                                schedule.status !== 'IN_PROGRESS' && new Date(schedule.startTime) > new Date()
                            )
                            .map((schedule) => (
                                <Link
                                    key={schedule.id}
                                    href={`/calendar/detail?id=${schedule.id}`}
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
                            ))
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
                      data-aos-delay="600"
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
                      data-aos-delay="600"
                      src="https://www.kma.go.kr/w/iframe/dfs.do"
                      className="w-full bg-[#fff] px-[10px] rounded-[6px] shadow-[0px_0px_5px_rgba(0,0,0,0.2)] mb-[22px] aspect-[38/93]"
                  ></iframe>
                </div>
              </div>
            </>
        )}
      </div>
  );
};

export default Home;
