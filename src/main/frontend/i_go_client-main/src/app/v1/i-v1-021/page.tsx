"use client";
import NavBar from "@/components/common/topNav";
// This directive is essential for using hooks like useState and event handlers

import React, { useState } from "react";

// Define an interface for the structure of a routine routine
interface routineroutine {
  id: number; // Using number for simplicity, could be string for UUIDs
  name: string;
  time: string;
  disabled: boolean;
}

export default function routineFormPage() {
  const initialroutineroutine: routineroutine = {
    id: Date.now(),
    name: "여유시간(기본값)",
    time: "10",
    disabled: true,
  };

  const [routineroutines, setroutineroutines] = useState<routineroutine[]>([
    initialroutineroutine,
  ]);

  const handleAddroutineroutine = () => {
    setroutineroutines((prevroutines) => [
      // Use functional update
      ...prevroutines,
      {
        id: Date.now() + prevroutines.length,
        name: "",
        time: "",
        disabled: false,
      },
    ]);
  };

  const handleRemoveroutineroutine = (idToRemove: number) => {
    setroutineroutines(
      (
        prevroutines // Use functional update
      ) => prevroutines.filter((routine) => routine.id !== idToRemove)
    );
  };

  const handleChange = (
    id: number,
    event: React.ChangeEvent<HTMLInputElement>
  ) => {
    const { name, value } = event.target;
    setroutineroutines(
      (
        prevroutines // Use functional update
      ) =>
        prevroutines.map((routine) =>
          routine.id === id ? { ...routine, [name]: value } : routine
        )
    );
  };

  // If you need to submit this data, you'd add a handler here
  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    // Process or send routineroutines data
    console.log("routine routines Data:", routineroutines);
    alert("루틴이 저장 되었습니다.");
    // Example:
    // try {
    //   const response = await fetch('/', {
    //     method: 'POST',
    //     headers: { 'Content-Type': 'application/json' },
    //     body: JSON.stringify(routineroutines),
    //   });
    //   if (response.ok) {
    //     alert('routine data submitted successfully!');
    //   } else {
    //     alert('Failed to submit routine data.');
    //   }
    // } catch (error) {
    //   console.error('Submission error:', error);
    //   alert('An error occurred during submission.');
    // }
  };

  return (
    <div className="flex flex-col w-full h-full">
      <NavBar title="루틴 상세" link="/mypage"></NavBar>
      <div className="w-full max-h-full overflow-y-auto">
        <div className="flex flex-col items-center justify-start p-[15px] w-full h-auto">
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
                          value="등교 전 루틴"
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
                    {routineroutines.map((routine) => (
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
                                  value={routine.time}
                                  onChange={(e) => handleChange(routine.id, e)}
                                  className="border-[1px] border-[#dfdfdf] bg-[#fff] px-[10px] py-[6px] rounded-[4px] w-full shadow-sm focus:ring-indigo-500 focus:border-indigo-500"
                                />
                              </div>
                            </div>
                          </div>

                          <div className="text-right">
                            <button
                              type="button"
                              onClick={() =>
                                handleRemoveroutineroutine(routine.id)
                              }
                              disabled={routine.disabled}
                              className="bg-red-500 hover:bg-red-700 text-white font-semibold py-2 px-4 rounded text-sm shadow-md whitespace-nowrap disabled:!bg-[#dfdfdf] "
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
                    onClick={handleAddroutineroutine}
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
                    >
                      루틴 삭제
                    </button>
                    <button
                      className="w-full hover:opacity-[0.7] cursor-pointer py-[10px] px-[5px] bg-[#01274F] border-[1px] border-[#01274F] rounded-[7px] text-[#fff] text-[15px] tracking-[-0.6px] font-[500]"
                      type="submit"
                    >
                      루틴 저장
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
