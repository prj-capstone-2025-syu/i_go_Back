// components/common/Skeleton.tsx
const Skeleton = ({ className = "", width = "100%", height = "20px" }) => (
    <div
        className={`animate-pulse bg-gray-200 rounded ${className}`}
        style={{ width, height }}
    />
);

const ScheduleSkeleton = () => (
    <div className="w-full bg-[#fff] p-[15px] rounded-[6px] shadow-[0px_0px_5px_rgba(0,0,0,0.2)] mb-[22px]">
        <div className="flex justify-between items-center w-full mb-[8px]">
            <Skeleton width="60%" height="24px" />
            <div className="flex items-center gap-x-[1px]">
                <Skeleton width="16px" height="16px" className="rounded-full" />
                <Skeleton width="40px" height="20px" />
            </div>
        </div>
        <Skeleton width="80%" height="16px" className="mb-2" />
        <Skeleton width="70%" height="16px" className="mb-2" />
        <Skeleton width="90%" height="16px" className="mb-4" />

        {/* 루틴 아이템들 */}
        <div className="flex flex-col gap-[9px]">
            {[...Array(3)].map((_, i) => (
                <div key={i} className="w-full h-auto px-[10px] py-[5px] bg-gray-100 rounded-[6px] flex items-center justify-between">
                    <Skeleton width="60%" height="16px" />
                    <Skeleton width="80px" height="16px" />
                </div>
            ))}
        </div>
    </div>
);

const UpcomingSchedulesSkeleton = () => (
    <div className="w-full bg-[#fff] p-[15px] rounded-[6px] shadow-[0px_0px_5px_rgba(0,0,0,0.2)] mb-[22px]">
        {[...Array(3)].map((_, i) => (
            <div key={i} className="flex items-start flex-col justify-between gap-y-[4px] w-full border-b-[1px] border-[#dfdfdf] py-[7px] px-[10px] last:!border-[0px]">
                <Skeleton width="80%" height="16px" />
                <div className="flex flex-row gap-x-[15px]">
                    <Skeleton width="120px" height="14px" />
                </div>
            </div>
        ))}
    </div>
);
