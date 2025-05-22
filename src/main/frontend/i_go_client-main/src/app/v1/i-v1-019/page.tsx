"use client";
import NavBar from "@/components/common/topNav";
import Link from "next/link";

export default function Home() {
  return (
    <div className="flex flex-col w-full h-full">
      <NavBar title="나의 루틴" link="/mypage"></NavBar>
      <div className="absolute bottom-[0px] left-[0px]  grid grid-cols-1 w-full bg-[#fff] p-[12px] gap-[12px]">
        <button
          className="hover:opacity-[0.7] cursor-pointer py-[10px] px-[5px] bg-[#01274F] border-[1px] border-[#01274F] rounded-[7px] text-[#fff] text-[15px] tracking-[-0.6px] font-[500]"
          onClick={() => {
            location.href = "#";
          }}
          type="button"
        >
          새 루틴 만들기
        </button>
      </div>
      <div className="w-full max-h-full overflow-y-auto">
        <div className="flex flex-col items-center justify-start p-[20px] w-full h-auto">
          <div className="w-full grid grid-cols-3 gap-[15px]">
            <Link
              href="#"
              className="text-[#383838] text-center text-[14px] bg-[#fff] rounded-[5px] aspect-square flex items-center justify-center flex-col hover:opacity-[0.7]"
            >
              <button type="button" className="cursor-pointer">
                삭제
              </button>
              <p>루틴이름</p>
            </Link>
            <Link
              href="#"
              className="text-[#383838] text-center text-[14px] bg-[#fff] rounded-[5px] aspect-square flex items-center justify-center flex-col hover:opacity-[0.7]"
            >
              <button type="button" className="cursor-pointer">
                삭제
              </button>
              <p>루틴이름</p>
            </Link>
            <Link
              href="#"
              className="text-[#383838] text-center text-[14px] bg-[#fff] rounded-[5px] aspect-square flex items-center justify-center flex-col hover:opacity-[0.7]"
            >
              <button type="button" className="cursor-pointer">
                삭제
              </button>
              <p>루틴이름</p>
            </Link>
            <Link
              href="#"
              className="text-[#383838] text-center text-[14px] bg-[#fff] rounded-[5px] aspect-square flex items-center justify-center flex-col hover:opacity-[0.7]"
            >
              <button type="button" className="cursor-pointer">
                삭제
              </button>
              <p>루틴이름</p>
            </Link>
          </div>
          <div></div>
        </div>
      </div>
    </div>
  );
}
