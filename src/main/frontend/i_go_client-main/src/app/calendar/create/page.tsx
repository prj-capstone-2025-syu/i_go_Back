"use client";
import NavBar from "@/components/common/topNav";
import React, { useState } from "react";
export default function Home() {
  async function createSchedule() {
    "use client";
  }
  const [selectedRoutine, setSelectedRoutine] = useState("r-0");
  const handleRoutineChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setSelectedRoutine(e.target.value);
  };
  return (
      <div className="flex flex-col w-full h-full">
        <NavBar title="일정 등록" link="/mypage"></NavBar>
        <div className="w-full max-h-full overflow-y-auto">
          <div className="flex flex-col items-center justify-start p-[20px] w-full h-auto">
            <div className="w-full shadow-[0px_0px_10px_rgba(0,0,0,0.2)] bg-[#fff] p-[20px]">
              <form
                  className="search-htmlForm"
                  id="createSchedulehtmlForm"
                  action={createSchedule}
              >
                <input type="hidden" name="perpage" defaultValue="10" />
                <input type="hidden" name="orderby" defaultValue="" />
                <div className="flex flex-col items-center justify-center gap-y-[8px] w-full">
                  <div className="grid grid-cols-1 2xl:grid-cols-3 flex-col 2xl:flex-row flex-wrap items-center gap-[20px] w-full">
                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        일정 제목
                      </p>
                      <div className="flex justify-between items-center w-full gap-x-[8px]">
                        <input
                            type="text"
                            name="name"
                            required
                            defaultValue=""
                            placeholder="일정명을 입력해주세요."
                            className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] disabled:!bg-[#ECEDEF] readonly:!bg-[#ECEDEF] !outline-none hover:border-[#383838]"
                        />
                      </div>
                    </div>
                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        시작일시
                      </p>
                      <div className="flex justify-between items-center w-full gap-x-[8px]">
                        <input
                            type="date"
                            name="start_at"
                            required
                            min="2025-05-16"
                            defaultValue=""
                            placeholder="0000-00-00"
                            className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] disabled:!bg-[#ECEDEF] readonly:!bg-[#ECEDEF] !outline-none hover:border-[#383838]"
                        />
                        <input
                            type="time"
                            name="end_at"
                            required
                            min="2025-05-16"
                            defaultValue=""
                            placeholder="0000-00-00"
                            className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] disabled:!bg-[#ECEDEF] readonly:!bg-[#ECEDEF] !outline-none hover:border-[#383838]"
                        />
                      </div>
                    </div>
                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        종료일시
                      </p>
                      <div className="flex justify-between items-center w-full gap-x-[8px]">
                        <input
                            type="date"
                            name="start_at"
                            required
                            min="2025-05-16"
                            defaultValue=""
                            placeholder="0000-00-00"
                            className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] disabled:!bg-[#ECEDEF] readonly:!bg-[#ECEDEF] !outline-none hover:border-[#383838]"
                        />
                        <input
                            type="time"
                            name="end_at"
                            required
                            min="2025-05-16"
                            defaultValue=""
                            placeholder="0000-00-00"
                            className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] disabled:!bg-[#ECEDEF] readonly:!bg-[#ECEDEF] !outline-none hover:border-[#383838]"
                        />
                      </div>
                    </div>

                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        장소
                      </p>
                      <div className="flex justify-between items-center w-full gap-x-[8px]">
                        <input
                            type="text"
                            name="address"
                            required
                            defaultValue=""
                            placeholder="일정 장소를 입력해주세요."
                            className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] disabled:!bg-[#ECEDEF] readonly:!bg-[#ECEDEF] !outline-none hover:border-[#383838]"
                        />
                      </div>
                    </div>
                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        루틴 선택
                      </p>
                      <div className="group relative flex w-full justify-between items-center overflow-hidden w-full">
                        <select
                            required
                            value={selectedRoutine}
                            onChange={handleRoutineChange}
                            name="address_type"
                            className="appearance-none bg-transparent w-full h-full outline-none border-[1px] border-[#DFDFDF] focus:border-[#383838] pl-[15px] pr-[42px] py-[8px] text-[13px] leading-[20px] tracking-[-0.4px] text-[#383838]"
                        >
                          <option value="r-0" disabled>
                            루틴을 선택하세요.
                          </option>
                          <option value="r-1">집-학교 루틴</option>
                          <option value="r-1">학교-약속 루틴</option>
                          <option value="r-1">집-약속 루틴</option>
                        </select>
                        <div className="group-hover:rotate-180 duration-300 absolute right-[15px]">
                          <svg
                              xmlns="http://www.w3.org/2000/svg"
                              width="12"
                              height="12"
                              fill="none"
                          >
                            <g clipPath="url(#a)">
                              <path
                                  fill="#383838"
                                  d="M5.572 9.12a.612.612 0 0 0 .857 0l5.394-5.381a.604.604 0 1 0-.856-.855L6 7.837 1.034 2.883a.604.604 0 1 0-.857.855l5.395 5.381Z"
                              ></path>
                            </g>
                            <defs>
                              <clipPath id="a">
                                <path fill="#fff" d="M12 12H0V0h12z"></path>
                              </clipPath>
                            </defs>
                          </svg>
                        </div>
                      </div>
                    </div>
                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        준비물
                      </p>
                      <div className="flex justify-between items-center w-full gap-x-[8px]">
                        <input
                            type="text"
                            name="address"
                            defaultValue=""
                            placeholder="준비물을 입력해주세요."
                            className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] disabled:!bg-[#ECEDEF] readonly:!bg-[#ECEDEF] !outline-none hover:border-[#383838]"
                        />
                      </div>
                    </div>

                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        메모
                      </p>
                      <div className="flex justify-between items-center w-full gap-x-[8px]">
                        <input
                            type="text"
                            name="address"
                            defaultValue=""
                            placeholder="메모를 입력해주세요."
                            className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] disabled:!bg-[#ECEDEF] readonly:!bg-[#ECEDEF] !outline-none hover:border-[#383838]"
                        />
                      </div>
                    </div>
                  </div>

                  <div className="flex flex-col items-start w-full gap-y-[20px] lg:gap-y-0 my-[15px]">
                    <div className="flex flex-row items-center gap-x-[8px]">
                      <label className="relative block min-w-[16px] min-h-[16px] cursor-pointer !mb-0">
                        <input
                            type="checkbox"
                            name="recommend"
                            id="recommend"
                            defaultValue="1"
                            className="peer a11y"
                        />
                        <span className="absolute top-[50%] translate-y-[-50%] left-0 bg-[#fff] peer-checked:bg-[#2155A0] border-[1px] border-[#949494] peer-checked:border-[#2155A0] w-[16px] h-[16px] peer-checked:after:content-[''] peer-checked:after:absolute peer-checked:after:left-[50%] peer-checked:after:top-[50%] peer-checked:after:w-[6px] peer-checked:after:h-[9px] peer-checked:after:mt-[-6px] peer-checked:after:ml-[-3px] peer-checked:after:border-r-[2px] peer-checked:after:border-r-[#fff] peer-checked:after:border-b-[2px] peer-checked:after:border-b-[#fff] peer-checked:after:rotate-[40deg]"></span>
                      </label>
                      <label
                          htmlFor="recommend"
                          className="text-[13px] leading-[16px] tracking-[-0.4px] text-[#777]"
                      >
                        비대면 일정
                      </label>
                    </div>
                    <div className="flex flex-row items-center justify-center gap-x-[12px] w-full pt-[20px]">
                      <button
                          type="button"
                          className="hidden search-reset flex justify-center bg-[#777777] hover:bg-[#777777]/90 min-w-[115px] py-[10px] px-[20px] rounded-[4px]"
                      >
                      <span className="font-[500] text-[15px] leading-[19px] tracking-[-0.4px] text-[#fff]">
                        일정삭제
                      </span>
                      </button>
                      <button
                          type="submit"
                          className="flex justify-center bg-[#01274F] hover:bg-[#01274F]/90 min-w-[115px] py-[10px] px-[20px] rounded-[4px]"
                      >
                      <span className="font-[500] text-[15px] leading-[19px] tracking-[-0.4px] text-[#fff]">
                        일정 등록하기
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
