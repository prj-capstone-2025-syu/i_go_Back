"use client";
import NavBar from "@/components/common/topNav";
import React, { useState, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { getRoutineById, updateRoutine, deleteRoutine } from "@/api/routineApi";
import ConfirmPopup from "@/components/common/ConfirmPopup";
import api from '@/api/axiosConfig';

// 루틴 항목 인터페이스 정의
interface RoutineItem {
  id: number;
  name: string;
  time: string;
  disabled: boolean;
  timeError?: string; // 각 항목별 시간 오류 메시지 추가
}

export default function RoutineDetailPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const routineId = searchParams.get('id');

  const [title, setTitle] = useState<string>("");
  const [routineItems, setRoutineItems] = useState<RoutineItem[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [isDeletePopupOpen, setIsDeletePopupOpen] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState(false); // 저장 로딩 상태 추가

  // 루틴 데이터 불러오기
  useEffect(() => {
    if (!routineId) {
      setError("루틴 ID가 없습니다.");
      setLoading(false);
      return;
    }

    const fetchRoutineData = async () => {
      try {
        setLoading(true);
        const data = await getRoutineById(routineId);

        // 루틴 제목 설정
        setTitle(data.name);

        // 루틴 아이템들 매핑
        const items = data.items.map((item: { id: any; name: any; durationMinutes: { toString: () => any; }; }) => ({
          id: item.id,
          name: item.name,
          time: item.durationMinutes.toString(),
          disabled: false
        }));

        setRoutineItems(items);
        setError(null);
      } catch (err) {
        console.error("루틴 정보 가져오기 실패:", err);
        setError("루틴 정보를 불러오는데 실패했습니다");
      } finally {
        setLoading(false);
      }
    };

    fetchRoutineData();
  }, [routineId]);

  const handleAddRoutineItem = () => {
    setRoutineItems(prevItems => [
      ...prevItems,
      {
        id: Date.now() + prevItems.length,
        name: "",
        time: "",
        disabled: false,
      },
    ]);
  };

  const handleRemoveRoutineItem = (idToRemove: number) => {
    setRoutineItems(prevItems =>
        prevItems.filter(item => item.id !== idToRemove)
    );
  };

  const handleChange = (
      id: number,
      event: React.ChangeEvent<HTMLInputElement>
  ) => {
    const { name, value } = event.target;
    setRoutineItems(prevItems =>
        prevItems.map(item => {
          if (item.id === id) {
            const updatedItem = { ...item, [name]: value };
            if (name === "time") {
              // 빈 문자열인 경우 에러 없음
              if (value === "") {
                updatedItem.timeError = undefined;
              } else {
                const numValue = parseFloat(value);
                // 음수 체크
                if (numValue < 0) {
                  updatedItem.timeError = "시간은 음수가 될 수 없습니다.";
                }
                // 소수점 체크 (정수가 아닌 경우)
                else if (!Number.isInteger(numValue)) {
                  updatedItem.timeError = "정수를 입력해주세요.";
                }
                // 유효한 값인 경우
                else {
                  updatedItem.timeError = undefined;
                }
              }
            }
            return updatedItem;
          }
          return item;
        })
    );
  };

  const handleTitleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setTitle(event.target.value);
  };

  // 루틴 저장 (API 연동)
  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null); // 이전 에러 메시지 초기화

    if (!routineId) {
      setError("루틴 ID가 없습니다");
      return;
    }

    // 기본 유효성 검사
    if (!title.trim()) {
      setError("루틴명을 입력해주세요.");
      return;
    }

    // 할일 항목이 하나도 없는 경우 체크
    if (routineItems.length === 0) {
      setError("최소 하나의 할일을 추가해주세요.");
      return;
    }

    if (routineItems.some(item => !item.name.trim() || !item.time.trim())) {
      setError("모든 할일의 이름과 시간을 입력해주세요.");
      return;
    }

    // handleChange에 의해 설정된 timeError가 있는지 확인
    if (routineItems.some(item => item.timeError)) {
      setError("시간 입력 값 중 유효하지 않은 항목이 있습니다. 각 항목 아래의 메시지를 확인해주세요.");
      return;
    }

    setIsLoading(true);

    try {
      await updateRoutine(routineId, {
        title: title,
        items: routineItems
      });
      router.push("/mypage/routine");
    } catch (err) {
      console.error("루틴 저장 실패:", err);
      setError("루틴 저장에 실패했습니다.");
    } finally {
      setIsLoading(false);
    }
  };

  // 루틴 삭제 (API 연동)
  const handleDelete = async () => {
    if (!routineId) {
      setError("루틴 ID가 없습니다");
      return;
    }

    setIsDeletePopupOpen(true);
  };

  // 루틴 삭제 확인
  const confirmDelete = async () => {
    try {
      await deleteRoutine(routineId!);
      router.push("/mypage/routine");
    } catch (err) {
      console.error("루틴 삭제 실패:", err);
      setError("루틴 삭제에 실패했습니다.");
    } finally {
      setIsDeletePopupOpen(false);
    }
  };

  if (loading) {
    return (
        <div className="flex flex-col w-full h-full">
          <NavBar title="루틴 상세" link="/mypage/routine"></NavBar>
          <div className="flex justify-center items-center h-full">
            <p>로딩 중...</p>
          </div>
        </div>
    );
  }

  return (
      <div className="flex flex-col w-full h-full">
        <NavBar title="루틴 상세" link="/mypage/routine"></NavBar>
        <div className="w-full max-h-full overflow-y-auto">
          <div className="flex flex-col items-center justify-start p-[15px] w-full h-auto">
            <form onSubmit={handleSubmit} className="w-full">
              <div className="w-full mx-auto py-[20px]">
                <div className="igo-form-txt-wrap w-full">
                  <div className="igo-form-txt-wrap w-full">
                    {error && (
                        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4">
                          {error}
                        </div>
                    )}
                    <h2 className="user__title text-[20px] font-bold mb-[12px]">
                      루틴명
                    </h2>
                    <div className="igo-form-input-wrap w-full mb-[22px]">
                      <div className="igo-form-input">
                        <div className="flex gap-8 items-center">
                          <input
                              type="text"
                              name="title"
                              required
                              value={title}
                              onChange={handleTitleChange}
                              placeholder="루틴 이름을 입력하세요."
                              className="border-[1px] bg-[#fff] border-[#dfdfdf] px-[10px] py-[6px] font-[500] text-[18px] rounded-[4px] w-full shadow-sm focus:ring-indigo-500 focus:border-indigo-500"
                          />
                        </div>
                      </div>
                    </div>
                    <h2 className="user__title text-[20px] font-bold mb-[12px]">
                      할일 목록
                    </h2>

                    <div id="routine-contents" className="flex flex-col-reverse">
                      {routineItems.map((routine) => (
                          <div
                              key={routine.id}
                              className="igo-form-routine-info-wrap mb-[15px] border-b border-gray-300 pb-6"
                          >
                            <div className="flex w-full justify-between items-center gap-[20px]">
                              <div className="igo-form-input-wrap w-full">
                                <div className="igo-form-input">
                                  <h3 className="text-[17px] tracking-[-0.8px] font-medium mb-1">
                                    할일
                                  </h3>
                                  <div className="flex gap-8 items-center">
                                    <input
                                        type="text"
                                        name="name"
                                        required
                                        value={routine.name}
                                        onChange={(e) => handleChange(routine.id, e)}
                                        placeholder="할일을 입력하세요"
                                        className="border-[1px] border-[#dfdfdf] bg-[#fff] px-[10px] py-[6px] rounded-[4px] w-full shadow-sm focus:ring-indigo-500 focus:border-indigo-500"
                                    />
                                  </div>
                                </div>
                              </div>

                              <div className="igo-form-input-wrap max-w-[90px] relative">
                                <div className="igo-form-input">
                                  <h3 className="text-[17px] tracking-[-0.8px] font-medium mb-1">
                                    수행시간 (분)
                                  </h3>
                                  <div className="flex gap-8 items-center">
                                    <input
                                        type="number"
                                        name="time"
                                        required
                                        value={routine.time}
                                        onChange={(e) => handleChange(routine.id, e)}
                                        placeholder="10"
                                        className={`border-[1px] bg-[#fff] px-[10px] py-[6px] rounded-[4px] w-full shadow-sm focus:ring-indigo-500 focus:border-indigo-500 ${routine.timeError ? 'border-red-500' : 'border-[#dfdfdf]'}`}
                                    />
                                  </div>
                                  {routine.timeError && (
                                      <div
                                          className="absolute bg-white border border-red-500 text-red-700 px-3 py-3 rounded-md shadow-lg text-xs z-10 whitespace-nowrap"
                                          style={{ top: 'calc(100% + 5px)', left: '0' }}
                                      >
                                        {routine.timeError}
                                        <div className="absolute w-2 h-2 bg-white border-l border-t border-red-500 transform rotate-45" style={{ top: '-4px', left: '15px' }}></div>
                                      </div>
                                  )}
                                </div>
                              </div>

                              <div className="text-right">
                                <button
                                    type="button"
                                    onClick={() =>
                                        handleRemoveRoutineItem(routine.id)
                                    }
                                    disabled={routine.disabled}
                                    className="bg-red-500 hover:bg-red-700 text-white font-semibold py-2 px-4 rounded text-sm shadow-md whitespace-nowrap disabled:!bg-[#dfdfdf]"
                                >
                                  삭제
                                </button>
                              </div>
                            </div>
                          </div>
                      ))}
                    </div>
                    <button
                        id="add-routine-btn"
                        type="button"
                        onClick={handleAddRoutineItem}
                        style={{
                          padding: "7px 20px",
                          margin: "10px auto 40px auto",
                          background: "#fff",
                          borderRadius: "4px",
                          fontSize: "16px",
                          fontWeight: "500",
                          color: "#383838",
                          display: "block",
                          border: "1px solid #dfdfdf",
                        }}
                        className="hover:opacity-70 transition-colors duration-150 cursor-pointer"
                    >
                      할일 추가
                    </button>
                    <div className="absolute bottom-[0px] left-[0px] flex justify-center items-center w-full bg-[#fff] p-[12px] gap-[12px]">
                      <button
                          className="w-full hover:opacity-[0.7] cursor-pointer py-[10px] px-[5px] bg-red-500 rounded-[7px] text-[#fff] text-[15px] tracking-[-0.6px] font-[500]"
                          type="button"
                          onClick={handleDelete}
                      >
                        루틴 삭제
                      </button>
                      <button
                          className={`w-full hover:opacity-[0.7] cursor-pointer py-[10px] px-[5px] bg-[#01274F] border-[1px] border-[#01274F] rounded-[7px] text-[#fff] text-[15px] tracking-[-0.6px] font-[500] ${isLoading ? 'opacity-70' : ''}`}
                          type="submit"
                          disabled={isLoading}
                      >
                        {isLoading ? "저장 중..." : "루틴 저장"}
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </form>
          </div>
        </div>

        <ConfirmPopup
            isOpen={isDeletePopupOpen}
            message={`루틴 '${title}'을 삭제하시겠습니까?`}
            onConfirm={confirmDelete}
            onCancel={() => setIsDeletePopupOpen(false)}
        />
      </div>
  );
}