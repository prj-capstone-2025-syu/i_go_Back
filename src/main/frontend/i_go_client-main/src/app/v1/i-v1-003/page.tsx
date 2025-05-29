"use client";

import NavBarMain from "@/components/common/topNavMain";
import Link from "next/link";
import { useState, useEffect, useRef } from "react";
import { useSearchParams } from "next/navigation";

export default function Home() {
  const [inputValue, setInputValue] = useState("");
  const [messages, setMessages] = useState<{role: 'user'|'ai', text: string}[]>([]);
  const [loading, setLoading] = useState(false);
  const searchParams = useSearchParams();
  const inputRef = useRef<HTMLInputElement>(null);

  // 쿼리스트링 message 자동 입력 및 전송
  useEffect(() => {
    const msg = searchParams.get("message");
    if (msg) {
      setInputValue(msg);
      handleSend(msg);
    }
    // eslint-disable-next-line
  }, []);

  const handleSend = async (msg?: string) => {
    const message = typeof msg === "string" ? msg : inputValue;
    if (!message.trim()) return;
    setMessages((prev) => [...prev, { role: "user", text: message }]);
    setInputValue("");
    setLoading(true);
    try {
      // 실제 API 엔드포인트에 맞게 수정 필요
      const res = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message }),
      });
      const data = await res.json();
      setMessages((prev) => [...prev, { role: "ai", text: data.message }]);
    } catch (e) {
      setMessages((prev) => [...prev, { role: "ai", text: "오류가 발생했습니다." }]);
    }
    setLoading(false);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    handleSend();
  };

  return (
    <div className="flex flex-col w-full h-full">
      <NavBarMain link="/mypage"></NavBarMain>
      <div className="w-full max-h-full overflow-y-auto">
        <div className="flex flex-col items-center justify-start p-[20px] w-full h-auto">
          {/* 채팅 메시지 영역 */}
          <div className="w-full 2xl:max-w-[781px] mb-[22px] flex flex-col gap-2">
            {messages.map((msg, idx) => (
              <div key={idx} className={msg.role === 'user' ? 'text-right' : 'text-left'}>
                <span className={msg.role === 'user' ? 'bg-blue-100 text-blue-900 rounded px-2 py-1 inline-block' : 'bg-gray-100 text-gray-900 rounded px-2 py-1 inline-block'}>
                  {msg.text}
                </span>
              </div>
            ))}
            {loading && <div className="text-left text-gray-400">AI가 답변 중...</div>}
          </div>
          {/* 프롬프트 입력창 */}
          <form onSubmit={handleSubmit} className="relative w-full 2xl:max-w-[781px] mb-[22px]">
            <input
              ref={inputRef}
              type="text"
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              className="bg-[#fff] outline-none border-[1px] border-[#DFDFDF] shadow-[0px_0px_5px_rgba(0,0,0,0.2)] rounded-[6px] pr-[38px] pl-[15px] py-[12px] w-full font-[400] text-[15px] leading-[20px] text-[#383838] placeholder:!text-[#949494] focus:border-[#01274F]"
              placeholder="아이고 AI - 무엇을 도와드릴까요?"
              disabled={loading}
            />
            <div className="absolute flex right-[11px] !top-[50%] !translate-y-[-50%]">
              <button className="p-[4px]" type="submit" disabled={loading}>
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
          {/* 진행중인 일정 */}
          <div className="flex justify-between items-end w-full mb-[8px] px-[5px]">
            <p className="text-[#01274F] text-[19px] font-[700] tracking-[-0.4px]">
              진행중인 일정
            </p>
            <p>
              <span className="schedule-status text-[#0080FF] text-[16px] font-[600] tracking-[-0.4px]">
                한가한 하루네요!
              </span>
            </p>
          </div>
          <div className="w-full bg-[#fff] p-[15px] rounded-[6px] shadow-[0px_0px_5px_rgba(0,0,0,0.2)] mb-[22px]">
            <div className="flex justify-between items-center w-full ">
              <p className="text-[#383838] text-[17px] font-[600] tracking-[-0.4px] leading-[155%] line-clamp-1">
                진행 중인 일정이 없습니다.
              </p>
              <div className="flex items-center gap-x-[1px]">
                <img src="/icon/clock.svg"></img>

                <p className="text-[#0080FF] text-[16px] font-[500] tracking-[-0.4px] leading-[160%]"></p>
              </div>
            </div>
          </div>

          {/* 다가오는 일정 */}
          <div className="flex justify-between items-end w-full mb-[8px] px-[5px]">
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
          <div className="w-full bg-[#fff] p-[15px] rounded-[6px] shadow-[0px_0px_5px_rgba(0,0,0,0.2)] mb-[22px]">
            <Link
              href="#"
              className="flex items-start  flex-col  justify-between gap-x-[10px] gap-y-[4px] w-full bg-[#fff] border-b-[1px] border-[#dfdfdf] py-[7px] px-[10px] lg:px-[20px] hover:bg-[#dfdfdf] last:!border-[0px] first:!pt-[0px] last:!pb-[0px]"
            >
              <p className="w-full text-[#383838] text-[13px] line-clamp-1 pr-[15px]">
                제목은 첫줄까지만 표시됩니다. 제목은 첫줄까지만
                표시됩니다.제목은 첫줄까지만 표시됩니다.제목은 첫줄까지만
                표시됩니다.제목은 첫줄까지만 표시됩니다.
              </p>
              <div className="flex flex-row gap-x-[3px] md:gap-x-[15px] xl:gap-x-[65px]">
                <p className="w-full text-[#777] text-[13px] pr-[5px] min-w-[68px] text-left md:text-center whitespace-nowrap">
                  YYYY-MM-DD TT:MM
                </p>
              </div>
            </Link>
            <Link
              href="#"
              className="flex items-start  flex-col  justify-between gap-x-[10px] gap-y-[4px] w-full bg-[#fff] border-b-[1px] border-[#dfdfdf] py-[7px] px-[10px] lg:px-[20px] hover:bg-[#dfdfdf] last:!border-[0px] first:!pt-[0px] last:!pb-[0px]"
            >
              <p className="w-full text-[#383838] text-[13px] line-clamp-1 pr-[15px]">
                제목은 첫줄까지만 표시됩니다. 제목은 첫줄까지만
                표시됩니다.제목은 첫줄까지만 표시됩니다.제목은 첫줄까지만
                표시됩니다.제목은 첫줄까지만 표시됩니다.
              </p>
              <div className="flex flex-row gap-x-[3px] md:gap-x-[15px] xl:gap-x-[65px]">
                <p className="w-full text-[#777] text-[13px] pr-[5px] min-w-[68px] text-left md:text-center whitespace-nowrap">
                  YYYY-MM-DD TT:MM
                </p>
              </div>
            </Link>
            <Link
              href="#"
              className="flex items-start  flex-col  justify-between gap-x-[10px] gap-y-[4px] w-full bg-[#fff] border-b-[1px] border-[#dfdfdf] py-[7px] px-[10px] lg:px-[20px] hover:bg-[#dfdfdf] last:!border-[0px] first:!pt-[0px] last:!pb-[0px]"
            >
              <p className="w-full text-[#383838] text-[13px] line-clamp-1 pr-[15px]">
                제목은 첫줄까지만 표시됩니다. 제목은 첫줄까지만
                표시됩니다.제목은 첫줄까지만 표시됩니다.제목은 첫줄까지만
                표시됩니다.제목은 첫줄까지만 표시됩니다.
              </p>
              <div className="flex flex-row gap-x-[3px] md:gap-x-[15px] xl:gap-x-[65px]">
                <p className="w-full text-[#777] text-[13px] pr-[5px] min-w-[68px] text-left md:text-center whitespace-nowrap">
                  YYYY-MM-DD TT:MM
                </p>
              </div>
            </Link>
          </div>

          {/* 날씨 */}
          <div className="flex justify-between items-end w-full mb-[8px] px-[5px]">
            <p className="text-[#01274F] text-[19px] font-[700] tracking-[-0.4px]">
              날씨
            </p>
            <Link
              href="https://www.weather.go.kr/w/index.do"
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
                  d="M11 8.06675V11.0001M11 11.0001V13.9334M11 11.0001H13.9333M11 11.0001L8.06665 11.0001M3.66666 15.5834C3.66665 17.1022 4.89787 18.3334 6.41666 18.3334H15.5833C17.1021 18.3334 18.3333 17.1022 18.3333 15.5834V6.41675C18.3333 4.89797 17.1021 3.66675 15.5833 3.66675H6.41666C4.89788 3.66675 3.66666 4.89797 3.66666 6.41675L3.66666 15.5834Z"
                  stroke="#01274F"
                  strokeLinecap="round"
                />
              </svg>
            </Link>
          </div>
          <div className="w-full bg-[#fff] p-[15px] rounded-[6px] shadow-[0px_0px_5px_rgba(0,0,0,0.2)] mb-[22px]">
            <Link
              href="#"
              className="flex items-start  flex-col  justify-between gap-x-[10px] gap-y-[4px] w-full bg-[#fff] border-b-[1px] border-[#dfdfdf] py-[7px] px-[10px] lg:px-[20px] hover:bg-[#dfdfdf] last:!border-[0px] first:!pt-[0px] last:!pb-[0px]"
            >
              <p className="w-full text-[#383838] text-[13px] line-clamp-1 pr-[15px]">
                제목은 첫줄까지만 표시됩니다. 제목은 첫줄까지만
                표시됩니다.제목은 첫줄까지만 표시됩니다.제목은 첫줄까지만
                표시됩니다.제목은 첫줄까지만 표시됩니다.
              </p>
              <div className="flex flex-row gap-x-[3px] md:gap-x-[15px] xl:gap-x-[65px]">
                <p className="w-full text-[#777] text-[13px] pr-[5px] min-w-[68px] text-left md:text-center whitespace-nowrap">
                  YYYY-MM-DD TT:MM
                </p>
              </div>
            </Link>
            <Link
              href="#"
              className="flex items-start  flex-col  justify-between gap-x-[10px] gap-y-[4px] w-full bg-[#fff] border-b-[1px] border-[#dfdfdf] py-[7px] px-[10px] lg:px-[20px] hover:bg-[#dfdfdf] last:!border-[0px] first:!pt-[0px] last:!pb-[0px]"
            >
              <p className="w-full text-[#383838] text-[13px] line-clamp-1 pr-[15px]">
                제목은 첫줄까지만 표시됩니다. 제목은 첫줄까지만
                표시됩니다.제목은 첫줄까지만 표시됩니다.제목은 첫줄까지만
                표시됩니다.제목은 첫줄까지만 표시됩니다.
              </p>
              <div className="flex flex-row gap-x-[3px] md:gap-x-[15px] xl:gap-x-[65px]">
                <p className="w-full text-[#777] text-[13px] pr-[5px] min-w-[68px] text-left md:text-center whitespace-nowrap">
                  YYYY-MM-DD TT:MM
                </p>
              </div>
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
