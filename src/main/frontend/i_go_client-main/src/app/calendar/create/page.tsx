"use client"; // 클라이언트 컴포넌트로 변경

import NavBar from "@/components/common/topNav";
import { useState } from "react";
import { useRouter } from "next/navigation";
import axios from "axios";

export default function Home() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isRemote, setIsRemote] = useState(false);

  // 폼 제출 핸들러
  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    const formData = new FormData(e.currentTarget);

    // 폼 데이터 가져오기
    const title = formData.get('name') as string;
    const location = formData.get('address') as string;
    const memo = formData.get('address') as string; // 메모 필드의 name 속성이 실제로는 다를 수 있음

    // 시작 일시
    const startDate = formData.get('start_at') as string;
    const startTime = formData.get('end_at') as string; // 실제로는 start_time 등의 이름일 수 있음
    const startTimeString = `${startDate}T${startTime}:00`;

    // 일정 생성 요청 데이터
    try {
      const response = await axios.post('/api/schedules', {
        title: title,
        startTime: startTimeString,
        location: isRemote ? "비대면" : location,
        memo: memo,
        category: "PERSONAL", // 카테고리는 폼에 없으므로 기본값으로 설정
        routineId: null // 루틴 ID는 백엔드에서 필수값이지만 현재 폼에 없음
      }, {
        withCredentials: true // 쿠키를 포함하여 요청하기 위해 필요
      });

      alert("일정이 성공적으로 등록되었습니다.");
      router.push('/calendar'); // 캘린더 페이지로 이동
    } catch (err: any) {
      console.error("일정 생성 실패:", err);
      setError(err.response?.data?.message || "일정 등록 중 오류가 발생했습니다.");
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
              {error && (
                  <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 mb-4 rounded">
                    {error}
                  </div>
              )}
              <form
                  className="search-htmlForm"
                  id="createSchedulehtmlForm"
                  onSubmit={handleSubmit}
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
                            min={new Date().toISOString().split('T')[0]}
                            defaultValue={new Date().toISOString().split('T')[0]}
                            placeholder="0000-00-00"
                            className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] disabled:!bg-[#ECEDEF] readonly:!bg-[#ECEDEF] !outline-none hover:border-[#383838]"
                        />
                        <input
                            type="time"
                            name="end_at"
                            required
                            defaultValue="09:00"
                            placeholder="00:00"
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
                            name="end_date"
                            required
                            min={new Date().toISOString().split('T')[0]}
                            defaultValue={new Date().toISOString().split('T')[0]}
                            placeholder="0000-00-00"
                            className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] disabled:!bg-[#ECEDEF] readonly:!bg-[#ECEDEF] !outline-none hover:border-[#383838]"
                        />
                        <input
                            type="time"
                            name="end_time"
                            required
                            defaultValue="10:00"
                            placeholder="00:00"
                            className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] disabled:!bg-[#ECEDEF] readonly:!bg-[#ECEDEF] !outline-none hover:border-[#383838]"
                        />
                      </div>
                    </div>

                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        일정 위치
                      </p>
                      <div className="flex justify-between items-center w-full gap-x-[8px]">
                        <input
                            type="text"
                            name="address"
                            defaultValue=""
                            placeholder="일정 위치를 입력해주세요."
                            className="text-[13px] text-[#383838] font-[400] tracking-[-0.4px] w-full border-[1px] border-[#DFDFDF] py-[8px] px-[15px] disabled:!bg-[#ECEDEF] readonly:!bg-[#ECEDEF] !outline-none hover:border-[#383838]"
                            disabled={isRemote}
                        />
                      </div>
                    </div>

                    <div className="relative">
                      <p className="text-[#383838] text-[13px] font-[500] tracking-[-0.4px] mb-[7px]">
                        준비물
                      </p>
                      <div className="flex justify-between items-center w-full gap-x-[8px]">
                        <input
                            type="text"
                            name="supplies"
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
                            name="memo"
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
                            checked={isRemote}
                            onChange={(e) => setIsRemote(e.target.checked)}
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
                    <div className="flex flex-row items-center justify-center gap-x-[12px] w-full">
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
                          disabled={loading}
                          className="flex justify-center bg-[#01274F] hover:bg-[#01274F]/90 min-w-[115px] py-[10px] px-[20px] rounded-[4px]"
                      >
                      <span className="font-[500] text-[15px] leading-[19px] tracking-[-0.4px] text-[#fff]">
                        {loading ? "처리중..." : "일정 등록하기"}
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