import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

export function middleware(request: NextRequest) {
  // 쿠키에서 토큰 확인
  const hasToken = request.cookies.has('access_token')

  // 현재 경로가 /greeting이 아니고 토큰이 없는 경우
  if (!hasToken && !request.nextUrl.pathname.startsWith('/greeting')) {
    // 현재 URL 저장 (나중에 로그인 후 돌아오기 위함)
    const url = request.nextUrl.clone()
    url.pathname = '/greeting'

    // 현재 경로를 쿼리 파라미터로 저장 (선택사항)
    if (request.nextUrl.pathname !== '/') {
      url.searchParams.set('redirect', request.nextUrl.pathname)
    }

    return NextResponse.redirect(url)
  }

  return NextResponse.next()
}

// 미들웨어가 작동할 경로 설정
// 제외할 경로: API 경로, Next.js 정적 파일, 이미지, 아이콘 등 정적 자산, greeting 페이지
export const config = {
  matcher: ['/((?!api|_next/static|_next/image|favicon.ico|icon|logo|images|assets|greeting).*)']
}
