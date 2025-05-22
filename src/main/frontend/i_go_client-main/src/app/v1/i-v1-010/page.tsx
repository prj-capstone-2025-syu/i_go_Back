import NavBar from "@/components/common/topNav";
import Link from "next/link";

export default function Home() {
  return (
    <div className="flex flex-col w-full h-full">
      <NavBar title="환경설정" link="/setting"></NavBar>
      <ul className="flex h-full flex-col bg-[#fff]">
        <li className="flex px-[16px] w-full h-[60px] bg-[#F5F5F5] box-border">
          <div className="flex w-full items-center justify-between">
            <div className="flex items-center gap-x-[8px]">
              <span className="line-clamp-1 text-[#999999] text-[15px] leading-[20px] tracking-[-0.2px]">
                푸시알림
              </span>
            </div>

            <div className="flex items-center justify-between">
              <span className="line-clamp-1 mr-[8px] text-[#999999] text-[15px] leading-[20px] tracking-[-0.2px]">
                전체 On/Off
              </span>
              <div className="relative flex rounded-full overflow-hidden cursor-pointer">
                <input
                  id="checkbox-all"
                  name="radio"
                  type="checkbox"
                  className="hidden peer"
                />
                <label
                  htmlFor="checkbox-all"
                  className="top-0 bottom-0 left-0 right-0 bg-[#CCCCCC] w-[50px] h-[25px]  peer-checked:bg-[#01274F] cursor-pointer"
                ></label>
                <label
                  htmlFor="checkbox-all"
                  className="absolute transition top-[2px] left-[2px] w-[21px] h-[21px] rounded-full bg-[#fff] peer-checked:translate-x-[25px]"
                ></label>
              </div>
            </div>
          </div>
        </li>

        <li className="flex px-[16px] w-full h-[60px] box-border">
          <div className="flex w-full items-center justify-between">
            <div className="flex items-center gap-x-[8px]">
              <span className="line-clamp-1 text-[#191919] text-[15px] leading-[20px] tracking-[-0.2px]">
                오늘 일정
              </span>
            </div>

            <div className="flex items-center justify-between">
              <div className="relative flex rounded-full overflow-hidden cursor-pointer">
                <input
                  id="checkbox_1"
                  name="radio"
                  type="checkbox"
                  className="hidden peer"
                />
                <label
                  htmlFor="checkbox_1"
                  className="top-0 bottom-0 left-0 right-0 bg-[#CCCCCC] w-[50px] h-[25px]  peer-checked:bg-[#01274F] cursor-pointer"
                ></label>
                <label
                  htmlFor="checkbox_1"
                  className="absolute transition top-[2px] left-[2px] w-[21px] h-[21px] rounded-full bg-[#fff] peer-checked:translate-x-[25px]"
                ></label>
              </div>
            </div>
          </div>
        </li>

        <li className="flex px-[16px] w-full h-[60px] box-border">
          <div className="flex w-full items-center justify-between">
            <div className="flex items-center gap-x-[8px]">
              <span className="line-clamp-1 text-[#191919] text-[15px] leading-[20px] tracking-[-0.2px]">
                다음 일정
              </span>
            </div>

            <div className="flex items-center justify-between">
              <div className="relative flex rounded-full overflow-hidden cursor-pointer">
                <input
                  id="checkbox_2"
                  name="radio"
                  type="checkbox"
                  className="hidden peer"
                />
                <label
                  htmlFor="checkbox_2"
                  className="top-0 bottom-0 left-0 right-0 bg-[#CCCCCC] w-[50px] h-[25px]  peer-checked:bg-[#01274F] cursor-pointer"
                ></label>
                <label
                  htmlFor="checkbox_2"
                  className="absolute transition top-[2px] left-[2px] w-[21px] h-[21px] rounded-full bg-[#fff] peer-checked:translate-x-[25px]"
                ></label>
              </div>
            </div>
          </div>
        </li>

        <li className="flex px-[16px] w-full h-[60px] box-border">
          <div className="flex w-full items-center justify-between">
            <div className="flex items-center gap-x-[8px]">
              <span className="line-clamp-1 text-[#191919] text-[15px] leading-[20px] tracking-[-0.2px]">
                루틴 진행
              </span>
            </div>

            <div className="flex items-center justify-between">
              <div className="relative flex rounded-full overflow-hidden cursor-pointer">
                <input
                  id="checkbox_3"
                  name="radio"
                  type="checkbox"
                  className="hidden peer"
                />
                <label
                  htmlFor="checkbox_3"
                  className="top-0 bottom-0 left-0 right-0 bg-[#CCCCCC] w-[50px] h-[25px]  peer-checked:bg-[#01274F] cursor-pointer"
                ></label>
                <label
                  htmlFor="checkbox_3"
                  className="absolute transition top-[2px] left-[2px] w-[21px] h-[21px] rounded-full bg-[#fff] peer-checked:translate-x-[25px]"
                ></label>
              </div>
            </div>
          </div>
        </li>

        <li className="flex px-[16px] w-full h-[60px] box-border">
          <div className="flex w-full items-center justify-between">
            <div className="flex items-center gap-x-[8px]">
              <span className="line-clamp-1 text-[#191919] text-[15px] leading-[20px] tracking-[-0.2px]">
                준비물
              </span>
            </div>

            <div className="flex items-center justify-between">
              <div className="relative flex rounded-full overflow-hidden cursor-pointer">
                <input
                  id="checkbox_4"
                  name="radio"
                  type="checkbox"
                  className="hidden peer"
                />
                <label
                  htmlFor="checkbox_4"
                  className="top-0 bottom-0 left-0 right-0 bg-[#CCCCCC] w-[50px] h-[25px]  peer-checked:bg-[#01274F] cursor-pointer"
                ></label>
                <label
                  htmlFor="checkbox_4"
                  className="absolute transition top-[2px] left-[2px] w-[21px] h-[21px] rounded-full bg-[#fff] peer-checked:translate-x-[25px]"
                ></label>
              </div>
            </div>
          </div>
        </li>

        <li className="flex px-[16px] w-full h-[60px] box-border">
          <div className="flex w-full items-center justify-between">
            <div className="flex items-center gap-x-[8px]">
              <span className="line-clamp-1 text-[#191919] text-[15px] leading-[20px] tracking-[-0.2px]">
                돌발
              </span>
            </div>

            <div className="flex items-center justify-between">
              <div className="relative flex rounded-full overflow-hidden cursor-pointer">
                <input
                  id="checkbox_5"
                  name="radio"
                  type="checkbox"
                  className="hidden peer"
                />
                <label
                  htmlFor="checkbox_5"
                  className="top-0 bottom-0 left-0 right-0 bg-[#CCCCCC] w-[50px] h-[25px]  peer-checked:bg-[#01274F] cursor-pointer"
                ></label>
                <label
                  htmlFor="checkbox_5"
                  className="absolute transition top-[2px] left-[2px] w-[21px] h-[21px] rounded-full bg-[#fff] peer-checked:translate-x-[25px]"
                ></label>
              </div>
            </div>
          </div>
        </li>

        <li className="flex px-[16px] w-full h-[60px] bg-[#F5F5F5] box-border">
          <div className="flex w-full items-center justify-between">
            <div className="flex items-center gap-x-[8px]">
              <span className="line-clamp-1 text-[#999999] text-[15px] leading-[20px] tracking-[-0.2px]">
                AI프롬프트
              </span>
            </div>
          </div>
        </li>

        <li className="flex px-[16px] w-full h-[60px] box-border">
          <div className="flex w-full items-center justify-between">
            <div className="flex items-center gap-x-[8px]">
              <span className="line-clamp-1 text-[#191919] text-[15px] leading-[20px] tracking-[-0.2px]">
                읽어주기
              </span>
            </div>

            <div className="flex items-center justify-between">
              <div className="relative flex rounded-full overflow-hidden cursor-pointer">
                <input
                  id="checkbox_6"
                  name="radio"
                  type="checkbox"
                  className="hidden peer"
                />
                <label
                  htmlFor="checkbox_6"
                  className="top-0 bottom-0 left-0 right-0 bg-[#CCCCCC] w-[50px] h-[25px]  peer-checked:bg-[#01274F] cursor-pointer"
                ></label>
                <label
                  htmlFor="checkbox_6"
                  className="absolute transition top-[2px] left-[2px] w-[21px] h-[21px] rounded-full bg-[#fff] peer-checked:translate-x-[25px]"
                ></label>
              </div>
            </div>
          </div>
        </li>

        <li className="flex px-[16px] w-full h-[60px] bg-[#F5F5F5] box-border">
          <div className="flex w-full items-center justify-between">
            <div className="flex items-center gap-x-[8px]">
              <span className="line-clamp-1 text-[#999999] text-[15px] leading-[20px] tracking-[-0.2px]">
                일정 카테고리
              </span>
            </div>
          </div>
        </li>

        <li className="flex px-[16px] w-full h-[60px] box-border">
          <Link
            href="#"
            className="flex w-full items-center justify-between hover:opacity-[0.7]"
          >
            <div className="flex items-center gap-x-[8px]">
              <span className="line-clamp-1 text-[#191919] text-[15px] leading-[20px] tracking-[-0.2px]">
                카테고리 관리
              </span>
            </div>

            <div className="flex items-center justify-between">
              <button className="flex justify-center items-center">
                <svg
                  width="8"
                  height="16"
                  viewBox="0 0 8 16"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                >
                  <path
                    fillRule="evenodd"
                    clipRule="evenodd"
                    d="M0.313115 0.979454C0.508377 0.784192 0.824959 0.784192 1.02022 0.979454L7.68689 7.64612C7.88215 7.84138 7.88215 8.15797 7.68689 8.35323L1.02022 15.0199C0.824959 15.2152 0.508377 15.2152 0.313115 15.0199C0.117852 14.8246 0.117852 14.508 0.313115 14.3128L6.62623 7.99967L0.313115 1.68656C0.117852 1.4913 0.117852 1.17472 0.313115 0.979454Z"
                    fill="#222222"
                  ></path>
                </svg>
              </button>
            </div>
          </Link>
        </li>
      </ul>
    </div>
  );
}
