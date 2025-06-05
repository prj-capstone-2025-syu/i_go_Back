"use client";

import React, { useState, useEffect, useRef, Suspense } from "react";
import { useSearchParams } from "next/navigation"; // App Router에서 URL 파라미터를 읽기 위함
import NavBarMain from "@/components/common/topNavMain"; // 필요하다면 NavBarMain을 여기에 추가
import { sendChatMessage, handleAIFunction } from "@/api/chatApi"; // sendChatMessage를 sendMessage로 변경
import { format } from "date-fns";
import { ko } from "date-fns/locale";
import { createSchedule, getSchedules } from "@/api/scheduleApi";

// --- 아이콘 컴포넌트 정의 ---
interface IconProps extends React.SVGProps<SVGSVGElement> {}

const ChevronLeftIcon: React.FC<IconProps> = (props) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    fill="none"
    viewBox="0 0 24 24"
    strokeWidth="1.5"
    stroke="currentColor"
    aria-hidden="true"
    {...props}
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      d="M15.75 19.5L8.25 12l7.5-7.5"
    ></path>
  </svg>
);
const SearchIcon: React.FC<IconProps> = (props) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    fill="none"
    viewBox="0 0 24 24"
    strokeWidth="1.5"
    stroke="currentColor"
    aria-hidden="true"
    {...props}
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z"
    ></path>
  </svg>
);
const DotsVerticalIcon: React.FC<IconProps> = (props) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    fill="none"
    viewBox="0 0 24 24"
    strokeWidth="1.5"
    stroke="currentColor"
    aria-hidden="true"
    {...props}
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      d="M12 6.75a.75.75 0 110-1.5.75.75 0 010 1.5zM12 12.75a.75.75 0 110-1.5.75.75 0 010 1.5zM12 18.75a.75.75 0 110-1.5.75.75 0 010 1.5z"
    ></path>
  </svg>
);
const ProfileInformationIcon: React.FC<IconProps> = (props) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    fill="none"
    viewBox="0 0 24 24"
    strokeWidth="1.5"
    stroke="currentColor"
    aria-hidden="true"
    {...props}
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      d="M11.25 11.25l.041-.02a.75.75 0 011.063.852l-.708 2.836a.75.75 0 001.063.853l.041-.021M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-9-3.75h.008v.008H12V8.25z"
    ></path>
  </svg>
);
const VoiceCallIcon: React.FC<IconProps> = (props) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    fill="none"
    viewBox="0 0 24 24"
    strokeWidth="1.5"
    stroke="currentColor"
    aria-hidden="true"
    {...props}
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      d="M2.25 6.75c0 8.284 6.716 15 15 15h2.25a2.25 2.25 0 002.25-2.25v-1.372c0-.516-.351-.966-.852-1.091l-4.423-1.106c-.44-.11-.902.055-1.173.417l-.97 1.293c-.282.376-.769.542-1.21.38a12.035 12.035 0 01-7.143-7.143c-.162-.441.004-.928.38-1.21l1.293-.97c.363-.271.527-.734.417-1.173L6.963 3.102a1.125 1.125 0 00-1.091-.852H4.5A2.25 2.25 0 002.25 4.5v2.25z"
    ></path>
  </svg>
);
const SharedMediaIcon: React.FC<IconProps> = (props) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    fill="none"
    viewBox="0 0 24 24"
    strokeWidth="1.5"
    stroke="currentColor"
    aria-hidden="true"
    {...props}
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      d="M7.217 10.907a2.25 2.25 0 100 2.186m0-2.186c.18.324.283.696.283 1.093s-.103.77-.283 1.093m0-2.186l9.566-5.314m-9.566 7.5l9.566 5.314m0 0a2.25 2.25 0 103.935 2.186 2.25 2.25 0 00-3.935-2.186zm0-12.814a2.25 2.25 0 103.933-2.185 2.25 2.25 0 00-3.933 2.185z"
    ></path>
  </svg>
);
const BlockContactIcon: React.FC<IconProps> = (props) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    fill="none"
    viewBox="0 0 24 24"
    strokeWidth="1.5"
    stroke="currentColor"
    aria-hidden="true"
    {...props}
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636"
    ></path>
  </svg>
);
const AttachmentIcon: React.FC<IconProps> = (props) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    fill="none"
    viewBox="0 0 24 24"
    strokeWidth="1.5"
    stroke="currentColor"
    aria-hidden="true"
    {...props}
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      d="M18.375 12.739l-7.693 7.693a4.5 4.5 0 01-6.364-6.364l10.94-10.94A3 3 0 1119.5 7.372L8.552 18.32m.009-.01l-.01.01m5.699-9.941l-7.81 7.81a1.5 1.5 0 002.112 2.13"
    ></path>
  </svg>
);
const EmojiIcon: React.FC<IconProps> = (props) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    fill="none"
    viewBox="0 0 24 24"
    strokeWidth="1.5"
    stroke="currentColor"
    aria-hidden="true"
    {...props}
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      d="M15.182 15.182a4.5 4.5 0 01-6.364 0M21 12a9 9 0 11-18 0 9 9 0 0118 0zM9.75 9.75c0 .414-.168.75-.375.75S9 10.164 9 9.75 9.168 9 9.375 9s.375.336.375.75zm-.375 0h.008v.015h-.008V9.75zm5.625 0c0 .414-.168.75-.375.75s-.375-.336-.375-.75.168-.75.375-.75.375.336.375.75zm-.375 0h.008v.015h-.008V9.75z"
    ></path>
  </svg>
);
const MicrophoneIcon: React.FC<IconProps> = (props) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    fill="none"
    viewBox="0 0 24 24"
    strokeWidth="1.5"
    stroke="currentColor"
    aria-hidden="true"
    {...props}
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      d="M12 18.75a6 6 0 006-6v-1.5m-6 7.5a6 6 0 01-6-6v-1.5m6 7.5v3.75m-3.75 0h7.5M12 15.75a3 3 0 01-3-3V4.5a3 3 0 116 0v8.25a3 3 0 01-3 3z"
    ></path>
  </svg>
);
const SendIcon: React.FC<IconProps> = (props) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    fill="none"
    viewBox="0 0 24 24"
    strokeWidth="1.5"
    stroke="currentColor"
    aria-hidden="true"
    {...props}
  >
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      d="M6 12L3.269 3.126A59.768 59.768 0 0121.485 12 59.77 59.77 0 013.27 20.876L5.999 12zm0 0h7.5"
    ></path>
  </svg>
);
// --- 아이콘 컴포넌트 정의 끝 ---

