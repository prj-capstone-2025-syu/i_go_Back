'use client'; // 클라이언트 컴포넌트 명시

import React, { useState, useEffect, useRef } from 'react';

interface AddressSearchProps {
  onAddressSelect: (address: string, x: number, y: number) => void;
  placeholder?: string;
  disabled?: boolean;
  defaultValue?: string;
  className?: string;
  name?: string;
}

declare global {
  interface Window {
    kakao: any;
  }
}

const AddressSearch: React.FC<AddressSearchProps> = ({
                                                       onAddressSelect,
                                                       placeholder = "주소를 입력해주세요",
                                                       disabled = false,
                                                       defaultValue = "",
                                                       className = "",
                                                       name = ""
                                                     }) => {
  const [keyword, setKeyword] = useState(defaultValue);
  const [results, setResults] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [showResults, setShowResults] = useState(false);
  const searchRef = useRef<HTMLDivElement>(null);
  const [sdkLoaded, setSdkLoaded] = useState(false);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (searchRef.current && !searchRef.current.contains(event.target as Node)) {
        setShowResults(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  useEffect(() => {
    const checkKakaoSDK = () => {
      if (
          window.kakao &&
          window.kakao.maps &&
          window.kakao.maps.services &&
          window.kakao.maps.services.Places && // Places 생성자 확인
          window.kakao.maps.services.Status // Status 객체 확인
      ) {
        setSdkLoaded(true);
        return true;
      }
      return false;
    };

    if (checkKakaoSDK()) {
      console.log('AddressSearch: Kakao Maps Places API가 마운트 시점에 이미 사용 가능합니다.');
      setSdkLoaded(true); // sdkLoaded를 true로 설정해야 합니다.
      return;
    }

    console.log('AddressSearch: Kakao Maps Places API가 즉시 사용 불가능합니다. 폴링을 시작합니다...');
    const interval = setInterval(() => {
      if (checkKakaoSDK()) {
        clearInterval(interval);
        console.log('AddressSearch: 폴링 후 Kakao Maps Places API 사용 가능.');
        // setSdkLoaded(true)는 checkKakaoSDK 내부에서 호출됩니다.
      } else {
        let missing = "AddressSearch Polling: ";
        if (!window.kakao) missing += "window.kakao 객체가 없습니다. ";
        else if (!window.kakao.maps) missing += "window.kakao.maps 객체가 없습니다. ";
        else if (!window.kakao.maps.services) missing += "window.kakao.maps.services 객체가 없습니다. ";
        else {
          if (!window.kakao.maps.services.Places) missing += "window.kakao.maps.services.Places 객체가 없습니다. ";
          if (!window.kakao.maps.services.Status) missing += "window.kakao.maps.services.Status 객체가 없습니다. ";
        }
        console.log(missing.trim());
      }
    }, 500); // 폴링 간격

    return () => {
      clearInterval(interval);
      console.log('AddressSearch: Kakao SDK 폴링 인터벌 정리됨.');
    };
  }, []);

  const searchAddress = (query: string) => {
    if (!query.trim() || disabled) return;

    if (!sdkLoaded) {
      console.warn("카카오맵 SDK가 아직 로드되지 않았습니다. (searchAddress 호출 시점)");
      // SDK가 로드되지 않았을 때 어떤 부분을 확인해야 하는지 추가 로그
      if (!window.kakao) console.warn("상세: window.kakao 없음");
      else if (!window.kakao.maps) console.warn("상세: window.kakao.maps 없음");
      else if (!window.kakao.maps.services) console.warn("상세: window.kakao.maps.services 없음");
      else if (!window.kakao.maps.services.Places) console.warn("상세: window.kakao.maps.services.Places 없음");
      else if (!window.kakao.maps.services.Status) console.warn("상세: window.kakao.maps.services.Status 없음");
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    try {
      const ps = new window.kakao.maps.services.Places();
      ps.keywordSearch(query, (data: any, status: any, pagination: any) => {
        if (status === window.kakao.maps.services.Status.OK) {
          setResults(data);
        } else if (status === window.kakao.maps.services.Status.ZERO_RESULT) {
          setResults([]);
          console.warn("검색 결과가 없습니다.");
        } else {
          setResults([]);
          console.error("카카오 주소 검색 오류:", status);
        }
        setIsLoading(false);
      });
    } catch (error) {
      console.error("카카오맵 API 호출 중 오류:", error);
      setIsLoading(false);
      setResults([]);
    }
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setKeyword(value);
    if (value.trim().length > 1) {
      setShowResults(true);
      searchAddress(value);
    } else {
      setResults([]);
      setShowResults(false);
    }
  };

  const handleSelectAddress = (item: any) => {
    const address = item.road_address_name || item.address_name;
    const x = parseFloat(item.x);
    const y = parseFloat(item.y);
    setKeyword(address);
    onAddressSelect(address, x, y);
    setShowResults(false);
  };

  return (
      <div ref={searchRef} className="relative w-full">
        <input
            type="text"
            name={name}
            value={keyword}
            onChange={handleInputChange}
            placeholder={placeholder}
            disabled={disabled}
            className={`${className}`}
        />
        {isLoading && (
            <div className="absolute right-3 top-1/2 transform -translate-y-1/2">
              {/* SVG 로더 */}
              <svg className="animate-spin h-4 w-4 text-gray-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
            </div>
        )}
        {showResults && results.length > 0 && (
            <div className="absolute z-50 w-full mt-1 bg-white border border-gray-300 rounded-md shadow-lg max-h-60 overflow-y-auto">
              <ul>
                {results.map((item, index) => (
                    <li
                        key={item.id || index}
                        className="px-4 py-2 hover:bg-gray-100 cursor-pointer border-b border-gray-100 text-left"
                        onClick={() => handleSelectAddress(item)}
                    >
                      <div className="text-[13px] font-medium text-[#383838]">{item.place_name}</div>
                      <div className="text-[11px] text-gray-500">
                        {item.road_address_name ? (
                            <>
                              <span className="text-blue-600">[도로명]</span> {item.road_address_name}
                              <br />
                              <span className="text-gray-600">[지번]</span> {item.address_name}
                            </>
                        ) : (
                            <span>{item.address_name}</span>
                        )}
                      </div>
                    </li>
                ))}
              </ul>
            </div>
        )}
        {showResults && keyword.trim().length > 1 && !isLoading && results.length === 0 && (
            <div className="absolute z-50 w-full mt-1 bg-white border border-gray-300 rounded-md shadow-lg">
              <div className="px-4 py-3 text-center text-gray-600 text-[12px]">
                검색 결과가 없습니다.
              </div>
            </div>
        )}
      </div>
  );
};

export default AddressSearch;