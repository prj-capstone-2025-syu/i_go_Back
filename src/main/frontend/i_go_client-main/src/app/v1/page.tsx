"use client";
import Link from "next/link";

export default function Home() {
  const screenNumbers = Array.from({ length: 26 }, (_, i) => i + 1);

  return (
    <div className="flex flex-col w-full h-full px-[15px]">
      <style jsx global>{`
        #layout-wrapper {
          max-width: 70% !important;
          margin: 0 auto !important;
          transition: 0s !important;
        }
        .main-wrapper {
          height: 100dvh !important;
        }
        .bottom-nav-warpper {
          display: none !important;
        }
      `}</style>
      <div className="w-full max-h-full overflow-y-auto grid lg:grid-cols-3 gap-[40px]">
        {screenNumbers.map((num) => {
          const screenSuffix = String(num).padStart(3, "0");
          const screenId = `i-v1-${screenSuffix}`;
          const screenPath = `/v1/${screenId}`;
          const iframeId = `Example_${screenId}`;

          return (
            <div className="w-full flex flex-col" key={screenId}>
              {" "}
              <div className="flex items-center justify-between pb-[15px] border-b-[1px] border-[#dfdfdf] md:!border-[0px]">
                <p className="text-[#1a1a1a] text-[15px] font-[700] tracking-[-0.4px]">
                  화면 : {screenId}
                </p>
                <Link
                  href={screenPath}
                  className="text-[#01274F] underline text-[14px] font-[600] tracking-[-0.4px]"
                >
                  화면으로 이동
                </Link>
              </div>
              <iframe
                id={iframeId}
                title={`iframe ${screenId}`}
                className="w-full aspect-[9/15] shadow-xl rounded-[8px]"
                src={screenPath}
              ></iframe>
            </div>
          );
        })}
      </div>
    </div>
  );
}