// --- 채팅 UI 관련 타입 및 컴포넌트 정의 ---
interface User {
  name: string;
  avatarUrl: string;
  lastSeen?: string;
  isOnline?: boolean;
}
interface Message {
  id: string;
  text: string;
  time: string;
  sender: User;
  isSenderMe: boolean;
  role: 'user' | 'ai';
  data?: any[]; // 일정 등 부가 데이터
}

interface ChatHeaderProps {
  groupName?: string;
  groupAvatarUrl?: string;
  participant?: User;
  lastSeen?: string;
  onBack?: () => void;
  onSearch?: () => void;
  onMenuToggle?: () => void;
  isMenuOpen?: boolean;
}

const ChatHeader: React.FC<ChatHeaderProps> = ({
  groupName,
  groupAvatarUrl,
  participant,
  lastSeen,
  onBack,
  onSearch,
  onMenuToggle,
  isMenuOpen,
}) => {
  const displayName = groupName || participant?.name || "Chat";
  const avatarUrl =
    groupAvatarUrl || participant?.avatarUrl || "/icon/aigo-ai-logo.svg"; // AI 로고 기본값
  return (
    <div className="w-full min-h-[5.25rem] px-5 py-6 border-b !border-[#DFDFDF] bg-white">
      {" "}
      {/* 배경색 및 테두리 수정 */}
      <div className="w-full flex justify-center items-center">
        <div className="group mr-4 md:hidden">
          <button
            onClick={onBack}
            className="group flex justify-center items-center rounded-full outline-none focus:outline-none hover:bg-gray-100 focus:bg-gray-100 transition-all duration-200 w-7 h-7"
            title="Close conversation"
            aria-label="Close conversation"
          >
            <ChevronLeftIcon className="w-[1.25rem] h-[1.25rem] text-gray-400 group-hover:text-indigo-500" />
          </button>
        </div>
        <div className="flex grow items-center">
          {" "}
          {/* items-center 추가 */}
          <button className="mr-3 outline-none" aria-label="Profile avatar">
            {" "}
            {/* mr-5에서 mr-3으로 수정 */}
            <div
              className="w-[2.25rem] h-[2.25rem] rounded-full bg-cover bg-center border border-gray-200"
              style={{ backgroundImage: `url("${avatarUrl}")` }}
            ></div>{" "}
            {/* 테두리 추가 */}
          </button>
          <div className="flex flex-col">
            <p
              className="outline-none text-[16px] text-[#383838] font-semibold leading-tight tracking-tight mb-1 cursor-pointer"
              tabIndex={0}
            >
              {displayName}
            </p>{" "}
            {/* 스타일 조정 */}
            {lastSeen && (
              <p
                className="outline-none text-[12px] text-gray-500 leading-tight tracking-tight"
                tabIndex={0}
                aria-label={`Last seen ${lastSeen}`}
              >
                {lastSeen}
              </p>
            )}{" "}
            {/* 스타일 조정 */}
          </div>
        </div>
        <div className="flex">
          <button
            onClick={onSearch}
            className="group flex justify-center items-center rounded-full outline-none focus:outline-none hover:bg-gray-100 focus:bg-gray-100 transition-all duration-200 w-7 h-7 mr-3"
            title="Search messages"
            aria-label="Search messages"
          >
            <SearchIcon className="w-[1.25rem] h-[1.25rem] text-gray-400 group-hover:text-indigo-500" />
          </button>
          <div className="relative">
            <button
              onClick={onMenuToggle}
              className="group flex justify-center items-center rounded-full outline-none focus:outline-none hover:bg-gray-100 focus:bg-gray-100 transition-all duration-200 w-7 h-7"
              id="open-conversation-menu"
              tabIndex={0}
              aria-expanded={isMenuOpen}
              aria-controls="conversation-menu"
              title="Toggle conversation menu"
              aria-label="Toggle conversation menu"
            >
              <DotsVerticalIcon className="w-[1.25rem] h-[1.25rem] text-gray-400 group-hover:text-indigo-500" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

interface ConversationMenuItemProps {
  label: string;
  icon: React.ReactNode;
  onClick?: () => void;
  isDestructive?: boolean;
}
const ConversationMenuItem: React.FC<ConversationMenuItemProps> = ({
  label,
  icon,
  onClick,
  isDestructive,
}) => (
  <button
    onClick={onClick}
    className={`w-full px-4 py-3 flex items-center border-b outline-none text-sm border-gray-200 dark:border-gray-600 transition-all duration-200 ${
      isDestructive
        ? "text-red-500 hover:bg-red-50 active:bg-red-100"
        : "text-gray-700 hover:bg-gray-50 active:bg-gray-100"
    }`}
    role="menuitem"
  >
    {icon} {label}
  </button>
);

interface ConversationMenuProps {}
const ConversationMenu: React.FC<ConversationMenuProps> = () => {
  const menuItems: ConversationMenuItemProps[] = [
    {
      label: "Profile Information",
      icon: <ProfileInformationIcon className="h-5 w-5 mr-3 text-gray-500" />,
      onClick: () => console.log("Profile Info clicked"),
    },
    {
      label: "Voice call",
      icon: <VoiceCallIcon className="h-5 w-5 mr-3 text-gray-500" />,
      onClick: () => console.log("Voice call clicked"),
    },
    {
      label: "Shared media",
      icon: <SharedMediaIcon className="h-5 w-5 mr-3 text-gray-500" />,
      onClick: () => console.log("Shared media clicked"),
    },
    {
      label: "Block contact",
      icon: <BlockContactIcon className="h-5 w-5 mr-3" />,
      onClick: () => console.log("Block contact clicked"),
      isDestructive: true,
    },
  ];
  return (
    <div
      className="right-0 absolute z-[100] w-[12.5rem] mt-2 rounded-md bg-white shadow-lg border border-gray-200 focus:outline-none"
      role="menu"
      aria-orientation="vertical"
      aria-labelledby="open-conversation-menu"
      tabIndex={-1}
    >
      <div role="none">
        {menuItems.map((item) => (
          <ConversationMenuItem key={item.label} {...item} />
        ))}
      </div>
    </div>
  );
};

interface MessageItemProps {
  message: Message;
}
const MessageItem: React.FC<MessageItemProps> = ({ message }) => {
  // 내가 보낸 메시지 스타일
  const myMessageBubbleClass =
    "bg-[#01274f] text-white rounded-l-xl rounded-br-xl"; // 요청하신 스타일
  const myMessageContainerClass = "justify-end pl-10"; // 메시지 전체 오른쪽 정렬, 왼쪽 여백

  // 상대방(AI)이 보낸 메시지 스타일
  const partnerMessageBubbleClass =
    "bg-[#DFDFDF] text-[#383838] rounded-r-xl rounded-bl-xl"; // 요청하신 스타일
  const partnerMessageContainerClass = "justify-start pr-10"; // 메시지 전체 왼쪽 정렬, 오른쪽 여백

  return (
    <div
      className={`select-none flex w-full mb-3 ${
        message.isSenderMe
          ? myMessageContainerClass
          : partnerMessageContainerClass
      }`}
    >
      {!message.isSenderMe && (
        <div className="mr-[10px] self-end shrink-0">
          <div aria-label={message.sender.name} className="outline-none">
            <div
              className="w-[36px] h-[36px] bg-cover bg-center rounded-full border border-gray-200"
              style={{
                backgroundImage: `url("${
                  message.sender.avatarUrl || "https://via.placeholder.com/150"
                }")`,
              }}
            ></div>
          </div>
        </div>
      )}
      <div
        className={`flex flex-col ${
          message.isSenderMe ? "items-end" : "items-start"
        }`}
      >
        {!message.isSenderMe && (
          <span className="text-xs text-gray-600 mb-1 ml-1">
            {message.sender.name}
          </span>
        )}
        <div
          className={`group max-w-xs md:max-w-md p-3 shadow-sm ${
            message.isSenderMe
              ? myMessageBubbleClass
              : partnerMessageBubbleClass
          }`}
        >
          <p
            className="text-sm font-normal leading-relaxed whitespace-pre-wrap"
            tabIndex={0}
          >
            {message.text}
          </p>
          {/* AI 메시지이면서 일정 데이터가 있을 때 표로 출력 */}
          {message.role === 'ai' && Array.isArray(message.data) && message.data.length > 0 && (
            <ul className="mt-2 text-xs text-gray-700">
              {message.data.map((schedule, idx) => (
                <li key={idx} className="mb-1">
                  <span className="font-semibold">{schedule.title}</span>
                  {schedule.startTime && (
                    <span> ({schedule.startTime} ~ {schedule.endTime})</span>
                  )}
                  {schedule.location && (
                    <span> @ {schedule.location}</span>
                  )}
                </li>
              ))}
            </ul>
          )}
          {/* 일정이 없을 때 안내 */}
          {message.role === 'ai' && Array.isArray(message.data) && message.data.length === 0 && (
            <div className="mt-2 text-xs text-gray-500">해당 날짜에 일정이 없습니다.</div>
          )}
        </div>
        <p
          className={`outline-none text-xs text-gray-400 font-light leading-4 tracking-tight mt-1 ${
            message.isSenderMe ? "mr-1" : "ml-1"
          }`}
        >
          {message.time}
        </p>
      </div>
      {message.isSenderMe && (
        <div className="ml-[10px] self-end shrink-0">
          {/* <img src="/assets/green_checks-6ffb68e3.svg" alt="read status" className="w-3.5 h-3.5" />  나중에 읽음 표시용 */}
        </div>
      )}
    </div>
  );
};

interface MessageInputProps {
  onSendMessage: (messageText: string) => void;
}
const MessageInput: React.FC<MessageInputProps> = ({ onSendMessage }) => {
  const [messageText, setMessageText] = useState("");
  const [isEmojiPickerOpen, setIsEmojiPickerOpen] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const adjustTextareaHeight = () => {
    if (textareaRef.current) {
      textareaRef.current.style.height = "auto";
      const scrollHeight = textareaRef.current.scrollHeight;
      const maxHeight = 160; // 10rem approx.
      textareaRef.current.style.height = `${Math.min(
        scrollHeight,
        maxHeight
      )}px`;
      if (scrollHeight > maxHeight) {
        textareaRef.current.style.overflowY = "auto";
      } else {
        textareaRef.current.style.overflowY = "hidden";
      }
    }
  };

  useEffect(() => {
    adjustTextareaHeight();
  }, [messageText]);

  const handleSubmit = () => {
    if (messageText.trim()) {
      onSendMessage(messageText.trim());
      setMessageText("");
    }
  };

  const handleKeyPress = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      handleSubmit();
    }
  };

  const handleInputChange = (event: React.ChangeEvent<HTMLTextAreaElement>) => {
    setMessageText(event.target.value);
  };

  return (
    <div className="w-full border-t !border-[#DFDFDF] bg-white p-[15px]">
      {" "}
      {/* 배경색, 테두리, 패딩 수정 */}
      <div className="flex items-end space-x-3">
        {" "}
        {/* space-x 로 간격 조정 */}
        <button
          className="p-2 text-gray-500 hover:text-indigo-600 focus:outline-none"
          title="Open select attachments modal"
          aria-label="Open select attachments modal"
          onClick={() => alert("Attach file clicked (implement modal)")}
        >
          <AttachmentIcon className="w-5 h-5" />
        </button>
        <div className="relative grow">
          <textarea
            ref={textareaRef}
            className="w-full px-4 py-2.5 rounded-lg content-center outline-none text-sm placeholder:text-[#949494] text-[#383838] bg-[#F9F9F9] border border-[#F0F0F0] focus:border-[#01274F] focus:ring-1 focus:ring-[#01274F] max-h-[10rem] pr-10 resize-none scrollbar-hidden transition-colors duration-200"
            value={messageText}
            onChange={handleInputChange}
            onKeyPress={handleKeyPress}
            rows={1}
            placeholder="무엇을 도와드릴까요?"
            aria-label="무엇을 도와드릴까요?"
            style={{ overflowY: "hidden" }} // 초기 스크롤바 숨김
          ></textarea>
          <div className="absolute bottom-2.5 right-2">
            {" "}
            {/* 위치 조정 */}
            <button
              onClick={() => setIsEmojiPickerOpen(!isEmojiPickerOpen)}
              className="p-1 text-gray-400 hover:text-indigo-500 focus:outline-none"
              title="Toggle emoji picker"
              aria-label="Toggle emoji picker"
            >
              <EmojiIcon className="w-5 h-5" />
            </button>
            {isEmojiPickerOpen && (
              <div className="absolute z-10 bottom-[calc(100%+0.5rem)] right-0 mt-2 bg-white shadow-lg rounded-md border border-gray-200 p-2">
                <p className="text-xs text-gray-500">
                  Emoji Picker Placeholder
                </p>
              </div>
            )}
          </div>
        </div>
        <button
          onClick={() => alert("Record audio clicked (not implemented)")}
          className="p-2 text-gray-500 hover:text-indigo-600 focus:outline-none"
          title="Start recording"
          aria-label="Start recording"
        >
          <MicrophoneIcon className="w-5 h-5" />
        </button>
        <button
          onClick={handleSubmit}
          className="p-2 bg-[#01274F] text-white rounded-lg hover:bg-opacity-90 focus:outline-none focus:ring-2 focus:ring-[#01274F] focus:ring-opacity-50 transition-colors duration-200 active:scale-95"
          title="Send message"
          aria-label="Send message"
        >
          <SendIcon className="w-5 h-5" />
        </button>
      </div>
    </div>
  );
};
// --- 채팅 UI 관련 타입 및 컴포넌트 정의 끝 ---

