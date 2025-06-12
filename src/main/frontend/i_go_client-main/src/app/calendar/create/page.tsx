"use client";
import NavBar from "@/components/common/topNav";
import React, { useState, useEffect } from "react";
import { createSchedule } from "@/api/scheduleApi";
import { getRoutineNames } from "@/api/routineApi";
import { useRouter } from "next/navigation";
import AddressSearch from "@/components/common/AddressSearch";
import KakaoMapScript from "@/components/common/KakaoMapScript";

interface RoutineName {
  id: number;
  name: string;
}

interface ValidationErrors {
  title?: string;
  startDate?: string;
  startTime?: string;
  endDate?: string;
  endTime?: string;
  dateTime?: string;
  routine?: string;
}

export default function CreateSchedule() {
  const router = useRouter();
  const [selectedRoutine, setSelectedRoutine] = useState<string>("");
  const [routines, setRoutines] = useState<RoutineName[]>([]);
  const [formData, setFormData] = useState({
    title: "",
    startDate: "",
    startTime: "",
    endDate: "",
    endTime: "",
    startLocation: "",
    startX: 0,
    startY: 0,
    location: "",
    destinationX: 0,
    destinationY: 0,
    supplies: "",
    memo: "",
    category: "PERSONAL",
    isOnline: false
  });
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<ValidationErrors>({});
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [showSubmitErrors, setShowSubmitErrors] = useState(false);
  const [startLocationSelected, setStartLocationSelected] = useState(false);
  const [destinationSelected, setDestinationSelected] = useState(false);

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

  // 유효성 검사 함수
  const validateField = (name: string, value: string, formData: any, selectedRoutine: string): string | undefined => {
    switch (name) {
      case 'title':
        if (!value.trim()) {
          return '일정 제목을 입력해주세요.';
        }
        break;
      case 'startDate':
        if (!value) {
          return '시작 날짜를 선택해주세요.';
        }
        break;
      case 'startTime':
        if (!value) {
          return '시작 시간을 입력해주세요.';
        }
        break;
      case 'endDate':
        if (!value) {
          return '종료 날짜를 선택해주세요.';
        }
        break;
      case 'endTime':
        if (!value) {
          return '종료 시간을 입력해주세요.';
        }
        break;
      case 'routine':
        if (!selectedRoutine) {
          return '루틴이 선택되어 있지 않습니다.';
        }
        break;
      case 'dateTime':
        // 시작일시와 종료일시 비교 검사
        if (formData.startDate && formData.startTime && formData.endDate && formData.endTime) {
          const startDateTime = new Date(`${formData.startDate}T${formData.startTime}`);
          const endDateTime = new Date(`${formData.endDate}T${formData.endTime}`);
          if (endDateTime <= startDateTime) {
            return '종료일시는 시작일시보다 늦어야 합니다.';
          }
        }
        break;
    }
    return undefined;
  };

  // 전체 폼 유효성 검사
  const validateForm = () => {
    const newErrors: ValidationErrors = {};

    // 각 필수 필드 검사
    newErrors.title = validateField('title', formData.title, formData, selectedRoutine);
    newErrors.startDate = validateField('startDate', formData.startDate, formData, selectedRoutine);
    newErrors.startTime = validateField('startTime', formData.startTime, formData, selectedRoutine);
    newErrors.endDate = validateField('endDate', formData.endDate, formData, selectedRoutine);
    newErrors.endTime = validateField('endTime', formData.endTime, formData, selectedRoutine);
    newErrors.routine = validateField('routine', '', formData, selectedRoutine);
    newErrors.dateTime = validateField('dateTime', '', formData, selectedRoutine);

    // undefined 값 제거
    Object.keys(newErrors).forEach(key => {
      if (newErrors[key as keyof ValidationErrors] === undefined) {
        delete newErrors[key as keyof ValidationErrors];
      }
    });

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  // 실시간 유효성 검사
  useEffect(() => {
    if (Object.keys(touched).length > 0) {
      validateForm();
    }
  }, [formData, selectedRoutine, touched]);

  // 제출 버튼 활성화 상태 확인
  const isFormValid = () => {
    // 기본 폼 유효성 검사
    const basicValidation = formData.title.trim() !== '' &&
        formData.startDate !== '' &&
        formData.startTime !== '' &&
        formData.endDate !== '' &&
        formData.endTime !== '' &&
        selectedRoutine !== '' &&
        Object.keys(errors).length === 0;

    // 비대면 일정인 경우는 기본 검증만 진행
    if (formData.isOnline) {
      return basicValidation;
    }

    // 비대면이 아닌 경우, 출발지와 목적지가 모두 선택되었는지 확인
    return basicValidation &&
           startLocationSelected &&
           destinationSelected;
  };

  const handleFieldTouch = (fieldName: string) => {
    setTouched(prev => ({ ...prev, [fieldName]: true }));
  };

  const handleRoutineChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setSelectedRoutine(e.target.value);
    handleFieldTouch('routine');
    console.log('선택된 루틴:', e.target.value);
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value, type, checked } = e.target;

    // 체크박스(isOnline)가 변경되었을 때 location 필드도 함께 업데이트
    if (name === 'isOnline') {
      setFormData(prev => ({
        ...prev,
        [name]: checked,
        // 체크박스가 체크되면 location을 '비대면'으로 설정, 아니면 빈 문자열로 초기화
        location: checked ? '비대면' : '',
        // 온라인일 경우 출발지 정보도 초기화
        startLocation: checked ? '' : prev.startLocation,
        startX: checked ? 0 : prev.startX,
        startY: checked ? 0 : prev.startY,
        destinationX: checked ? 0 : prev.destinationX,
        destinationY: checked ? 0 : prev.destinationY
      }));

      // 주소 선택 상태도 초기화
      if (checked) {
        setStartLocationSelected(false);
        setDestinationSelected(false);
      }
    } else {
      setFormData(prev => ({
        ...prev,
        [name]: type === 'checkbox' ? checked : value
      }));
    }

    handleFieldTouch(name);
  };

  // 출발지 주소 선택 핸들러
  const handleStartAddressSelect = (address: string, x: number, y: number) => {
    if (formData.isOnline) return; // 비대면인 경우 무시

    console.log('선택된 출발지 주소:', address, '좌표:', x, y);
    setFormData(prev => ({
      ...prev,
      startLocation: address,
      startX: x,
      startY: y
    }));
    setStartLocationSelected(true);
  };

  // 도착지 주소 선택 핸들러
  const handleDestinationAddressSelect = (address: string, x: number, y: number) => {
    if (formData.isOnline) return; // 비대면인 경우 무시

    console.log('선택된 도착지 주소:', address, '좌표:', x, y);
    setFormData(prev => ({
      ...prev,
      location: address,
      destinationX: x,
      destinationY: y
    }));
    setDestinationSelected(true);
  };

  // 로컬 시간을 그대로 유지하는 ISO 문자열 생성 함수
  const createLocalISOString = (dateStr: string, timeStr: string): string => {
    return `${dateStr}T${timeStr}:00`;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setShowSubmitErrors(true);

    // 모든 필드를 touched로 설정
    setTouched({
      title: true,
      startDate: true,
      startTime: true,
      endDate: true,
      endTime: true,
      routine: true
    });

    // 유효성 검사
    if (!validateForm()) {
      return;
    }

    setLoading(true);

    try {
      // 시간대 변환 없이 로컬 시간 그대로 사용
      const startDateTime = createLocalISOString(formData.startDate, formData.startTime);
      const endDateTime = createLocalISOString(formData.endDate, formData.endTime);

      const scheduleData = {
        routineId: selectedRoutine ? parseInt(selectedRoutine) : null,
        title: formData.title,
        startTime: startDateTime,
        endTime: endDateTime,
        startLocation: formData.isOnline ? "" : formData.startLocation,
        startX: formData.isOnline ? 0 : formData.startX,
        startY: formData.isOnline ? 0 : formData.startY,
        location: formData.isOnline ? "비대면" : formData.location,
        destinationX: formData.isOnline ? 0 : formData.destinationX,
        destinationY: formData.isOnline ? 0 : formData.destinationY,
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
        startLocation: "",
        startX: 0,
        startY: 0,
        location: "",
        destinationX: 0,
        destinationY: 0,
        supplies: "",
        memo: "",
        category: "PERSONAL",
        isOnline: false
      });
      setSelectedRoutine("");
      setErrors({});
      setTouched({});
      setShowSubmitErrors(false);
      setStartLocationSelected(false);
      setDestinationSelected(false);

    } catch (error) {
      console.error('일정 등록 실패:', error);
      alert('일정 등록에 실패했습니다. 다시 시도해주세요.');
    } finally {
      setLoading(false);
    }
  };

  // 오류 메시지 표시 여부 결정
  const shouldShowError = (fieldName: keyof ValidationErrors) => {
    return (touched[fieldName] || showSubmitErrors) && errors[fieldName];
  };

  // 필드 테두리 색상 결정
  const getFieldBorderClass = (fieldName: keyof ValidationErrors) => {
    if (shouldShowError(fieldName)) {
      return "border-red-500 focus:border-red-500";
    }
    return "border-[#DFDFDF] focus:border-[#383838]";
  };

  return (
      <div className="flex flex-col w-full h-full">
        <NavBar title="일정 등록" link="/mypage"></NavBar>
        <KakaoMapScript /> {/* 명시적으로 컴포넌트 추가 */}
        <div className="w-full max-h-full overflow-y-auto">
          <div className="flex flex-col items-center justify-start p-[20px] w-full h-auto">
            <div className="w-full shadow-[0px_0px_10px_rgba(0,0,0,0.2)] bg-[#fff] p-[20px]">
              <form onSubmit={handleSubmit} className="search-htmlForm">
                <div className="flex flex-col items-center justify-center gap-y-[8px] w-full">
                  <div className="grid grid-cols-1 2xl:grid-cols-3 flex-col 2xl:flex-row flex-wrap items-center gap-[20px] w-full">

                    {/* 일정 제목 */}
                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        일정 제목 <span className="text-red-500">*</span>
                      </p>
                      <input
                          type="text"
                          name="title"
                          value={formData.title}
                          onChange={handleInputChange}
                          onBlur={() => handleFieldTouch('title')}
                          required
                          placeholder="일정명을 입력해주세요."
                          className={`text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] py-[8px] px-[15px] rounded-[4px] outline-none ${getFieldBorderClass('title')}`}
                      />
                      {shouldShowError('title') && (
                          <p className="text-red-500 text-[11px] mt-1">{errors.title}</p>
                      )}
                    </div>

                    {/* 시작일시 */}
                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        시작일시 <span className="text-red-500">*</span>
                      </p>
                      <div className="flex justify-between items-center w-full gap-x-[8px]">
                        <div className="w-full">
                          <input
                              type="date"
                              name="startDate"
                              value={formData.startDate}
                              onChange={handleInputChange}
                              onBlur={() => handleFieldTouch('startDate')}
                              required
                              className={`text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] py-[8px] px-[15px] rounded-[4px] outline-none ${getFieldBorderClass('startDate')}`}
                          />
                        </div>
                        <div className="w-full">
                          <input
                              type="time"
                              name="startTime"
                              value={formData.startTime}
                              onChange={handleInputChange}
                              onBlur={() => handleFieldTouch('startTime')}
                              required
                              className={`text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] py-[8px] px-[15px] rounded-[4px] outline-none ${getFieldBorderClass('startTime')}`}
                          />
                        </div>
                      </div>
                      {(shouldShowError('startDate') || shouldShowError('startTime')) && (
                          <p className="text-red-500 text-[11px] mt-1">
                            {errors.startDate || errors.startTime}
                          </p>
                      )}
                    </div>

                    {/* 종료일시 */}
                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        종료일시 <span className="text-red-500">*</span>
                      </p>
                      <div className="flex justify-between items-center w-full gap-x-[8px]">
                        <div className="w-full">
                          <input
                              type="date"
                              name="endDate"
                              value={formData.endDate}
                              onChange={handleInputChange}
                              onBlur={() => handleFieldTouch('endDate')}
                              required
                              className={`text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] py-[8px] px-[15px] rounded-[4px] outline-none ${getFieldBorderClass('endDate')}`}
                          />
                        </div>
                        <div className="w-full">
                          <input
                              type="time"
                              name="endTime"
                              value={formData.endTime}
                              onChange={handleInputChange}
                              onBlur={() => handleFieldTouch('endTime')}
                              required
                              className={`text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] py-[8px] px-[15px] rounded-[4px] outline-none ${getFieldBorderClass('endTime')}`}
                          />
                        </div>
                      </div>
                      {(shouldShowError('endDate') || shouldShowError('endTime') || shouldShowError('dateTime')) && (
                          <p className="text-red-500 text-[11px] mt-1">
                            {errors.endDate || errors.endTime || errors.dateTime}
                          </p>
                      )}
                    </div>

                    {/* 출발지 */}
                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        출발지
                      </p>
                      <div className="relative">
                        <AddressSearch
                          onAddressSelect={handleStartAddressSelect}
                          placeholder="출발지 주소를 검색하세요."
                          disabled={formData.isOnline}
                          className={`text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] rounded-[4px] focus:border-[#383838] outline-none ${formData.isOnline ? 'bg-gray-100' : ''}`}
                        />
                        {startLocationSelected && !formData.isOnline && (
                          <div className="absolute right-3 top-1/2 transform -translate-y-1/2">
                            <span className="text-green-600 text-sm">✓ 선택 완료</span>
                          </div>
                        )}
                      </div>
                      {/* 숨겨진 좌표 필드 */}
                      <input type="hidden" name="startX" value={formData.startX} />
                      <input type="hidden" name="startY" value={formData.startY} />
                    </div>

                    {/* 도착지 */}
                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        도착지
                      </p>
                      <div className="relative">
                        <AddressSearch
                          onAddressSelect={handleDestinationAddressSelect}
                          placeholder="도착지 주소를 검색하세요."
                          disabled={formData.isOnline}
                          className={`text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] rounded-[4px] focus:border-[#383838] outline-none ${formData.isOnline ? 'bg-gray-100' : ''}`}
                        />
                        {destinationSelected && !formData.isOnline && (
                          <div className="absolute right-3 top-1/2 transform -translate-y-1/2">
                            <span className="text-green-600 text-sm">✓ 선택 완료</span>
                          </div>
                        )}
                      </div>
                      {/* 숨겨진 좌표 필드 */}
                      <input type="hidden" name="destinationX" value={formData.destinationX} />
                      <input type="hidden" name="destinationY" value={formData.destinationY} />
                    </div>

                    {/* 루틴 선택 */}
                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        루틴 선택 <span className="text-red-500">*</span>
                        <span className="text-[10px] text-gray-500 ml-1">
                        ({routines.length}개)
                      </span>
                      </p>
                      <div className="group relative flex w-full justify-between items-center overflow-hidden">
                        <select
                            value={selectedRoutine}
                            onChange={handleRoutineChange}
                            onBlur={() => handleFieldTouch('routine')}
                            className={`appearance-none bg-transparent w-full h-full outline-none border-[1px] pl-[15px] pr-[42px] py-[8px] text-[13px] rounded-[4px] ${getFieldBorderClass('routine')}`}
                        >
                          <option value="">루틴을 선택하세요.</option>
                          {routines && routines.length > 0 ? (
                              routines.map((routine: RoutineName) => {
                                console.log('렌더링할 루틴:', routine);
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
                      {shouldShowError('routine') && (
                          <p className="text-red-500 text-[11px] mt-1">{errors.routine}</p>
                      )}
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
                        <span className="
                          absolute top-[50%] translate-y-[-50%] left-0
                          bg-[#fff] peer-checked:bg-[#2155A0]
                          border-[1px] border-[#949494] peer-checked:border-[#2155A0]
                          w-[16px] h-[16px]
                          peer-checked:after:content-[''] peer-checked:after:absolute
                          peer-checked:after:left-[50%] peer-checked:after:top-[50%]
                          peer-checked:after:w-[6px] peer-checked:after:h-[9px]
                          peer-checked:after:mt-[-6px] peer-checked:after:ml-[-3px]
                          peer-checked:after:border-r-[2px] peer-checked:after:border-r-[#fff]
                          peer-checked:after:border-b-[2px] peer-checked:after:border-b-[#fff]
                          peer-checked:after:rotate-[40deg]"
                        />
                      </label>
                      <label className="text-[13px] leading-[16px] tracking-[-0.4px] text-[#777]">
                        비대면 일정
                      </label>
                    </div>

                    <div className="flex flex-row items-center justify-center gap-x-[12px] w-full pt-[20px]">
                      <button
                          type="submit"
                          disabled={loading || !isFormValid()}
                          className={`flex justify-center min-w-[115px] py-[10px] px-[20px] rounded-[4px] transition-all duration-200 ${
                              isFormValid() && !loading
                                  ? 'bg-[#01274F] hover:bg-[#01274F]/90 cursor-pointer'
                                  : 'bg-gray-400 cursor-not-allowed'
                          }`}
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
