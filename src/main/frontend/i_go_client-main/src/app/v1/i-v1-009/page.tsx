import NavBarMain from "@/components/common/topNavMain";
import Link from "next/link";

export default function Home() {
  return (
    <div className="flex flex-col w-full h-full">
      <NavBarMain link="setting"></NavBarMain>
      <div className="absolute z-[99] w-full h-[100dvh] bg-[rgba(217,217,217,0.85)]"></div>
      <div
        className="absolute left-[50%] top-[55%] translate-x-[-50%] translate-y-[-50%] w-[85%] max-w-[335px] bg-[#fff] p-[20px] pb-[10px] z-[100] rounded-[8px] shadow-md
        "
      >
        <p className="text-[18px] text-[#5C5C5C] font-[400] text-center">
          <span className="font-[700]">회원탈퇴</span> 하시겠습니까?
        </p>
        <div className="mt-[10px] w-full h-[1px] bg-[#dfdfdf]"></div>
        <div className="w-full grid grid-cols-2">
          <Link
            href="/"
            className="text-[#ff2f01] text-[16px] font-[500] tracking-[-0.8px] leading-[155%] line-clamp-1 text-center py-[10px] hover:opacity-[0.7]"
          >
            예
          </Link>
          <Link
            href="/"
            className="text-[#383838] text-[16px] font-[500] tracking-[-0.8px] leading-[155%] line-clamp-1 text-center py-[10px] hover:opacity-[0.7]"
          >
            아니요
          </Link>
        </div>
      </div>
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
          </Link>
          <Link
            className="hover:opacity-[0.7] border-[1px] p-[20px] border-[#dfdfdf] rounded-[6px] bg-[#fff] w-full shadow-sm flex justify-between items-center"
            href="/setting"
          >
            <p className="text-[18px] font-[500] text-[#01274F] leading-[130%] line-clamp-1">
              IGO 설정
            </p>
            <img className="w-[24px]" src="/icon/setting.svg" />
          </Link>
          <div className="w-full flex gap-x-[15px]">
            <Link
              className="hover:opacity-[0.7] border-[1px] p-[20px] border-[#dfdfdf] rounded-[6px] bg-[#fff] w-full shadow-sm flex justify-between items-center justify-center"
              href="#"
            >
              <p className="text-[17px] font-[500] text-[#01274F] leading-[130%] line-clamp-1">
                로그아웃
              </p>
            </Link>
            <Link
              className="hover:opacity-[0.7] border-[1px] p-[20px] border-[#dfdfdf] rounded-[6px] bg-[#fff] w-full shadow-sm flex justify-between items-center justify-center"
              href="#"
            >
              <p className="text-[17px] font-[500] text-[#ff2f01] leading-[130%] line-clamp-1">
                회원탈퇴
              </p>
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
