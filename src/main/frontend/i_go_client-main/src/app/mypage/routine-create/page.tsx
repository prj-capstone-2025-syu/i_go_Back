"use client";
import NavBar from "@/components/common/topNav";
import React, { useState } from "react";
import { createRoutine } from "@/api/routineApi"; // API 모듈 import
import { useRouter } from 'next/navigation'; // Next.js 라우터

// 루틴 아이템 인터페이스
interface RoutineItem {
  id: number;
  name: string;
  time: string;
  disabled: boolean;
}

export default function RoutineFormPage() {
  const router = useRouter();
  const initialRoutineItem: RoutineItem = {
    id: Date.now(),
    name: "여유시간(기본값)",
    time: "10",
    disabled: true,
  };

  const [routineItems, setRoutineItems] = useState<RoutineItem[]>([
    initialRoutineItem,
  ]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleAddRoutineItem = () => {
    setRoutineItems((prevItems) => [
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
    setRoutineItems((prevItems) =>
        prevItems.filter((item) => item.id !== idToRemove)
    );
  };

  const handleChange = (
      id: number,
      event: React.ChangeEvent<HTMLInputElement>
  ) => {
    const { name, value } = event.target;
    setRoutineItems((prevItems) =>
        prevItems.map((item) =>
            item.id === id ? { ...item, [name]: value } : item
        )
    );
  };

  // 폼 제출 핸들러 - API 호출 구현
  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsSubmitting(true);
    setError(null);

    try {
      const titleInput = document.querySelector('input[name="title"]') as HTMLInputElement;
      const routineTitle = titleInput.value;

      if (!routineTitle) {
        throw new Error("루틴 이름을 입력해주세요.");
      }

      // API 요청 데이터 구성
      const routineData = {
        title: routineTitle,
        items: routineItems.map(item => ({
          name: item.name,
          time: item.time
        }))
      };

      // API 호출
      await createRoutine(routineData);
      alert("루틴이 성공적으로 저장되었습니다.");

      // 성공 후 루틴 목록 페이지로 이동
      router.push('/routines');
    } catch (error) {
      console.error("루틴 저장 실패:", error);
      setError("루틴 저장에 실패했습니다. 다시 시도해주세요.");
      alert("루틴 저장에 실패했습니다. 다시 시도해주세요.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
      <div className="flex flex-col w-full h-full">
        <NavBar title="루틴 생성" link="/mypage"></NavBar>
        <div className="w-full max-h-full overflow-y-auto">
          <div className="flex flex-col items-center justify-start p-[15px] w-full h-auto">
            {error && (
                <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4 w-full">
                  {error}
                </div>
            )}
            <form onSubmit={handleSubmit} className="w-full">
              <div className="w-full mx-auto py-[20px]">
                <div className="igo-form-txt-wrap w-full">
                  <div className="igo-form-txt-wrap w-full">
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
                      {routineItems.map((item) => (
                          <div
                              key={item.id}
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
                                        value={item.name}
                                        onChange={(e) => handleChange(item.id, e)}
                                        className="border-[1px] border-[#dfdfdf] bg-[#fff] px-[10px] py-[6px] rounded-[4px] w-full shadow-sm focus:ring-indigo-500 focus:border-indigo-500"
                                    />
                                  </div>
                                </div>
                              </div>

                              <div className="igo-form-input-wrap max-w-[90px]">
                                <div className="igo-form-input">
                                  <h3 className="text-[17px] tracking-[-0.8px] font-medium mb-1">
                                    수행시간 (분)
                                  </h3>
                                  <div className="flex gap-8 items-center">
                                    <input
                                        type="number"
                                        name="time"
                                        required
                                        value={item.time}
                                        onChange={(e) => handleChange(item.id, e)}
                                        className="border-[1px] border-[#dfdfdf] bg-[#fff] px-[10px] py-[6px] rounded-[4px] w-full shadow-sm focus:ring-indigo-500 focus:border-indigo-500"
                                    />
                                  </div>
                                </div>
                              </div>
                              {routineItems.length > 1 && (
                                  <div className="text-right">
                                    <button
                                        type="button"
                                        onClick={() =>
                                            handleRemoveRoutineItem(item.id)
                                        }
                                        disabled={item.disabled}
                                        className="bg-red-500 hover:bg-red-700 text-white font-semibold py-2 px-4 rounded text-sm shadow-md whitespace-nowrap disabled:!bg-[#dfdfdf] "
                                    >
                                      삭제
                                    </button>
                                  </div>
                              )}
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
                    <div className="absolute bottom-[0px] left-[0px] grid grid-cols-1 w-full bg-[#fff] p-[12px] gap-[12px]">
                      <button
                          className="hover:opacity-[0.7] cursor-pointer py-[10px] px-[5px] bg-[#01274F] border-[1px] border-[#01274F] rounded-[7px] text-[#fff] text-[15px] tracking-[-0.6px] font-[500]"
                          type="submit"
                          disabled={isSubmitting}
                      >
                        {isSubmitting ? "저장 중..." : "루틴 저장"}
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </form>
          </div>
        </div>
      </div>
  );
}