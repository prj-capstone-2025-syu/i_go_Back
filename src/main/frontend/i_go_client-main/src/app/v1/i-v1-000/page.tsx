import NavBarMain from "@/components/common/topNavMain";

export default function Home() {
  return (
    <div className="flex flex-col w-full h-full">
      <NavBarMain link="#"></NavBarMain>
      <div className="w-full max-h-full overflow-y-auto">
        <div className="flex flex-col items-center justify-start p-[20px] w-full h-auto"></div>
      </div>
    </div>
  );
}
