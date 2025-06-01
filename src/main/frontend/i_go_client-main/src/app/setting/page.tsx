// src/app/setting/page.tsx
"use client";

import NavBar from "@/components/common/topNav";
import { useEffect, useState, useCallback } from "react";
import { getNotificationSettings, updateNotificationSettings } from "@/api/userApi.js"; // 경로는 실제 프로젝트에 맞게 조정

interface NotificationSettingsDto {
  notificationsEnabled: boolean;
  notifyTodaySchedule: boolean;
  notifyNextSchedule: boolean;
  notifyRoutineProgress: boolean;
  notifySupplies: boolean;
  notifyUnexpectedEvent: boolean;
  notifyAiFeature: boolean;
}

// 서버 응답 타입 (UserController 반환값 기준)
interface UpdateNotificationSettingsResponse {
  message: string;
  settings: NotificationSettingsDto;
}

// 모든 개별 알림 설정 키 목록
const individualSettingKeys: (keyof Omit<NotificationSettingsDto, 'notificationsEnabled'>)[] = [
  'notifyTodaySchedule',
  'notifyNextSchedule',
  'notifyRoutineProgress',
  'notifySupplies',
  'notifyUnexpectedEvent',
  'notifyAiFeature',
];

// 초기 상태값 (모든 스위치 off)
const initialSettings: NotificationSettingsDto = {
  notificationsEnabled: false,
  notifyTodaySchedule: false,
  notifyNextSchedule: false,
  notifyRoutineProgress: false,
  notifySupplies: false,
  notifyUnexpectedEvent: false,
  notifyAiFeature: false,
};

