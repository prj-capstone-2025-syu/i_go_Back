"use client";
import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import NavBar from "@/components/common/topNav";
import Link from "next/link";
import { getScheduleById, deleteSchedule } from "@/api/scheduleApi";
import { getRoutineById } from "@/api/routineApi";
import { format } from "date-fns";
import ConfirmPopup from "@/components/common/ConfirmPopup";
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

export default function ScheduleDetail() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const scheduleId = searchParams.get('id');

  const [schedule, setSchedule] = useState<ScheduleType | null>(null);
  const [routineDetails, setRoutineDetails] = useState<RoutineInfo | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [currentTime] = useState(new Date());
  const [error, setError] = useState<string | null>(null);
  const [isDeletePopupOpen, setIsDeletePopupOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState(""); // 검색어 상태 변수 추가
  const [isTransportLoading, setIsTransportLoading] = useState(false); // 이동시간 로딩 상태
  const [showTransportTimes, setShowTransportTimes] = useState(false); // 이동시간 애니메이션을 위한 상태
  // 비대면 일정 여부 상태 추가
  const [isRemoteEvent, setIsRemoteEvent] = useState(false);
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

  // 스케줄 데이터 로드
  useEffect(() => {
    const fetchScheduleData = async () => {
      if (!scheduleId) {
        setError("일정 ID가 제공되지 않았습니다.");
        setIsLoading(false);
        return;
      }

      try {
        setIsLoading(true);
        const scheduleData = await getScheduleById(parseInt(scheduleId));
        setSchedule(scheduleData);

        // 비대면 여부 확인
        if (scheduleData.location?.toLowerCase() === '비대면') {
          setIsRemoteEvent(true);
          console.log('🚦 [DEBUG] 비대면 일정 확인됨', scheduleData.title);
        } else {
          setIsRemoteEvent(false);
        }

        // 루틴 정보 로드
        if (scheduleData.routineId) {
          try {
            const routineData = await getRoutineById(scheduleData.routineId);
            setRoutineDetails(routineData);
          } catch (routineError) {
            console.error("루틴 정보 로드 실패:", routineError);
          }
        }

        setIsLoading(false);
      } catch (err) {
        console.error("일정 정보 로드 실패:", err);
        setError("일정 정보를 불러오는 데 실패했습니다.");
        setIsLoading(false);
      }
    };

    fetchScheduleData();
  }, [scheduleId]);

  // 일정 위치 정보를 바탕으로 이동 시간 계산
  useEffect(() => {
    // 비대면 일정이거나, 스케줄 데이터가 없거나, 로딩 중이면 계산하지 않음
    if (isRemoteEvent || !schedule || isLoading) {
      setIsTransportLoading(false);
      return;
    }

    // 애니메이션 초기 상태 설정
    setShowTransportTimes(false);

    // 출발지와 목적지 좌표가 있는지 확인
    const hasStartCoords = schedule.startX != null && schedule.startY != null;
    const hasDestCoords = schedule.destinationX != null && schedule.destinationY != null;

    console.log('🚦 [DEBUG] 일정 좌표 정보:', {
      scheduleId: schedule.id,
      title: schedule.title,
      hasStartCoords,
      hasDestCoords,
      startX: schedule.startX,
      startY: schedule.startY,
      destX: schedule.destinationX,
      destY: schedule.destinationY,
      location: schedule.location
    });

    // 위치 정보가 있는 경우에만 이동시간 계산
    if ((hasStartCoords && hasDestCoords) || (schedule.location && schedule.location !== '비대면')) {
      setIsTransportLoading(true);
      console.log('🚦 [DEBUG] 이동시간 계산 시작...');

      // 이동시간 계산 함수
      const calculateTimes = async () => {
        try {
          let startX, startY, endX, endY;

          // 1. 출발지 좌표 설정
          if (hasStartCoords) {
            // 일정에 저장된 출발지 좌표 사용
            startX = schedule.startX;
            startY = schedule.startY;
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
            endX = schedule.destinationX;
            endY = schedule.destinationY;
            console.log("🚦 [DEBUG] 일정의 목적지 좌표 사용:", endX, endY);
          } else if (schedule.location && schedule.location !== '비대면') {
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

          // 잘못된 좌표값 체크 (null, undefined, NaN)
          if (!startX || !startY || !endX || !endY ||
              isNaN(Number(startX)) || isNaN(Number(startY)) ||
              isNaN(Number(endX)) || isNaN(Number(endY))) {
            console.error("🚦 [ERROR] 유효하지 않은 좌표값:", { startX, startY, endX, endY });
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
      if (schedule.location === '비대면') {
        console.log('🚦 [DEBUG] 비대면 일정은 이동시간 계산을 건너뜀');
      } else {
        console.log('🚦 [DEBUG] 좌표 정보 부족으로 이동시간 계산 건너뜀');
      }
      setIsTransportLoading(false);
    }
  }, [schedule, isLoading, isRemoteEvent]);

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

  // Tmap 링크 생성 함수
  const generateTmapDirectionLink = (schedule: ScheduleType | null) => {
    if (!schedule || !schedule.location) return "#";

    // 출발지와 목적지 좌표가 있는지 확인
    const hasStartCoords = schedule.startX != null && schedule.startY != null;
    const hasDestCoords = schedule.destinationX != null && schedule.destinationY != null;

    // 좌표가 있으면 해당 좌표로 Tmap 길찾기 링크 생성
    if (hasStartCoords && hasDestCoords) {
      return `https://map.kakao.com/?map_type=TYPE_MAP&target=car&rt=${schedule.startY},${schedule.startX},${schedule.destinationY},${schedule.destinationX}&rt1=${encodeURIComponent(schedule.startLocation || '출발지')}&rt2=${encodeURIComponent(schedule.location || '도착지')}&rtIds=,&rtTypes=,`;
    }

    // 좌표가 없으면 장소 이름만으로 링크 생성
    return `https://map.kakao.com/?map_type=TYPE_MAP&target=car&q=${encodeURIComponent(schedule.location)}`;
  };

  // 일정 삭제 핸들러
  const handleDeleteSchedule = () => {
    if (!scheduleId) {
      alert('일정 ID가 없습니다.');
      return;
    }

    setIsDeletePopupOpen(true);
  };

  // 삭제 확인 시 실행될 함수
  const confirmDelete = async () => {
    if (!scheduleId) return;

    setLoading(true);

    try {
      await deleteSchedule(scheduleId);
      //alert("일정이 삭제되었습니다.");
      router.push('/calendar');
    } catch (error) {
      console.error("일정 삭제 실패:", error);
      alert("일정 삭제에 실패했습니다.");
    } finally {
      setLoading(false);
      setIsDeletePopupOpen(false);
    }
  };

  // 날짜 포맷팅 함수
  const formatDate = (dateString: string) => {
    try {
      const date = new Date(dateString);
      return format(date, "yyyy년 M월 d일");
    } catch (error) {
      return "날짜 정보 없음";
    }
  };

  // 시간 포맷팅 함수
  const formatTime = (dateString: string) => {
    try {
      const date = new Date(dateString);
      return format(date, "HH:mm");
    } catch (error) {
      return "--:--";
    }
  };

  // 검색 제출 핸들러 추가
  const handleSearchSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (keyword.trim()) {
      const encodedKeyword = encodeURIComponent(keyword.trim());
      const chatUrl = `/chat?keyword=${encodedKeyword}`;
      router.push(chatUrl);
    }
  };

  if (isLoading) {
    return (
      <div className="flex flex-col w-full h-full">
        <NavBar title="일정상세" link="/calendar"></NavBar>
        <div className="w-full h-full flex items-center justify-center">
          <p>일정 정보를 불러오는 중...</p>
        </div>
      </div>
    );
  }

  if (error || !schedule) {
    return (
      <div className="flex flex-col w-full h-full">
        <NavBar title="일정상세" link="/calendar"></NavBar>
        <div className="w-full h-full flex items-center justify-center">
          <p>{error || "일정을 찾을 수 없습니다."}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col w-full h-full">
      <NavBar title="일정상세" link="/calendar"></NavBar>
      <div className="absolute bottom-[0px] left-[0px] grid grid-cols-2 w-full bg-[#fff] p-[12px] gap-[12px]">
        <button
          className="hover:opacity-[0.7] cursor-pointer py-[10px] px-[5px] bg-[#fff] border-[1px] border-[#01274F] rounded-[7px] text-[#01274F] text-[15px] tracking-[-0.6px] font-[500]"
          onClick={handleDeleteSchedule}
          type="button"
        >
          일정 삭제
        </button>
        <button
          className="hover:opacity-[0.7] cursor-pointer py-[10px] px-[5px] bg-[#01274F] border-[1px] border-[#01274F] rounded-[7px] text-[#fff] text-[15px] tracking-[-0.6px] font-[500]"
          onClick={() => {
            router.push(`/calendar/edit?id=${schedule?.id}`);
          }}
          type="button"
        >
          일정 수정
        </button>
      </div>
      <div className="w-full max-h-full overflow-y-auto pb-[100px]">
        <div className="flex flex-col items-center justify-start p-[20px] w-full h-auto">
          {/* 프롬프트 입력창 */}
          <form
            className="relative w-full 2xl:max-w-[781px] mb-[22px]"
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

          {/* 일정 상세 정보 */}
          <div className="flex justify-between items-end w-full mb-[8px] px-[5px]">
            <p className="text-[#01274F] text-[19px] font-[700] tracking-[-0.4px]">
              {formatDate(schedule.startTime)}
            </p>
          </div>
          <div className="w-full bg-[#fff] p-[15px] rounded-[6px] shadow-[0px_0px_5px_rgba(0,0,0,0.2)] mb-[22px]">
            <div className="flex justify-between items-center w-full mb-[8px]">
              <p className="text-[#383838] text-[17px] font-[500] tracking-[-0.4px] leading-[155%] line-clamp-1">
                {schedule.title}
              </p>
              <div className="flex items-center gap-x-[1px]">
                <img src="/icon/clock.svg" alt="시계 아이콘" />
                <p className="text-[#0080FF] text-[16px] font-[500] tracking-[-0.4px] leading-[160%]">
                  {formatTime(schedule.startTime)}
                </p>
              </div>
            </div>
            {schedule.location && (
              <p className="text-[#383838] text-[15px] font-[400] tracking-[-0.1px] leading-[160%] line-clamp-1">
                장소 : <span>{schedule.location}</span>
              </p>
            )}
            {routineDetails && (
              <p className="text-[#383838] text-[15px] font-[400] tracking-[-0.1px] leading-[160%] line-clamp-1">
                루틴 :{" "}
                <span className="font-[500] tracking-[-0.4px] bg-[#0080FF] text-[#fff] px-[7px] rounded-[10px] leading-[120%]">
                  {routineDetails.name || "루틴 정보 없음"}
                </span>
              </p>
            )}
            {schedule.supplies && (
              <p className="text-[#383838] text-[15px] font-[400] tracking-[-0.1px] leading-[160%]">
                준비물 : <span>{schedule.supplies}</span>
                {schedule.category === "OUTDOOR" && (
                  <span className="font-[600]">, 우산(자동추가)</span>
                )}
              </p>
            )}
            {schedule.memo && (
              <p className="text-[#383838] text-[15px] font-[400] tracking-[-0.1px] leading-[160%]">
                메모 : <span>{schedule.memo}</span>
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
                          <use xlinkHref="#image0_298_2791" transform="scale(0.00195312)" />
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
                          <use xlinkHref="#image0_298_2792" transform="scale(0.00195312)" />
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
                          <use xlinkHref="#image0_298_2793" transform="scale(0.00195312)" />
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
                {schedule && schedule.location && (
                  <Link
                    href={generateTmapDirectionLink(schedule)}
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
                      <rect width="22" height="22" fill="url(#pattern0_298_2746dafasdfa)" />
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
                )}
              </>
            )}
            {/* 루틴 정보 섹션 */}
            {routineDetails && routineDetails.items && routineDetails.items.length > 0 && (
              <>
                <div className="mt-[20px] mb-[13px] w-full h-[1px] bg-[#dfdfdf]"></div>
                <div className="flex justify-between items-center w-full mb-[8px]">
                  <p className="text-[#383838] text-[17px] font-[600] tracking-[-0.4px] leading-[110%] line-clamp-1">
                    설정된 루틴
                  </p>
                  <div className="flex items-center gap-x-[1px]">
                    <span className="text-[#01274f] text-[15px] font-[600] tracking-[-0.8px] leading-[102%] line-clamp-1">
                      {routineDetails.name} ({routineDetails.totalDurationMinutes ||
                        routineDetails.items.reduce((sum, item) => sum + item.durationMinutes, 0)}분)
                    </span>
                  </div>
                </div>
                <div className="flex flex-col gap-[9px]">
                  {routineDetails.items.map((item) => {
                    return (
                      <div
                        key={item.id}
                        className={`w-full h-auto px-[10px] py-[5px] bg-[#0080FF]/40 rounded-[6px] flex items-center justify-between`}
                      >
                        <p className="text-[#fff] text-[14px] font-[600] tracking-[-0.5px] leading-[102%] line-clamp-1">
                          {item.name}
                        </p>
                        <div className="flex items-center gap-x-[1px]">
                          <span>⏭️&nbsp;</span>
                          <span className="text-[#fff] text-[15px] font-[600] tracking-[-0.8px] leading-[102%] line-clamp-1">
                            {item.durationMinutes}분
                          </span>
                          <span className="text-[#fff] text-[15px] font-[600] tracking-[-0.8px] leading-[102%] line-clamp-1">
                            &nbsp;대기 중
                          </span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      {/* 일정 삭제 확인 팝업 */}
      <ConfirmPopup
        isOpen={isDeletePopupOpen}
        message={schedule ? `스케줄 '${schedule.title}'을 삭제하시겠습니까?` : '이 일정을 삭제하시겠습니까?'}
        onConfirm={confirmDelete}
        onCancel={() => setIsDeletePopupOpen(false)}
      />
    </div>
  );
}

