"use client";
import NavBar from "@/components/common/topNav";
import Link from "next/link";
import { useEffect, useState } from "react";
import { getRoutines } from "@/api/routineApi";

// 루틴 타입 정의
interface RoutineItem {
  id: number;
  name: string;
  durationMinutes: number;
  flexibleTime: boolean;
  orderIndex: number;
}

interface Routine {
  id: number;
  name: string;
  items: RoutineItem[];
  totalDurationMinutes: number;
}

export default function Home() {
  // 루틴 목록 상태 관리
  const [routines, setRoutines] = useState<Routine[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [showRoutines, setShowRoutines] = useState<boolean>(false);

  // 컴포넌트 마운트 시 루틴 데이터 가져오기
  useEffect(() => {
    const fetchRoutines = async () => {
      try {
        setLoading(true);
        const data = await getRoutines();
        setRoutines(data);
        setError(null);
      } catch (err) {
        console.error("루틴 목록 가져오기 실패:", err);
        setError("루틴 목록을 불러오는데 실패했습니다");
      } finally {
        setLoading(false);
      }
    };

    fetchRoutines();
  }, []);

  // 로딩이 완료되면 애니메이션 트리거
  useEffect(() => {
    if (!loading && routines.length > 0) {
      // 짧은 지연 후 애니메이션 트리거
      const timer = setTimeout(() => {
        setShowRoutines(true);
      }, 100);
      return () => clearTimeout(timer);
    }
  }, [loading, routines]);

  return (
      <div className="flex flex-col w-full h-full">
        <NavBar title="나의 루틴" link="/mypage"></NavBar>
        <div className="absolute bottom-[0px] left-[0px] grid grid-cols-1 w-full bg-[#fff] p-[12px] gap-[12px]">
          <button
              className="hover:opacity-[0.7] cursor-pointer py-[10px] px-[5px] bg-[#01274F] border-[1px] border-[#01274F] rounded-[7px] text-[#fff] text-[15px] tracking-[-0.6px] font-[500]"
              onClick={() => {
                location.href = "/mypage/routine-create";
              }}
              type="button"
          >
            새 루틴 만들기
          </button>
        </div>
        <div className="w-full max-h-full overflow-y-auto">
          <div className="flex flex-col items-center justify-start p-[20px] w-full h-auto">
            {loading ? (
                <p className="text-center"> </p>
            ) : error ? (
                <p className="text-center text-red-500">{error}</p>
            ) : routines.length === 0 ? (
                <p className="text-center">등록된 루틴이 없습니다</p>
            ) : (
                <div className="w-full grid grid-cols-3 gap-[15px] transition-opacity duration-700 ease-in-out"
                     style={{ opacity: showRoutines ? 1 : 0, transform: showRoutines ? 'translateY(0)' : 'translateY(10px)' }}>
                  {routines.map((routine, index) => (
                      <Link
                          key={routine.id}
                          href={`/mypage/routine-detail?id=${routine.id}`}
                          className={`text-[#383838] text-center text-[14px] bg-[#fff] rounded-[5px] aspect-square shadow-sm flex items-center justify-center flex-col hover:opacity-[0.7] transition-all duration-500 ease-in-out`}
                          style={{
                            animationDelay: `${index * 100}ms`,
                            opacity: showRoutines ? 1 : 0,
                            transform: showRoutines ? 'scale(1)' : 'scale(0.95)',
                            transition: `opacity 500ms ease-in-out ${index * 100}ms, transform 500ms ease-in-out ${index * 100}ms`
                          }}
                      >
                        <p>{routine.name || "이름 없는 루틴"}</p>
                        <p className="text-[12px] text-gray-500">
                          {routine.totalDurationMinutes}분
                        </p>
                      </Link>
                  ))}
                </div>
            )}
          </div>
        </div>
      </div>
  );
}