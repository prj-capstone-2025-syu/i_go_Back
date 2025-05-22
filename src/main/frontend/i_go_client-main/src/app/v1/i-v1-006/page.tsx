import NavBarMain from "@/components/common/topNavMain";
import Link from "next/link";

export default function Home() {
  return (
    <div className="flex flex-col w-full h-full">
      <NavBarMain link="setting"></NavBarMain>
      <div className="w-full max-h-full overflow-y-auto">
        <div className="flex flex-col items-center justify-start p-[20px] w-full h-auto gap-y-[15px]">
          <Link
            className="hover:opacity-[0.7] border-[1px] p-[20px] border-[#dfdfdf] rounded-[6px] bg-[#fff] w-full shadow-sm flex items-center jutify-start gap-x-[12px]"
            href="#"
          >
            <div className="w-[80px] aspect-square rounded-full bg-[#dfdfdf]"></div>
            <div className="w-full flex flex-col gap-y-[1px]">
              <span className="text-[18px] font-[500] text-[#01274F] leading-[130%] line-clamp-1">
                김삼육
              </span>
              <span className="text-[15px] font-[500] text-[#01274F] leading-[150%] line-clamp-1 tracking-[-0.8px]">
                나의 한마디 : 아자아자 화이팅!!
              </span>
              <span className="text-[15px] font-[500] text-[#01274F] leading-[130%] line-clamp-1 tracking-[-0.8px]">
                example@example.com
              </span>
            </div>
            <img className="w-[24px]" src="/icon/edit.svg"></img>
          </Link>
          <Link
            className="hover:opacity-[0.7] border-[1px] p-[20px] border-[#dfdfdf] rounded-[6px] bg-[#fff] w-full shadow-sm flex justify-between items-center"
            href="#"
          >
            <p className="text-[18px] font-[500] text-[#01274F] leading-[130%] line-clamp-1">
              나의 루틴 설정하기
            </p>
            <img className="w-[24px]" src="/icon/setting.svg"></img>
          </Link>
          {/* 알람 목록 */}
          <div className="flex justify-between items-end w-full mb-[0px] px-[5px]">
            <p className="text-[#01274F] text-[19px] font-[700] tracking-[-0.4px]">
              최근 알람
            </p>
          </div>
          <div className="w-full bg-[#fff] p-[15px] rounded-[6px] shadow-[0px_0px_5px_rgba(0,0,0,0.2)] mb-[22px]">
            <Link
              href="#"
              className="flex items-start  flex-col  justify-between gap-x-[10px] gap-y-[4px] w-full bg-[#fff] border-b-[1px] border-[#dfdfdf] py-[7px] px-[10px] lg:px-[20px] hover:bg-[#dfdfdf] last:!border-[0px] first:!pt-[0px] last:!pb-[0px]"
            >
              <p className="w-full text-[#383838] text-[13px] line-clamp-3 pr-[15px]">
                제목은 3줄까지만 표시됩니다.
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
              <p className="w-full text-[#383838] text-[13px] line-clamp-3 pr-[15px]">
                제목은 3줄까지만 표시됩니다. 제목은 3줄까지만 표시됩니다.제목은
                3줄까지만 표시됩니다.제목은 3줄까지만 표시됩니다.제목은
                3줄까지만 표시됩니다.
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
              <p className="w-full text-[#383838] text-[13px] line-clamp-3 pr-[15px]">
                제목은 3줄까지만 표시됩니다.
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
              <p className="w-full text-[#383838] text-[13px] line-clamp-3 pr-[15px]">
                제목은 3줄까지만 표시됩니다.
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
              <p className="w-full text-[#383838] text-[13px] line-clamp-3 pr-[15px]">
                제목은 3줄까지만 표시됩니다.
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
              <p className="w-full text-[#383838] text-[13px] line-clamp-3 pr-[15px]">
                제목은 3줄까지만 표시됩니다.
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
              <p className="w-full text-[#383838] text-[13px] line-clamp-3 pr-[15px]">
                제목은 3줄까지만 표시됩니다.
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
