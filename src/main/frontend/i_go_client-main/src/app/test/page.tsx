'use client';

import React, { useState, useEffect } from 'react';
import KakaoMapScript from '@/components/common/KakaoMapScript';
import AddressSearch from '@/components/common/AddressSearch';

export default function TestPage() {
  const [selectedAddress, setSelectedAddress] = useState<string | null>(null);
  const [coordinates, setCoordinates] = useState<{ x: number; y: number } | null>(null);
  const [apiKeyStatus, setApiKeyStatus] = useState<string>("확인 중...");
  const [sdkStatusDetails, setSdkStatusDetails] = useState<{[key: string]: boolean}>({
    kakao: false,
    maps: false,
    services: false,
    places: false,
    status: false,
  });

  const handleAddressSelect = (address: string, x: number, y: number) => {
    console.log('선택된 주소:', address, '좌표:', x, y);
    setSelectedAddress(address);
    setCoordinates({ x, y });
  };

  // API 키 확인
  const checkKakaoApiKey = () => {
    const kakaoApiKey = process.env.NEXT_PUBLIC_KAKAO_API_KEY;

    if (!kakaoApiKey) {
      alert("카카오 API 키가 로드되지 않았습니다! (process.env.NEXT_PUBLIC_KAKAO_API_KEY 없음)");
      setApiKeyStatus("❌ API 키 로드 실패");
      return;
    }

    alert(`카카오 API 키가 로드되었습니다: ${kakaoApiKey.substring(0, 4)}...${kakaoApiKey.substring(kakaoApiKey.length - 4)}`);
    setApiKeyStatus("✅ API 키 로드 성공");

    // 카카오맵 객체 상태 세부 확인
    const hasKakao = typeof window.kakao !== 'undefined';
    const hasMaps = hasKakao && typeof window.kakao.maps !== 'undefined';
    const hasServices = hasMaps && typeof window.kakao.maps.services !== 'undefined';
    const hasPlaces = hasServices && typeof window.kakao.maps.services.Places !== 'undefined';
    const hasStatus = hasServices && typeof window.kakao.maps.services.Status !== 'undefined';

    let message = "";
    if (hasKakao) message += "✅ window.kakao 존재\n";
    else message += "❌ window.kakao 없음\n";

    if (hasMaps) message += "✅ window.kakao.maps 존재\n";
    else message += "❌ window.kakao.maps 없음\n";

    if (hasServices) message += "✅ window.kakao.maps.services 존재\n";
    else message += "❌ window.kakao.maps.services 없음\n";

    if (hasPlaces) message += "✅ window.kakao.maps.services.Places 존재\n";
    else message += "❌ window.kakao.maps.services.Places 없음\n";

    if (hasStatus) message += "✅ window.kakao.maps.services.Status 존재\n";
    else message += "❌ window.kakao.maps.services.Status 없음\n";

    // 상태 업데이트
    setSdkStatusDetails({
      kakao: hasKakao,
      maps: hasMaps,
      services: hasServices,
      places: hasPlaces,
      status: hasStatus,
    });

    // 최종 알림 표시
    alert(message);
  };

  // 카카오 SDK 라이브러리 설정 확인
  const checkScriptUrl = () => {
    const scriptTags = document.querySelectorAll('script');
    let kakaoScriptUrl = "";

    scriptTags.forEach(script => {
      if (script.src && script.src.includes('dapi.kakao.com')) {
        kakaoScriptUrl = script.src;
      }
    });

    if (kakaoScriptUrl) {
      alert(`카카오맵 스크립트 URL: ${kakaoScriptUrl}\n\n이 URL에 'libraries=services' 파라미터가 포함되어 있는지 확인하세요.`);
    } else {
      alert("카카오맵 스크립트를 찾을 수 없습니다.");
    }
  };

  // SDK 상태 체크
  useEffect(() => {
    const checkKakaoSDK = () => {
      // 카카오맵 객체 상태 세부 확인
      const hasKakao = typeof window.kakao !== 'undefined';
      const hasMaps = hasKakao && typeof window.kakao.maps !== 'undefined';
      const hasServices = hasMaps && typeof window.kakao.maps.services !== 'undefined';
      const hasPlaces = hasServices && typeof window.kakao.maps.services.Places !== 'undefined';
      const hasStatus = hasServices && typeof window.kakao.maps.services.Status !== 'undefined';

      // 상태 업데이트
      setSdkStatusDetails({
        kakao: hasKakao,
        maps: hasMaps,
        services: hasServices,
        places: hasPlaces,
        status: hasStatus,
      });

      if (hasKakao) {
        if (hasServices && hasPlaces && hasStatus) {
          console.log("Kakao SDK 완전히 로드됨 (services, Places, Status 포함)");
          setApiKeyStatus("✅ SDK 완전히 로드됨");
        } else if (hasMaps) {
          console.log("Kakao SDK 부분 로드됨 (services 미포함)");
          setApiKeyStatus("⚠️ SDK 부분 로드됨");
        } else {
          console.log("Kakao SDK 부분 로드됨 (maps 미포함)");
          setApiKeyStatus("⚠️ SDK 부분 로드됨");
        }
      } else {
        console.log("Kakao SDK 로드되지 않음");
        setApiKeyStatus("❌ SDK 로드되지 않음");
      }
    };

    // 페이지 로드 후 1초 후 확인 (SDK 로드 시간 고려)
    const timer = setTimeout(checkKakaoSDK, 1000);

    return () => clearTimeout(timer);
  }, []);

  return (
    <div className="container mx-auto py-8 px-4">
      <h1 className="text-2xl font-bold mb-6">카카오맵 컴포넌트 테스트</h1>

      {/* KakaoMapScript 컴포넌트는 UI 렌더링 없이 SDK를 로드하는 역할을 함 */}
      <KakaoMapScript />

      {/* API 키 테스트 섹션 */}
      <section className="mt-4 p-4 border border-yellow-200 bg-yellow-50 rounded-md">
        <h2 className="text-xl font-semibold mb-2">카카오 API 키 테스트</h2>
        <p className="mb-3">현재 상태: <span className="font-medium">{apiKeyStatus}</span></p>

        <div className="flex flex-col sm:flex-row gap-3 mb-4">
          <button
            onClick={checkKakaoApiKey}
            className="px-4 py-2 bg-yellow-500 hover:bg-yellow-600 text-white rounded-md transition-colors"
          >
            API 키 및 SDK 객체 확인
          </button>

          <button
            onClick={checkScriptUrl}
            className="px-4 py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-md transition-colors"
          >
            스크립트 URL 확인
          </button>
        </div>

        <div className="p-3 bg-white rounded border border-gray-200">
          <h3 className="text-sm font-semibold mb-2">SDK 로드 상태 세부 정보</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2 text-sm">
            <div className={`py-1 px-2 rounded ${sdkStatusDetails.kakao ? 'bg-green-100' : 'bg-red-100'}`}>
              window.kakao: {sdkStatusDetails.kakao ? '✅' : '❌'}
            </div>
            <div className={`py-1 px-2 rounded ${sdkStatusDetails.maps ? 'bg-green-100' : 'bg-red-100'}`}>
              kakao.maps: {sdkStatusDetails.maps ? '✅' : '❌'}
            </div>
            <div className={`py-1 px-2 rounded ${sdkStatusDetails.services ? 'bg-green-100' : 'bg-red-100'}`}>
              maps.services: {sdkStatusDetails.services ? '✅' : '❌'}
            </div>
            <div className={`py-1 px-2 rounded ${sdkStatusDetails.places ? 'bg-green-100' : 'bg-red-100'}`}>
              services.Places: {sdkStatusDetails.places ? '✅' : '❌'}
            </div>
            <div className={`py-1 px-2 rounded ${sdkStatusDetails.status ? 'bg-green-100' : 'bg-red-100'}`}>
              services.Status: {sdkStatusDetails.status ? '✅' : '❌'}
            </div>
          </div>
        </div>
      </section>

      <section className="mt-8 p-4 border border-gray-200 rounded-md shadow-sm">
        <h2 className="text-xl font-semibold mb-4">주소 검색 컴포넌트 테스트</h2>
        <div className="mb-4">
          <AddressSearch
            onAddressSelect={handleAddressSelect}
            placeholder="주소를 검색해보세요"
            className="w-full p-2 border border-gray-300 rounded"
          />
        </div>

        {selectedAddress && coordinates && (
          <div className="mt-4 p-4 bg-gray-50 border border-gray-200 rounded-md">
            <h3 className="font-medium mb-2">검색 결과</h3>
            <p><span className="font-medium">선택된 주소:</span> {selectedAddress}</p>
            <p className="mt-1"><span className="font-medium">좌표:</span> X: {coordinates.x}, Y: {coordinates.y}</p>
          </div>
        )}
      </section>

      <div className="mt-8 p-4 bg-blue-50 border border-blue-200 rounded-md">
        <h3 className="text-lg font-medium mb-2">테스트 안내</h3>
        <ul className="list-disc list-inside space-y-2">
          <li>페이지 로드 후 개발자 콘솔에서 Kakao Maps SDK 로드 관련 로그를 확인해보세요.</li>
          <li><strong>API 키 및 SDK 객체 확인</strong> 버튼을 클릭하여 카카오맵 SDK의 세부 로드 상태를 확인할 수 있습니다.</li>
          <li><strong>스크립트 URL 확인</strong> 버튼을 클릭하여 카카오맵 스크립트의 정확한 URL을 확인할 수 있습니다.</li>
          <li>주소 검색 필드에 주소를 입력하고 검색 결과를 확인해보세요.</li>
          <li>검색된 주소를 선택하면 선택된 주소와 좌표 정보가 아래에 표시됩니다.</li>
          <li>카카오 API 키가 정상적으로 로드되고, SDK가 준비되면 주소 검색이 정상 작동합니다.</li>
        </ul>
      </div>
    </div>
  );
}

