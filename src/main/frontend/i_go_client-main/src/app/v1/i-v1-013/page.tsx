"use client";
import NavBar from "@/components/common/topNav";
import Link from "next/link";

export default function Home() {
  return (
      <div className="flex flex-col w-full h-full">
        <NavBar title="일정상세" link="/mypage"></NavBar>
        <div className="absolute bottom-[0px] left-[0px]  grid grid-cols-2 w-full bg-[#fff] p-[12px] gap-[12px]">
          <button
              className="hover:opacity-[0.7] cursor-pointer py-[10px] px-[5px] bg-[#fff] border-[1px] border-[#01274F] rounded-[7px] text-[#01274F] text-[15px] tracking-[-0.6px] font-[500]"
              onClick={() => {
                location.href = "#";
              }}
              type="button"
          >
            일정 삭제
          </button>
          <button
              className="hover:opacity-[0.7] cursor-pointer py-[10px] px-[5px] bg-[#01274F] border-[1px] border-[#01274F] rounded-[7px] text-[#fff] text-[15px] tracking-[-0.6px] font-[500]"
              onClick={() => {
                location.href = "#";
              }}
              type="button"
          >
            일정 수정
          </button>
        </div>
        <div className="w-full max-h-full overflow-y-auto">
          <div className="flex flex-col items-center justify-start p-[20px] w-full h-auto">
            {/* 프롬프트 입력창 */}
            <div className="relative w-full 2xl:max-w-[781px] mb-[22px]">
              <input
                  type="text"
                  className="bg-[#fff] !outline-none border-[1px] border-[#DFDFDF] shadow-[0px_0px_5px_rgba(0,0,0,0.2)] rounded-[6px] pr-[38px] pl-[15px] py-[12px] w-full font-[400] text-[15px] leading-[20px] text-[#383838] placeholder:!text-[#949494] focus:border-[#01274F]"
                  placeholder="아이고 AI - 무엇을 도와드릴까요?"
              />
              <div className="absolute flex right-[11px] !top-[50%] !translate-y-[-50%]">
                <button className="p-[4px]" type="submit">
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
            </div>
            {/* 진행중인 일정 */}
            <div className="flex justify-between items-end w-full mb-[8px] px-[5px]">
              <p className="text-[#01274F] text-[19px] font-[700] tracking-[-0.4px]">
                2025년 5월 28일
              </p>
            </div>
            <div className="w-full bg-[#fff] p-[15px] rounded-[6px] shadow-[0px_0px_5px_rgba(0,0,0,0.2)] mb-[22px]">
              <div className="flex justify-between items-center w-full mb-[8px] ">
                <p className="text-[#383838] text-[17px] font-[500] tracking-[-0.4px] leading-[155%] line-clamp-1">
                  삼육이와 맛집&카페 탐방
                </p>
                <div className="flex items-center gap-x-[1px]">
                  <img src="/icon/clock.svg"></img>

                  <p className="text-[#0080FF] text-[16px] font-[500] tracking-[-0.4px] leading-[160%]">
                    13:30
                  </p>
                </div>
              </div>
              <p className="text-[#383838] text-[15px] font-[400] tracking-[-0.1px] leading-[160%] line-clamp-1">
                장소 : <span>강남역 2번출구</span>
              </p>
              <p className="text-[#383838] text-[15px] font-[400] tracking-[-0.1px] leading-[160%] line-clamp-1">
                루틴 :{" "}
                <span className="font-[500] tracking-[-0.4px] bg-[#0080FF] text-[#fff] px-[7px] rounded-[10px] leading-[120%]">
                학교에서 출발(35분)
              </span>
              </p>
              <p className="text-[#383838] text-[15px] font-[400] tracking-[-0.1px] leading-[160%]">
                준비물 : <span>지갑</span>
                <span className="font-[600]">, 우산(자동추가)</span>
              </p>
              <p className="text-[#383838] text-[15px] font-[400] tracking-[-0.1px] leading-[160%]">
                메모 : <span>삼육이가 식당 미리 검색해보라고 함</span>
              </p>
              <div className="my-[10px] w-full h-[1px] bg-[#dfdfdf]"></div>
              <p className="text-[#383838] text-[16px] font-[500] tracking-[-0.8px] leading-[155%] line-clamp-1 mb-[7px]">
                실시간 예상 소요시간
              </p>
              <div className=" grid grid-cols-3">
                <div className="h-auto flex gap-x-[5px] items-center justify-center">
                  <svg
                      width="22"
                      height="22"
                      viewBox="0 0 22 22"
                      fill="none"
                      xmlns="http://www.w3.org/2000/svg"
                      xlinkHref="http://www.w3.org/1999/xlink"
                  >
                    <rect width="22" height="22" fill="url(#pattern0_298_2791)" />
                    <defs>
                      <pattern
                          id="pattern0_298_2791"
                          patternContentUnits="objectBoundingBox"
                          width="1"
                          height="1"
                      >
                        <use
                            xlinkHref="#image0_298_2791"
                            transform="scale(0.00195312)"
                        />
                      </pattern>
                      <image
                          id="image0_298_2791"
                          width="512"
                          height="512"
                          preserveAspectRatio="none"
                          xlinkHref=""
                      />
                    </defs>
                  </svg>

                  <p className="group-hover:!text-[#fff] text-[#01274F] text-[14px] font-[500] tracking-[-0.8px] leading-[110%]">
                    30분
                  </p>
                </div>
                <div className="h-auto flex gap-x-[5px] items-center justify-center">
                  <svg
                      width="22"
                      height="22"
                      viewBox="0 0 22 22"
                      fill="none"
                      xmlns="http://www.w3.org/2000/svg"
                      xlinkHref="http://www.w3.org/1999/xlink"
                  >
                    <rect width="22" height="22" fill="url(#pattern0_298_2792)" />
                    <defs>
                      <pattern
                          id="pattern0_298_2792"
                          patternContentUnits="objectBoundingBox"
                          width="1"
                          height="1"
                      >
                        <use
                            xlinkHref="#image0_298_2792"
                            transform="scale(0.00195312)"
                        />
                      </pattern>
                      <image
                          id="image0_298_2792"
                          width="512"
                          height="512"
                          preserveAspectRatio="none"
                          xlinkHref=""
                      />
                    </defs>
                  </svg>

                  <p className="group-hover:!text-[#fff] text-[#01274F] text-[14px] font-[500] tracking-[-0.8px] leading-[110%]">
                    1시간 5분
                  </p>
                </div>
                <div className="h-auto flex gap-x-[5px] items-center justify-center">
                  <svg
                      width="22"
                      height="22"
                      viewBox="0 0 22 22"
                      fill="none"
                      xmlns="http://www.w3.org/2000/svg"
                      xlinkHref="http://www.w3.org/1999/xlink"
                  >
                    <rect width="22" height="22" fill="url(#pattern0_298_2793)" />
                    <defs>
                      <pattern
                          id="pattern0_298_2793"
                          patternContentUnits="objectBoundingBox"
                          width="1"
                          height="1"
                      >
                        <use
                            xlinkHref="#image0_298_2793"
                            transform="scale(0.00195312)"
                        />
                      </pattern>
                      <image
                          id="image0_298_2793"
                          width="512"
                          height="512"
                          preserveAspectRatio="none"
                          xlinkHref=""
                      />
                    </defs>
                  </svg>

                  <p className="group-hover:!text-[#fff] text-[#01274F] text-[14px] font-[500] tracking-[-0.8px] leading-[110%]">
                    5시간 10분
                  </p>
                </div>
              </div>
              <Link
                  href="https://map.kakao.com/?map_type=TYPE_MAP&target=car&rt=%2C%2C537811%2C927642&rt1=%EC%82%BC%EC%9C%A1%EB%8C%80%ED%95%99%EA%B5%90&rt2=%EA%B0%95%EB%82%A8%EC%97%AD2%EB%B2%88%EC%B6%9C%EA%B5%AC&rtIds=%2C&rtTypes=%2C"
                  target="_blank"
                  className="px-[5px] py-[10px] border-[#dfdfdf] border-[1px] rounded-[5px] hover:opacity-[0.7] w-full flex items-center justify-center gap-x-[5px] mt-[10px]"
              >
                <svg
                    width="22"
                    height="22"
                    viewBox="0 0 22 22"
                    fill="none"
                    xmlns="http://www.w3.org/2000/svg"
                    xlinkHref="http://www.w3.org/1999/xlink"
                >
                  <rect
                      width="22"
                      height="22"
                      fill="url(#pattern0_298_2746dafasdfa)"
                  />
                  <defs>
                    <pattern
                        id="pattern0_298_2746dafasdfa"
                        patternContentUnits="objectBoundingBox"
                        width="1"
                        height="1"
                    >
                      <use
                          xlinkHref="#image0_298_2746"
                          transform="translate(-3.68301 -1.77451) scale(0.00326797)"
                      />
                    </pattern>
                    <image
                        id="image0_298_2746"
                        width="2560"
                        height="1440"
                        preserveAspectRatio="none"
                        xlinkHref=""
                    />
                  </defs>
                </svg>
                <p className="text-[#383838] text-[14px] tracking-[-0.2px] font-[400]">
                  TMAP 빠른 길찾기
                </p>
              </Link>
              <div className="mt-[20px] mb-[13px] w-full h-[1px] bg-[#dfdfdf]"></div>
              <div className="flex justify-between items-center w-full mb-[8px] ">
                <p className="text-[#383838] text-[17px] font-[600] tracking-[-0.4px] leading-[110%] line-clamp-1">
                  설정된 루틴
                </p>
                <div className="flex items-center gap-x-[1px]">
                  <span>🏃‍♂️&nbsp;</span>
                  <span className="text-[#01274f] text-[15px] font-[600] tracking-[-0.8px] leading-[102%] line-clamp-1">
                  집 -&gt; 학교 루틴 (65분)
                </span>
                </div>
              </div>
              <div className="flex flex-col gap-[9px]">
                <div className="w-full h-auto px-[10px] py-[5px] bg-[#0080FF]/40 rounded-[6px] flex items-center justify-between">
                  <p className="text-[#fff] text-[14px] font-[600] tracking-[-0.5px] leading-[102%] line-clamp-1">
                    모닝 커피 내리기
                  </p>
                  <div className="flex items-center gap-x-[1px]">
                    <span>⏭️&nbsp;</span>
                    <span className="text-[#fff] text-[15px] font-[600] tracking-[-0.8px] leading-[102%] line-clamp-1">
                    5분
                  </span>
                    <span className="text-[#fff] text-[15px] font-[600] tracking-[-0.8px] leading-[102%] line-clamp-1">
                    &nbsp; 대기 중
                  </span>
                  </div>
                </div>
                <div className="w-full h-auto px-[10px] py-[5px] bg-[#0080FF]/40 rounded-[6px] flex items-center justify-between">
                  <p className="text-[#fff] text-[14px] font-[600] tracking-[-0.5px] leading-[102%] line-clamp-1">
                    머리 감기
                  </p>
                  <div className="flex items-center gap-x-[1px]">
                    <span>⏭️&nbsp;</span>
                    <span className="text-[#fff] text-[15px] font-[600] tracking-[-0.8px] leading-[102%] line-clamp-1">
                    10분
                  </span>
                    <span className="text-[#fff] text-[15px] font-[600] tracking-[-0.8px] leading-[102%] line-clamp-1">
                    &nbsp; 대기 중
                  </span>
                  </div>
                </div>

                <div className="w-full h-auto px-[10px] py-[5px] bg-[#0080FF]/40 rounded-[6px] flex items-center justify-between">
                  <p className="text-[#fff] text-[14px] font-[600] tracking-[-0.5px] leading-[102%] line-clamp-1">
                    세수 하기
                  </p>
                  <div className="flex items-center gap-x-[1px]">
                    <span>⏭️&nbsp;</span>
                    <span className="text-[#fff] text-[15px] font-[600] tracking-[-0.8px] leading-[102%] line-clamp-1">
                    10분
                  </span>
                    <span className="text-[#fff] text-[15px] font-[600] tracking-[-0.8px] leading-[102%] line-clamp-1">
                    &nbsp; 대기 중
                  </span>
                  </div>
                </div>
                <div className="w-full h-auto px-[10px] py-[5px] bg-[#0080FF]/40 rounded-[6px] flex items-center justify-between">
                  <p className="text-[#fff] text-[14px] font-[600] tracking-[-0.5px] leading-[102%] line-clamp-1">
                    머리 말리기
                  </p>
                  <div className="flex items-center gap-x-[1px]">
                    <span>⏭️&nbsp;</span>
                    <span className="text-[#fff] text-[15px] font-[600] tracking-[-0.8px] leading-[102%] line-clamp-1">
                    10분
                  </span>
                    <span className="text-[#fff] text-[15px] font-[600] tracking-[-0.8px] leading-[102%] line-clamp-1">
                    &nbsp; 대기 중
                  </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
  );
}
