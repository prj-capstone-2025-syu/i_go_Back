import React from "react";

interface ConfirmPopupProps {
  isOpen: boolean;
  message: string;
  onConfirm: () => void;
  onCancel: () => void;
  confirmText?: string;
  cancelText?: string;
}

const ConfirmPopup: React.FC<ConfirmPopupProps> = ({
  isOpen,
  message,
  onConfirm,
  onCancel,
  confirmText = "예",
  cancelText = "아니오",
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
          {message}
        </p>
        <div className="mt-[10px] w-full h-[1px] bg-[#dfdfdf]"></div>
        <div className="w-full grid grid-cols-2">
          <button
            onClick={onConfirm}
            className="text-[#ff2f01] text-[16px] font-[500] tracking-[-0.8px] leading-[155%] line-clamp-1 text-center py-[10px] hover:opacity-[0.7]"
          >
            {confirmText}
          </button>
          <button
            onClick={onCancel}
            className="text-[#383838] text-[16px] font-[500] tracking-[-0.8px] leading-[155%] line-clamp-1 text-center py-[10px] hover:opacity-[0.7]"
          >
            {cancelText}
          </button>
        </div>
      </div>
    </>
  );
};

export default ConfirmPopup;