// --- ChatInterface 컴포넌트 (메인 채팅 UI 로직) ---
const ChatInterface = ({
                         initialKeyword,
                       }: {
  initialKeyword?: string | null;
}) => {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const messageAreaRef = useRef<HTMLDivElement>(null);
  const [inputValue, setInputValue] = useState("");

  // 🔧 핵심 수정사항: useRef를 사용하여 초기화 중복 실행 방지
  const initializedRef = useRef(false);

  const currentUser: User = {
    name: "나",
    avatarUrl: "https://via.placeholder.com/150/007BFF/FFFFFF?Text=ME",
  };
  const aiPartner: User = {
    name: "아이고 AI",
    avatarUrl: "/logo.png",
    lastSeen: "언제나 당신 곁에",
  };

  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(false);

  const addMessage = (
      text: string,
      sender: User,
      isSenderMe: boolean,
      role: 'user' | 'ai',
      data?: any[]
  ) => {
    const newMessage: Message = {
      id: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`, // 🔧 ID 생성 개선
      text: text,
      time: new Date().toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit",
        hour12: true,
      }),
      sender: sender,
      isSenderMe: isSenderMe,
      role: role,
      data: data,
    };
    setMessages((prevMessages) => [...prevMessages, newMessage]);
  };

  const handleSend = async (msg?: string) => {
    const message = typeof msg === "string" ? msg : inputValue;
    if (!message.trim()) return;
    addMessage(message, currentUser, true, 'user');
    setInputValue("");
    setLoading(true);
    try {
      const sessionId = "1";
      const aiResponse = await sendChatMessage(message, sessionId);

      // 1. function_call이 객체로 오면 정상 처리
      if (aiResponse.function_call) {
        const result = await handleAIFunction(aiResponse.function_call);
        addMessage(result.message, aiPartner, false, 'ai', result.data);
      } else {
        // 2. function_call이 텍스트(마크다운 등)로 오면 파싱 시도
        let aiMsg = aiResponse.response || aiResponse.message;
        if (typeof aiMsg === "string") {
          // 마크다운/기호 제거 및 JSON 파싱 시도
          const jsonMatch = aiMsg.match(/```json([\s\S]*?)```/);
          if (jsonMatch) {
            try {
              const jsonStr = jsonMatch[1].replace(/^\s*\|]\s*/, '').trim();
              const parsed = JSON.parse(jsonStr);
              if (parsed.function_call) {
                const result = await handleAIFunction(parsed.function_call);
                addMessage(result.message, aiPartner, false, 'ai', result.data);
                setLoading(false);
                return;
              }
            } catch (e) {
              // 파싱 실패시 무시
            }
          }
          // |] 등 이상한 기호 제거
          aiMsg = aiMsg.replace(/^\s*\|]\s*/, '').replace(/```json|```/g, '').trim();
        }
        addMessage(aiMsg, aiPartner, false, 'ai');
      }
    } catch (e) {
      addMessage("오류가 발생했습니다.", aiPartner, false, 'ai');
    }
    setLoading(false);
  };

  // 🔧 핵심 수정사항: useEffect 중복 실행 방지 및 로직 개선
  useEffect(() => {
    // 이미 초기화되었으면 중복 실행 방지
    if (initializedRef.current) {
      return;
    }

    const initializeChat = async () => {
      initializedRef.current = true; // 🔧 초기화됨으로 표시

      // 초기 인사말 추가
      addMessage(
          "안녕하세요! 아이고 AI입니다. 무엇을 도와드릴까요?",
          aiPartner,
          false,
          'ai'
      );

      // initialKeyword가 있을 때만 자동으로 메시지 전송
      if (initialKeyword && initialKeyword.trim()) {
        // 약간의 지연을 주어 자연스럽게 처리
        setTimeout(() => {
          handleSend(initialKeyword.trim());
        }, 500);
      }
    };

    initializeChat();
  }, []); // 🔧 의존성 배열을 빈 배열로 변경하여 한 번만 실행되도록 함

  // 스크롤 자동 이동
  useEffect(() => {
    if (messageAreaRef.current) {
      messageAreaRef.current.scrollTop = messageAreaRef.current.scrollHeight;
    }
  }, [messages]);

  return (
      <div
          id="chatInterface"
          className="flex flex-col grow h-full w-full bg-[#F9F9F9] overflow-hidden"
          role="region"
      >
        <ChatHeader
            participant={aiPartner}
            lastSeen={aiPartner.lastSeen}
            onBack={() => window.history.back()}
            onSearch={() => alert("Search clicked")}
            onMenuToggle={() => setIsMenuOpen(!isMenuOpen)}
            isMenuOpen={isMenuOpen}
        />
        <div className="relative z-10">
          {isMenuOpen && <ConversationMenu />}
        </div>
        <div
            ref={messageAreaRef}
            className="grow px-4 py-5 flex flex-col overflow-y-auto scrollbar-thin scrollbar-thumb-gray-300 scrollbar-track-gray-100 gap-y-4"
        >
          <div className="w-full h-full">
            {messages.map((msg) => (
                <MessageItem key={msg.id} message={msg} />
            ))}
            {loading && (
                <div className="text-left text-gray-400 px-2 py-1">AI가 답변 중...</div>
            )}
          </div>
        </div>
        <MessageInput onSendMessage={handleSend} />
      </div>
  );
};

// --- /chat 페이지의 실제 컨텐츠를 렌더링하는 컴포넌트 ---
function ChatPageContent() {
  const searchParams = useSearchParams();
  const keyword = searchParams.get("keyword");

  return <ChatInterface initialKeyword={keyword} />;
}

// --- /chat 페이지 기본 내보내기 ---
export default function ChatPage() {
  return (
      <Suspense
          fallback={
            <div className="flex justify-center items-center h-screen text-xl">
              채팅 로딩 중...
            </div>
          }
      >
        <div className="flex flex-col w-full h-full">
          <NavBarMain link="/mypage" />
          <ChatPageContent />
        </div>
      </Suspense>
  );
}