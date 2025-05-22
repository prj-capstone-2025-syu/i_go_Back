import NavBarMain from "@/components/common/topNavMain";

export default function Home() {
  return (
    <div className="flex flex-col w-full h-full">
      <NavBarMain link="setting"></NavBarMain>
      <div className="w-full max-h-full overflow-y-auto">
        <div className="flex flex-col items-center justify-start p-[20px] w-full h-auto">
          <p className="text-[20px] p-[20px] text-center">
            로그인, 회원가입 필수이기 때문에
            <br /> i-v1-005는 필요없는 화면임
          </p>
        </div>
      </div>
    </div>
  );
}
