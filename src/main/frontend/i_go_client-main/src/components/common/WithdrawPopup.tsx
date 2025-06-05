import React from "react";

interface WithdrawPopupProps {
  isOpen: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

const WithdrawPopup: React.FC<WithdrawPopupProps> = ({
  isOpen,
  onConfirm,
  onCancel,
}) => {
  if (!isOpen) return null;

  return (
    <>
      <div
        className="fixed z-[99] w-full h-full bg-[rgba(217,217,217,0.85)]"
        onClick={onCancel}
      ></div>
      <div
        className="fixed left-[50%] top-[55%] translate-x-[-50%] translate-y-[-50%] w-[85%] max-w-[335px] bg-[#fff] p-[20px] pb-[10px] z-[100] rounded-[8px] shadow-md"
      >
        <p className="text-[18px] text-[#5C5C5C] font-[400] text-center">
          <span className="font-[700]">회원탈퇴</span> 하시겠습니까?
        </p>
        <div className="mt-[10px] w-full h-[1px] bg-[#dfdfdf]"></div>
        <div className="w-full grid grid-cols-2">
          <button
            onClick={onConfirm}
            className="text-[#ff2f01] text-[16px] font-[500] tracking-[-0.8px] leading-[155%] line-clamp-1 text-center py-[10px] hover:opacity-[0.7]"
          >
            예
          </button>
          <button
            onClick={onCancel}
            className="text-[#383838] text-[16px] font-[500] tracking-[-0.8px] leading-[155%] line-clamp-1 text-center py-[10px] hover:opacity-[0.7]"
          >
            아니요
          </button>
        </div>
      </div>
    </>
  );
};

export default WithdrawPopup;
