"use client";
import NavBar from "@/components/common/topNav";
import React, { useState, useEffect } from "react";
import { createSchedule } from "@/api/scheduleApi";
import { getRoutineNames } from "@/api/routineApi";
import {router} from "next/client";

interface RoutineName {
  id: number;
  name: string;
}

export default function CreateSchedule() {
  const [selectedRoutine, setSelectedRoutine] = useState<string>("");
  const [routines, setRoutines] = useState<RoutineName[]>([]);
  const [formData, setFormData] = useState({
    title: "",
    startDate: "",
    startTime: "",
    endDate: "",
    endTime: "",
    location: "",
    supplies: "",
    memo: "",
    category: "PERSONAL",
    isOnline: false
  });
  const [loading, setLoading] = useState(false);

  // 루틴 목록 로드
  useEffect(() => {
    const loadRoutines = async () => {
      try {
        const routineData = await getRoutineNames();
        console.log('원본 루틴 데이터:', routineData);
        console.log('데이터 타입:', typeof routineData);
        console.log('배열인지 확인:', Array.isArray(routineData));

        // 데이터가 배열인지 확인
        if (!Array.isArray(routineData)) {
          console.error('루틴 데이터가 배열이 아닙니다:', routineData);
          setRoutines([]);
          return;
        }

        // null이나 undefined 값들을 필터링 (타입 명시)
        const validRoutines = routineData.filter(
            (routine: any): routine is RoutineName => {
              const isValid = routine !== null &&
                  routine !== undefined &&
                  typeof routine.id === 'number' &&
                  typeof routine.name === 'string' &&
                  routine.name.trim() !== '';

              if (!isValid) {
                console.log('유효하지 않은 루틴:', routine);
              }
              return isValid;
            }
        );

        console.log('필터링된 루틴:', validRoutines);
        setRoutines(validRoutines);
      } catch (error) {
        console.error('루틴 목록 로드 실패:', error);
        setRoutines([]);
      }
    };
    loadRoutines();
  }, []);

  // 루틴 상태 변화 감지 (디버깅용)
  useEffect(() => {
    console.log('현재 루틴 상태:', routines);
    console.log('루틴 개수:', routines.length);
  }, [routines]);

  const handleRoutineChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setSelectedRoutine(e.target.value);
    console.log('선택된 루틴:', e.target.value);
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value, type, checked } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));
  };

  // 로컬 시간을 그대로 유지하는 ISO 문자열 생성 함수
  const createLocalISOString = (dateStr: string, timeStr: string): string => {
    // Date 객체를 사용하지 않고 직접 ISO 형식으로 변환
    return `${dateStr}T${timeStr}:00`;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    try {
      // 시간 유효성 검사
      if (!formData.startDate || !formData.startTime || !formData.endDate || !formData.endTime) {
        alert('시작일시와 종료일시를 모두 입력해주세요.');
        return;
      }

      // 시간대 변환 없이 로컬 시간 그대로 사용
      const startDateTime = createLocalISOString(formData.startDate, formData.startTime);
      const endDateTime = createLocalISOString(formData.endDate, formData.endTime);

      // 종료시간이 시작시간보다 빠른지 검사
      if (new Date(endDateTime) <= new Date(startDateTime)) {
        alert('종료일시는 시작일시보다 늦어야 합니다.');
        return;
      }

      const scheduleData = {
        routineId: selectedRoutine ? parseInt(selectedRoutine) : null,
        title: formData.title,
        startTime: startDateTime,
        endTime: endDateTime,
        location: formData.location,
        memo: formData.memo,
        supplies: formData.supplies,
        category: formData.category
      };

      console.log('전송할 일정 데이터:', scheduleData);
      console.log('변환된 시작시간:', startDateTime);
      console.log('변환된 종료시간:', endDateTime);

      await createSchedule(scheduleData);
      alert('일정이 성공적으로 등록되었습니다!');
      router.push('/calendar');

      // 폼 초기화
      setFormData({
        title: "",
        startDate: "",
        startTime: "",
        endDate: "",
        endTime: "",
        location: "",
        supplies: "",
        memo: "",
        category: "PERSONAL",
        isOnline: false
      });
      setSelectedRoutine("");

    } catch (error) {
      console.error('일정 등록 실패:', error);
      alert('일정 등록에 실패했습니다. 다시 시도해주세요.');
    } finally {
      setLoading(false);
    }
  };

  return (
      <div className="flex flex-col w-full h-full">
        <NavBar title="일정 등록" link="/mypage"></NavBar>
        <div className="w-full max-h-full overflow-y-auto">
          <div className="flex flex-col items-center justify-start p-[20px] w-full h-auto">
            <div className="w-full shadow-[0px_0px_10px_rgba(0,0,0,0.2)] bg-[#fff] p-[20px]">
              <form onSubmit={handleSubmit} className="search-htmlForm">
                <div className="flex flex-col items-center justify-center gap-y-[8px] w-full">
                  <div className="grid grid-cols-1 2xl:grid-cols-3 flex-col 2xl:flex-row flex-wrap items-center gap-[20px] w-full">

                    {/* 일정 제목 */}
                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        일정 제목
                      </p>
                      <input
                          type="text"
                          name="title"
                          value={formData.title}
                          onChange={handleInputChange}
                          required
                          placeholder="일정명을 입력해주세요."
                          className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] rounded-[4px] focus:border-[#383838] outline-none"
                      />
                    </div>

                    {/* 시작일시 */}
                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        시작일시
                      </p>
                      <div className="flex justify-between items-center w-full gap-x-[8px]">
                        <input
                            type="date"
                            name="startDate"
                            value={formData.startDate}
                            onChange={handleInputChange}
                            required
                            className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] rounded-[4px] focus:border-[#383838] outline-none"
                        />
                        <input
                            type="time"
                            name="startTime"
                            value={formData.startTime}
                            onChange={handleInputChange}
                            required
                            className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] rounded-[4px] focus:border-[#383838] outline-none"
                        />
                      </div>
                    </div>

                    {/* 종료일시 */}
                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        종료일시
                      </p>
                      <div className="flex justify-between items-center w-full gap-x-[8px]">
                        <input
                            type="date"
                            name="endDate"
                            value={formData.endDate}
                            onChange={handleInputChange}
                            required
                            className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] rounded-[4px] focus:border-[#383838] outline-none"
                        />
                        <input
                            type="time"
                            name="endTime"
                            value={formData.endTime}
                            onChange={handleInputChange}
                            required
                            className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] rounded-[4px] focus:border-[#383838] outline-none"
                        />
                      </div>
                    </div>

                    {/* 장소 */}
                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        장소
                      </p>
                      <input
                          type="text"
                          name="location"
                          value={formData.location}
                          onChange={handleInputChange}
                          placeholder="일정 장소를 입력해주세요."
                          className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] rounded-[4px] focus:border-[#383838] outline-none"
                      />
                    </div>

                    {/* 루틴 선택 */}
                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        루틴 선택
                        <span className="text-[10px] text-gray-500 ml-1">
                        ({routines.length}개)
                      </span>
                      </p>
                      <div className="group relative flex w-full justify-between items-center overflow-hidden">
                        <select
                            value={selectedRoutine}
                            onChange={handleRoutineChange}
                            className="appearance-none bg-transparent w-full h-full outline-none border-[1px] border-[#DFDFDF] focus:border-[#383838] pl-[15px] pr-[42px] py-[8px] text-[13px] rounded-[4px]"
                        >
                          <option value="">루틴을 선택하세요.</option>
                          {routines && routines.length > 0 ? (
                              routines.map((routine: RoutineName) => {
                                console.log('렌더링할 루틴:', routine); // 각 루틴 렌더링 시 로그
                                return (
                                    <option key={routine.id} value={routine.id.toString()}>
                                      {routine.name}
                                    </option>
                                );
                              })
                          ) : (
                              <option disabled>
                                {routines.length === 0 ? "루틴이 없습니다" : "로딩 중..."}
                              </option>
                          )}
                        </select>
                        <div className="group-hover:rotate-180 duration-300 absolute right-[15px]">
                          <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" fill="none">
                            <g clipPath="url(#a)">
                              <path
                                  fill="#383838"
                                  d="M5.572 9.12a.612.612 0 0 0 .857 0l5.394-5.381a.604.604 0 1 0-.856-.855L6 7.837 1.034 2.883a.604.604 0 1 0-.857.855l5.395 5.381Z"
                              />
                            </g>
                            <defs>
                              <clipPath id="a">
                                <path fill="#fff" d="M12 12H0V0h12z" />
                              </clipPath>
                            </defs>
                          </svg>
                        </div>
                      </div>
                    </div>

                    {/* 준비물 */}
                    <div className="relative">
                      <p className="text-[#383838] text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        준비물
                      </p>
                      <input
                          type="text"
                          name="supplies"
                          value={formData.supplies}
                          onChange={handleInputChange}
                          placeholder="준비물을 입력해주세요."
                          className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] rounded-[4px] focus:border-[#383838] outline-none"
                      />
                    </div>

                    {/* 메모 */}
                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        메모
                      </p>
                      <input
                          type="text"
                          name="memo"
                          value={formData.memo}
                          onChange={handleInputChange}
                          placeholder="메모를 입력해주세요."
                          className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] rounded-[4px] focus:border-[#383838] outline-none"
                      />
                    </div>

                    {/* 카테고리 */}
                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        카테고리
                      </p>
                      <select
                          name="category"
                          value={formData.category}
                          onChange={(e) => setFormData(prev => ({ ...prev, category: e.target.value }))}
                          className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] rounded-[4px] focus:border-[#383838] outline-none"
                      >
                        <option value="WORK">업무</option>
                        <option value="STUDY">공부</option>
                        <option value="EXERCISE">운동</option>
                        <option value="PERSONAL">개인</option>
                        <option value="OTHER">기타</option>
                      </select>
                    </div>
                  </div>

                  <div className="flex flex-col items-start w-full gap-y-[20px] lg:gap-y-0 my-[15px]">
                    <div className="flex flex-row items-center gap-x-[8px]">
                      <label className="relative block min-w-[16px] min-h-[16px] cursor-pointer !mb-0">
                        <input
                            type="checkbox"
                            name="isOnline"
                            checked={formData.isOnline}
                            onChange={handleInputChange}
                            className="peer a11y"
                        />
                        <span className="absolute top-[50%] translate-y-[-50%] left-0 bg-[#fff] peer-checked:bg-[#2155A0] border-[1px] border-[#949494] peer-checked:border-[#2155A0] w-[16px] h-[16px] rounded-[2px]" />
                      </label>
                      <label className="text-[13px] leading-[16px] tracking-[-0.4px] text-[#777]">
                        비대면 일정
                      </label>
                    </div>

                    <div className="flex flex-row items-center justify-center gap-x-[12px] w-full pt-[20px]">
                      <button
                          type="submit"
                          disabled={loading}
                          className="flex justify-center bg-[#01274F] hover:bg-[#01274F]/90 disabled:bg-gray-400 min-w-[115px] py-[10px] px-[20px] rounded-[4px]"
                      >
                      <span className="font-[500] text-[15px] leading-[19px] tracking-[-0.4px] text-[#fff]">
                        {loading ? '등록 중...' : '일정 등록하기'}
                      </span>
                      </button>
                    </div>
                  </div>
                </div>
              </form>
            </div>
          </div>
        </div>
      </div>
  );
}