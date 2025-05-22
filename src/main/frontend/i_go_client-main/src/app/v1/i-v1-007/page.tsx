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
