"use client";
import { useEffect, useState } from "react";
import NavBar from "@/components/common/topNav";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import listPlugin from "@fullcalendar/list";
import koLocale from "@fullcalendar/core/locales/ko";
import { getSchedules } from "@/api/scheduleApi";
import { useNotification } from "@/components/common/NotificationContext";{/*버튼주석*/}

export default function Calendar() {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedEvent, setSelectedEvent] = useState(null);
  const { routineNotificationOpen } = useNotification();{/*버튼주석*/}

  // 일정 데이터 가져오기
  const fetchSchedules = async () => {
    try {
      setLoading(true);

      // 현재 날짜 기준으로 3개월 범위의 일정 조회
      const now = new Date();
      const startDate = new Date(now.getFullYear(), now.getMonth() - 1, 1);
      const endDate = new Date(now.getFullYear(), now.getMonth() + 2, 0);

      // ISO 날짜 형식으로 변환
      const startISO = startDate.toISOString();
      const endISO = endDate.toISOString();

      console.log("조회 시작:", startISO);
      console.log("조회 종료:", endISO);

      // API 호출
      const schedules = await getSchedules(startISO, endISO);
      console.log("가져온 일정:", schedules);

      if (schedules && schedules.length > 0) {
        // 백엔드 데이터를 FullCalendar 이벤트 형식으로 변환
        const formattedEvents = schedules.map((schedule: { id: any; title: any; startTime: any; endTime: any; category: any; location: any; memo: any; routineId: any; }) => ({
          id: schedule.id,
          title: schedule.title,
          start: schedule.startTime,
          end: schedule.endTime,
          color: getCategoryColor(schedule.category),
          extendedProps: {
            location: schedule.location,
            memo: schedule.memo,
            category: schedule.category,
            routineId: schedule.routineId
          }
        }));
        setEvents(formattedEvents);
      }
    } catch (err) {
      console.error("일정 가져오기 오류:", err);
    } finally {
      setLoading(false);
    }
  };

  // 카테고리별 색상 반환
  const getCategoryColor = (category: any) => {
    switch (category) {
      case "DAILY": return "#4285F4";
      case "WORK": return "#0F9D58";
      case "STUDY": return "#F4B400";
      case "EXERCISE": return "#DB4437";
      default: return "#fb8494";
    }
  };

  // 이벤트 클릭 핸들러
  const handleEventClick = (info: { event: { id: any; title: any; start: any; end: any; }; }) => {
    console.log("선택된 이벤트:", info.event);
    // 이벤트 정보 저장
    setSelectedEvent({
      id: info.event.id,
      title: info.event.title,
      start: info.event.start,
      end: info.event.end
    });
  };

  // 수정 페이지로 이동
  const goToEditPage = () => {
    if (selectedEvent) {
      location.href = `/calendar/edit?id=${selectedEvent.id}`;
    } else {
      alert('수정할 일정을 선택해주세요.');
    }
  };

  // 컴포넌트 마운트 시 일정 데이터 로드
  useEffect(() => {
    fetchSchedules();
  }, []);

  return (
      <div className="flex flex-col w-full h-full">
        {/* 툴바 스타일 개선 추가 */}
        <style jsx global>{`
          .fc-toolbar-title {
            font-size: 18px !important;
            font-weight: 500;
          }
          .fc .fc-toolbar.fc-header-toolbar {
            margin-bottom: 15px !important;
          }
          .selected-event {
            border: 2px solid #01274F !important;
            box-shadow: 0 0 5px rgba(1, 39, 79, 0.5) !important;
            transform: scale(1.02);
            transition: all 0.2s ease;
          }
          .fc-list-event.selected-event td {
            background-color: rgba(1, 39, 79, 0.1) !important;
            font-weight: bold;
          }
          /* 오늘 버튼 스타일 */
          .fc-today-button {
            background-color: #6b7280 !important;
            border-color: #6b7280 !important;
            padding: 0.375rem 0.75rem !important;
            font-size: 0.875rem !important;
          }
          /* 화살표 버튼 스타일 */
          .fc-prev-button, .fc-next-button {
            background-color: #1e293b !important;
            border-color: #1e293b !important;
          }
        `}</style>

        <NavBar title="캘린더" link="#" />

        <div className={`z-[999] absolute bottom-[0px] left-[0px] grid grid-cols-2 w-full bg-[#fff] p-[12px] gap-[12px] ${
          routineNotificationOpen ? 'filter blur-sm pointer-events-none' : ''
        }`}>{/*버튼주석*/}
          <button
              className={`hover:opacity-[0.7] cursor-pointer py-[10px] px-[5px] bg-[#fff] border-[1px] 
          ${selectedEvent ? 'border-[#01274F] text-[#01274F]' : 'border-[#999] text-[#999]'} 
          rounded-[7px] text-[15px] tracking-[-0.6px] font-[500]`}
              onClick={goToEditPage}
              type="button"
          >
            {selectedEvent ? '일정 수정' : '일정 선택 필요'}
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
            {/* 레이아웃 구조 개선: gap-y-[20px] 추가, padding 조정 */}
            <div className="flex flex-col gap-y-[20px] p-[15px] h-full">
              <FullCalendar
                  locale={koLocale} // 한국어 로케일 추가
                  plugins={[dayGridPlugin]}
                  initialView="dayGridMonth"
                  headerToolbar={{
                    left: 'prev',
                    center: 'title',
                    right: 'today next'
                  }}
                  events={events}
                  eventClick={handleEventClick}
                  eventClassNames={(arg) => {
                    // 선택된 이벤트에 CSS 클래스 추가
                    return selectedEvent && selectedEvent.id === arg.event.id ?
                        'selected-event' : '';
                  }}
              />

              <FullCalendar
                  locale={koLocale} // 한국어 로케일 추가
                  plugins={[listPlugin]}
                  initialView="listWeek"
                  headerToolbar={{
                    left: 'prev',
                    center: 'title',
                    right: 'today next'
                  }}
                  events={events}
                  eventClick={handleEventClick}
                  eventClassNames={(arg) => {
                    return selectedEvent && selectedEvent.id === arg.event.id ?
                        'selected-event' : '';
                  }}
              />
            </div>
          </div>
        </div>
      </div>
  );
}