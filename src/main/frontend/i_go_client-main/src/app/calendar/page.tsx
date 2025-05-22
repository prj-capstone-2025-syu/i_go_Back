"use client";
import NavBar from "@/components/common/topNav";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import listPlugin from "@fullcalendar/list";

// Metadata 객체 export

export default function Calendar() {
  const events = [
    {
      title: "아침 먹기",
      start: "2025-05-17",
      end: "2025-05-17",
      color: "#fb8494",
    },
    {
      title: "점심 먹기",
      start: "2025-05-19",
      end: "2025-05-20",
      color: "#fb8494",
    },
    {
      title: "저녁 먹기",
      start: "2025-05-23",
      end: "2025-05-30",
      color: "#fb8494",
    },
  ];

  return (
    <div className="flex flex-col w-full h-full">
      <NavBar title="캘린더" link="#" />
      <div className="z-[999] absolute bottom-[0px] left-[0px]  grid grid-cols-2 w-full bg-[#fff] p-[12px] gap-[12px]">
        <button
          className="hover:opacity-[0.7] cursor-pointer py-[10px] px-[5px] bg-[#fff] border-[1px] border-[#01274F] rounded-[7px] text-[#01274F] text-[15px] tracking-[-0.6px] font-[500]"
          onClick={() => {
            location.href = "/calendar/edit";
          }}
          type="button"
        >
          일정 수정
        </button>
        <button
          className="hover:opacity-[0.7] cursor-pointer py-[10px] px-[5px] bg-[#01274F] border-[1px] border-[#01274F] rounded-[7px] text-[#fff] text-[15px] tracking-[-0.6px] font-[500]"
          onClick={() => {
            location.href = "/calendar/create";
          }}
          type="button"
        >
          일정 등록
        </button>
      </div>
      <div className="w-full max-h-full overflow-y-auto">
        <div className="w-full max-h-full overflow-y-auto">
          <div className="p-[20px] h-full">
            <FullCalendar
              initialView="dayGridMonth"
              plugins={[dayGridPlugin]}
              events={events}
            />
            <FullCalendar
              plugins={[listPlugin]}
              initialView="listWeek"
              events={events}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