export default function SettingPage() {
  const [settings, setSettings] = useState<NotificationSettingsDto>(initialSettings);
  const [error, setError] = useState<string | null>(null);

  // 서버에서 설정 정보 로드
  useEffect(() => {
    const fetchSettings = async () => {
      try {
        setError(null);
        const serverSettings = await getNotificationSettings();
        // 서버에서 받은 개별 설정을 기준으로 notificationsEnabled 상태 결정
        const allIndividualEnabled = individualSettingKeys.every(key => serverSettings[key] === true);
        setSettings({ ...serverSettings, notificationsEnabled: allIndividualEnabled });
      } catch (err) {
        console.error("Failed to fetch notification settings:", err);
        setError("설정 정보를 불러오는데 실패했습니다. 기본값으로 표시됩니다.");
        // 실패 시 initialSettings 유지
      }
    };
    fetchSettings();
  }, []);

  const handleToggleChange = useCallback(async (key: keyof NotificationSettingsDto) => {
    setError(null);
    const previousSettings = { ...settings }; // 롤백을 위한 이전 상태 저장

    // 1. 다음 상태를 계산 (낙관적 업데이트를 위해)
    let newTentativeSettings: NotificationSettingsDto;
    const toggledValue = !previousSettings[key];

    if (key === 'notificationsEnabled') {
      // 전체 스위치 변경: 모든 개별 스위치를 새 값으로 설정
      newTentativeSettings = { ...previousSettings, notificationsEnabled: toggledValue };
      individualSettingKeys.forEach(individualKey => {
        newTentativeSettings[individualKey] = toggledValue;
      });
    } else {
      // 개별 스위치 변경: 해당 스위치 값 변경 후, 전체 스위치 상태 재계산
      newTentativeSettings = { ...previousSettings, [key]: toggledValue };
      const allIndividualEnabled = individualSettingKeys.every(
          individualKey => newTentativeSettings[individualKey] === true
      );
      newTentativeSettings.notificationsEnabled = allIndividualEnabled;
    }

    // 2. UI 즉시 업데이트
    setSettings(newTentativeSettings);

    // 3. API 호출하여 서버에 변경 사항 전송
    try {
      // API에는 모든 필드를 포함한 newTentativeSettings 객체를 전송
      const response: UpdateNotificationSettingsResponse = await updateNotificationSettings(newTentativeSettings);
      // 서버로부터 받은 최종 설정으로 로컬 상태 동기화
      setSettings(response.settings);
    } catch (err) {
      console.error("Failed to update notification settings:", err);
      setError("설정 업데이트에 실패했습니다. 변경 사항이 저장되지 않았을 수 있습니다.");
      setSettings(previousSettings); // 오류 발생 시 이전 상태로 롤백
    }
  }, [settings]); // settings가 변경될 때마다 함수 재생성 (롤백 및 최신 상태 참조 위함)

  // 에러 발생 시 UI
  if (error && !settings) { // 초기 로드 실패 시 에러만 표시 (선택적)
    return (
        <div className="flex flex-col w-full h-full">
          <NavBar title="환경설정" link="/setting" />
          <p className="p-4 text-center text-red-500">{error}</p>
        </div>
    );
  }

  return (
      <div className="flex flex-col w-full h-full">
        <NavBar title="환경설정" link="/setting" />
        {error && <p className="p-4 text-center text-red-500 bg-red-100">{error}</p>}
        <ul className="flex h-full flex-col bg-[#fff]">
          {/* 전체 On/Off */}
          <li className="flex px-[16px] w-full h-[60px] bg-[#F5F5F5] box-border">
            <div className="flex w-full items-center justify-between">
              <span className="text-[#999999] text-[15px]">푸시알림</span>
              <div className="flex items-center">
                <span className="mr-[8px] text-[#999999] text-[15px]">전체 On/Off</span>
                <div className="relative flex rounded-full overflow-hidden cursor-pointer">
                  <input
                      id="checkbox-all"
                      type="checkbox"
                      className="hidden peer"
                      checked={settings.notificationsEnabled}
                      onChange={() => handleToggleChange('notificationsEnabled')}
                  />
                  <label htmlFor="checkbox-all" className="top-0 bottom-0 left-0 right-0 bg-[#CCCCCC] w-[50px] h-[25px] peer-checked:bg-[#01274F] cursor-pointer"></label>
                  <label htmlFor="checkbox-all" className="absolute transition top-[2px] left-[2px] w-[21px] h-[21px] rounded-full bg-[#fff] peer-checked:translate-x-[25px]"></label>
                </div>
              </div>
            </div>
          </li>

          {/* 오늘 일정 */}
          <SettingItem
              id="checkbox_1"
              label="오늘 일정"
              checked={settings.notifyTodaySchedule}
              onChange={() => handleToggleChange('notifyTodaySchedule')}
          />
          {/* 다음 일정 */}
          <SettingItem
              id="checkbox_2"
              label="다음 일정"
              checked={settings.notifyNextSchedule}
              onChange={() => handleToggleChange('notifyNextSchedule')}
          />
          {/* 루틴 진행 */}
          <SettingItem
              id="checkbox_3"
              label="루틴 진행"
              checked={settings.notifyRoutineProgress}
              onChange={() => handleToggleChange('notifyRoutineProgress')}
          />
          {/* 준비물 */}
          <SettingItem
              id="checkbox_4"
              label="준비물"
              checked={settings.notifySupplies}
              onChange={() => handleToggleChange('notifySupplies')}
          />
          {/* 돌발 */}
          <SettingItem
              id="checkbox_5"
              label="돌발"
              checked={settings.notifyUnexpectedEvent}
              onChange={() => handleToggleChange('notifyUnexpectedEvent')}
          />

          {/* AI 프롬프트 섹션 타이틀 */}
          <li className="flex px-[16px] w-full h-[60px] bg-[#F5F5F5] box-border items-center">
            <span className="text-[#999999] text-[15px]">AI프롬프트</span>
          </li>
          {/* 읽어주기 */}
          <SettingItem
              id="checkbox_6"
              label="읽어주기"
              checked={settings.notifyAiFeature}
              onChange={() => handleToggleChange('notifyAiFeature')}
          />

          {/* 카테고리 관리 등 나머지 UI */}
        </ul>
      </div>
  );
}

// 반복되는 UI를 위한 간이 컴포넌트 (disabled 관련 로직 제거)
interface SettingItemProps {
  id: string;
  label: string;
  checked: boolean;
  onChange: () => void;
}
const SettingItem: React.FC<SettingItemProps> = ({ id, label, checked, onChange }) => (
    <li className={`flex px-[16px] w-full h-[60px] box-border`}>
      <div className="flex w-full items-center justify-between">
        <span className={`text-[15px] text-[#191919]`}>{label}</span>
        <div className="relative flex rounded-full overflow-hidden cursor-pointer">
          <input
              id={id}
              type="checkbox"
              className="hidden peer"
              checked={checked}
              onChange={onChange}
          />
          <label htmlFor={id} className={`top-0 bottom-0 left-0 right-0 bg-[#CCCCCC] w-[50px] h-[25px] peer-checked:bg-[#01274F] cursor-pointer`}></label>
          <label htmlFor={id} className={`absolute transition top-[2px] left-[2px] w-[21px] h-[21px] rounded-full bg-[#fff] peer-checked:translate-x-[25px]`}></label>
        </div>
      </div>
    </li>
);