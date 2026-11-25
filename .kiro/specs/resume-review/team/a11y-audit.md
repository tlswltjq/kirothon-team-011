# 접근성·QA 감사

- 대상: `/home/ubuntu/workspaces/kirothon/src/main/resources/static/`의 정적 HTML·CSS·JavaScript
- 방법: 색상 토큰과 실제 선언을 추출해 WCAG 상대 휘도 공식으로 대비비를 계산하고, DOM·이벤트 코드를 정적 점검했다.
- 범위 한계: 화면 낭독기, 실제 키보드, 모바일 실기기에서의 동작은 실행하지 않았다. 아래 `추정` 표시는 실제 브라우저 확인이 필요하다.

## 색상 대비 계산 결과

WCAG 2.1 상대 휘도 공식 `(L1 + 0.05) / (L2 + 0.05)`으로 계산했다. 일반 텍스트 기준은 4.5:1, UI 경계·아이콘 등 비문자 기준은 3:1이다.

|화면·선택자|전경 / 배경|대비비|판정|
|---|---:|---:|---|
|공통 기본 본문 `body`|`#0B0B12` / `#F6F4FB`|18.75:1|통과|
|공통 muted `.muted`, `.page-label`|`#6B6880` / `#F6F4FB`|4.91:1|통과|
|공통 muted, 흰 카드|`#6B6880` / `#FFFFFF`|5.36:1|통과|
|보라 기본 버튼·평가 점수 선택 `.btn`, `.score label:has(input:checked)`|`#FFFFFF` / `#7C3AED`|5.70:1|통과|
|진보라 hover 버튼|`#FFFFFF` / `#5B21B6`|8.98:1|통과|
|보조 버튼 링크|`#7C3AED` / `#FFFFFF`|5.70:1|통과|
|오류 배너 `.msg.error`|`#B91C1C` / `#FDECEC`|5.66:1|통과|
|성공 배너 `.msg.ok`|`#15803D` / `#E9F7EE`|4.54:1|통과, 여유가 작음|
|경고 배너 `.msg.warn`|`#B45309` / `#FEF3E2`|4.58:1|통과, 여유가 작음|
|검정 히어로·게시 바의 흰 본문|`#FFFFFF` / `#0B0B12`|19.61:1|통과|
|검정 히어로 문제문·내 이력서 메타|`#A78BFA` / `#0B0B12`|7.21:1|통과|
|비활성 게시 버튼 `#publish-btn:disabled`|`#A78BFA` / `#1A1A26`|6.33:1|통과|
|평가자 색상 배지·내 이력서 핀 `PALETTE`|`#FFFFFF` / `#7C3AED`, `#1D4ED8`, `#15803D`, `#B45309`, `#BE185D`, `#0E7490`, `#B91C1C`, `#5B21B6`|5.02:1~8.98:1|통과|
|평가 화면 기본 핀 `.pin`|`#FFFFFF` / `#E8590C`|**3.58:1**|**실패**|
|업로드 마스킹 수량 0 `.count-chip.zero`|합성 `#919194` / `#F7F5FF` (`opacity: .45`)|**2.91:1**|**실패**|
|입력·점수 선택·카드 테두리 `--line`|`#E4E0F0` / `#FFFFFF`|**1.29:1**|**비문자 대비 실패 가능성**|
|차트 격자 `.my-grid`|`#1A1A26` / `#0B0B12`|**1.14:1**|추정: 보조 격자라면 비필수 정보로 예외, 값 판독에는 사용하지 말 것|
|비활성 일반 버튼 `button:disabled`|합성 흰 글자 / 보라 계열 배경 약 2.05:1~2.25:1|**4.5:1 미만**|비활성 UI는 WCAG 1.4.3 예외|

### 4.5:1 미만 색상 쌍의 정확한 위치

|심각도|파일:줄|화면·선택자|문제|구체적 수정 제안|
|---|---|---|---|---|
|중간|`/home/ubuntu/workspaces/kirothon/src/main/resources/static/css/app.css:61-64`|평가하기, 기본 `.pin`|흰 숫자 `#FFFFFF`와 기본 주황 핀 `#E8590C`의 대비가 3.58:1이다. 16px 굵은 글자도 일반 텍스트로 4.5:1이 필요하다.|핀 배경을 `#C2410C`로 바꾸면 흰색 대비가 5:1 이상이다. 또는 핀 숫자와 배경을 대비 4.5:1 이상 조합으로 함께 지정한다.|
|중간|`/home/ubuntu/workspaces/kirothon/src/main/resources/static/css/upload.css:10`|올리기, `.count-chip.zero`|요소 전체 `opacity: .45`가 검정 글자와 연보라 배경까지 흐리게 만들어 합성 대비가 2.91:1이다. `이메일 0` 등 정보성 수량을 읽기 어렵다.|`opacity: 1; color: #6B6880; background: #EDE9FE;`로 바꾼다. 이 조합은 4.52:1이다. 더 강하게 구분하려면 `color: #5B21B6`을 사용한다(7.57:1).|
|중간|`/home/ubuntu/workspaces/kirothon/src/main/resources/static/css/app.css:4,27-28,68` 및 `/home/ubuntu/workspaces/kirothon/src/main/resources/static/css/review.css:16-17`|올리기·평가하기·내 이력서, 입력·점수 선택·핀 목록 경계|`--line: #E4E0F0`가 흰 배경에서 1.29:1이다. 입력·라디오 점수 선택의 경계가 UI 식별에 필요한 유일한 시각 단서라면 WCAG 1.4.11의 3:1을 충족하지 못한다.|`--line: #8A849B`로 교체한다. 흰색 대비는 3.59:1, 종이 배경 대비는 3.29:1이다. 카드 장식 테두리만은 비필수일 수 있으나, 입력·점수 선택에는 이 색을 적용한다.|
|낮음|`/home/ubuntu/workspaces/kirothon/src/main/resources/static/css/my.css:45`|내 이력서, SVG 점수 추이 격자|`#1A1A26` 격자와 `#0B0B12` 배경은 1.14:1이다. 값·선·점이 충분히 구분되므로 보조 격자만이라면 예외일 수 있다(추정).|격자 자체가 점수 판독에 필요하면 `stroke: #6B6880` 이상으로 올려 3:1 이상을 확보한다. 그렇지 않으면 격자를 제거해 저대비 장식을 줄인다.|
|정보|`/home/ubuntu/workspaces/kirothon/src/main/resources/static/css/app.css:35`|업로드·평가 제출 중 일반 비활성 버튼|`opacity: .5` 적용 후 약 2.05:1~2.25:1이나 disabled 상태는 WCAG 1.4.3 예외다.|대기 상태도 더 읽기 쉽게 하려면 opacity를 제거하고 `background: #5B21B6; color: #FFFFFF;`을 쓴다. 게시 버튼은 이미 별도 규칙으로 통과한다.|

## 기능별 접근성·QA 결과

|심각도|파일:줄|점검 결과|수정 제안|
|---|---|---|---|
|높음|`/home/ubuntu/workspaces/kirothon/src/main/resources/static/review.html:37-39`, `/home/ubuntu/workspaces/kirothon/src/main/resources/static/js/review.js:134-142`|평가 핵심 동작인 핀 추가가 `div.page-wrap`의 마우스 클릭 좌표에만 연결되어 있다. `tabindex`, 키보드 이벤트, 대체 위치 입력이 없어 키보드 사용자와 화면 낭독기 사용자는 핀을 추가할 수 없다.|페이지마다 이름 있는 `핀 추가` 버튼을 제공하고, 버튼 뒤에 X/Y(0~100%) 입력 또는 방향키로 1%씩 이동 가능한 핀 편집기를 둔다. 이미지 클릭은 보조 입력으로 유지한다. 새 핀을 만들면 `aria-live`로 `1쪽, 42%, 30%에 핀 1 추가됨`을 알리고 코멘트 입력으로 포커스한다.|
|중간|`/home/ubuntu/workspaces/kirothon/src/main/resources/static/review.html:15`, `/home/ubuntu/workspaces/kirothon/src/main/resources/static/js/review.js:13-21,157-160,239-245`|평가 화면의 `#status`, `#pin-error`, `#summary-error`, `#score-error`, `#form-error`는 동적으로 바뀌지만 `role="status"`, `role="alert"`, `aria-live`가 없다. 로딩 실패, 핀 50개 초과, 유효성 오류가 화면 낭독기에 공지되지 않는다.|일반 진행·성공 영역에는 `role="status" aria-live="polite"`, 제출 실패·유효성 오류에는 `role="alert"`를 부여한다. 오류를 여러 곳에 중복 공지하지 않도록 폼 최상단 `#form-error` 한 곳에서 요약한다.|
|중간|`/home/ubuntu/workspaces/kirothon/src/main/resources/static/upload.html:34,40,42,51,63`, `/home/ubuntu/workspaces/kirothon/src/main/resources/static/js/upload.js:65-83,233-271`|업로드의 `#role-msg`, `#file-msg`, `#upload-loading`, `#warnings`, `#unmatched`는 비동기 변경되지만 라이브 영역이 아니다. `#top-msg`, `#term-msg`, `#publish-msg`만 각각 status/alert이다.|필드 오류 컨테이너에는 `role="alert"`, 처리 중 문구에는 `role="status" aria-live="polite"`, 경고 목록에는 `role="status"`를 지정한다. `aria-busy="true"`를 업로드 폼 또는 미리보기 카드에 처리 중 설정한다.|
|중간|`/home/ubuntu/workspaces/kirothon/src/main/resources/static/js/review.js:201-205`|핀 삭제 버튼을 누르면 `renderPins()`가 현재 포커스 요소를 제거한 뒤 다음 포커스를 정하지 않는다. 키보드 포커스가 문서 시작 또는 body로 사라질 수 있다.|삭제 전 인덱스를 보관하고, 삭제 후 같은 위치의 다음 핀 코멘트 입력, 이전 핀 코멘트 입력, 마지막으로 `#pin-empty` 또는 핀 추가 버튼 순으로 포커스를 명시적으로 이동한다. 상태 메시지도 공지한다.|
|낮음|`/home/ubuntu/workspaces/kirothon/src/main/resources/static/js/my.js:247-255`|`scrollIntoView({ behavior: 'smooth' })`를 무조건 실행하며 CSS/JS 어디에도 `prefers-reduced-motion` 처리가 없다.|`matchMedia('(prefers-reduced-motion: reduce)').matches`이면 `behavior: 'auto'`를 사용한다. 장차 transition/animation을 추가하면 CSS에도 `@media (prefers-reduced-motion: reduce) { *, *::before, *::after { animation-duration: .01ms; transition-duration: .01ms; } }`를 둔다.|
|낮음|`/home/ubuntu/workspaces/kirothon/src/main/resources/static/js/my.js:317-328`, `/home/ubuntu/workspaces/kirothon/src/main/resources/static/css/my.css:20`|내 이력서 핀은 버튼·라벨을 갖추고 있으나, 활성 핀의 검정 outline은 이력서 이미지의 검정 영역에서 보이지 않을 수 있다(추정).|`.my-pin.my-pin-active`에 `outline: 3px solid #FFD43B; outline-offset: 2px; box-shadow: 0 0 0 5px #0B0B12;`처럼 이중 윤곽을 둔다. 이미지 위에서도 명확히 구분되는지 수동 확인한다.|
|낮음|`/home/ubuntu/workspaces/kirothon/src/main/resources/static/js/review.js:185-193,201-220`|핀 버튼 라벨이 `핀 1`뿐이고, 삭제 버튼은 `삭제`뿐이다. 화면 낭독기 탐색에서 페이지·위치·대상 코멘트를 구별하기 어렵다.|핀 라벨을 `1쪽 42%, 30%의 핀 1, 코멘트 편집`처럼 만들고, 삭제 라벨을 `1쪽 핀 1 삭제`로 만든다. 이미 마스킹 단어 삭제는 `/home/ubuntu/workspaces/kirothon/src/main/resources/static/js/upload.js:154`에서 대상 단어를 포함해 더 낫다.|

## 통과하거나 유지할 항목

- **포커스 표시:** 공통 `:focus-visible`이 2px 보라 윤곽을 제공한다(`/home/ubuntu/workspaces/kirothon/src/main/resources/static/css/app.css:33`). 흰색·종이 배경에서 `#7C3AED` 대비는 각각 5.70:1, 5.22:1이고, 어두운 게시 바의 `#A78BFA` 윤곽도 6.33:1 이상이다. 점수 라디오도 별도 포커스 규칙이 있다(`/home/ubuntu/workspaces/kirothon/src/main/resources/static/css/review.css:19`).
- **라벨과 버튼명:** 업로드의 지원 직무·제목·PDF는 연결된 `label`을 쓴다(`/home/ubuntu/workspaces/kirothon/src/main/resources/static/upload.html:32-39`). 총평도 연결되어 있고, 총점은 `radiogroup`과 `aria-labelledby`로 이름을 얻는다(`/home/ubuntu/workspaces/kirothon/src/main/resources/static/review.html:51-62`). 정적 주요 버튼의 문구는 목적을 설명한다.
- **이미지 대체 텍스트:** 히어로 일러스트에 의미 있는 `alt`가 있다(`/home/ubuntu/workspaces/kirothon/src/main/resources/static/index.html:24`). 런타임 마스킹 이미지도 페이지별 alt를 넣는다(`/home/ubuntu/workspaces/kirothon/src/main/resources/static/js/api.js:104-118`; 호출부는 `review.js:148`, `upload.js:132` 및 `my.js:305`). 점수 차트 SVG는 `role="img"`와 레이블을 갖는다(`/home/ubuntu/workspaces/kirothon/src/main/resources/static/js/my.js:127`).
- **배너 역할:** 평가 완료 배너는 `role="status"`이고(`/home/ubuntu/workspaces/kirothon/src/main/resources/static/js/api.js:124-130`), 게시 실패 메시지는 `role="alert"`다(`/home/ubuntu/workspaces/kirothon/src/main/resources/static/upload.html:77`). 위 표의 누락한 동적 메시지 영역을 같은 원칙으로 보완해야 한다.
- **탭 순서:** 정적 DOM 기준으로 내비게이션 → 화면 주 콘텐츠 → 동적으로 생성된 핀 → 코멘트/총점/제출 순서다. 기존 핀은 실제 `button`으로 생성되어 Tab/Enter/Space로 선택되고(`/home/ubuntu/workspaces/kirothon/src/main/resources/static/js/review.js:185-195`), 삭제도 button이다. 다만 핀 **추가**는 앞서 지적한 대로 키보드 경로가 없다.
- **터치 대상:** 기본 버튼·링크·입력은 최소 높이 44px이며(`/home/ubuntu/workspaces/kirothon/src/main/resources/static/css/app.css:15-18,27-31`), 점수 선택은 48px, 제출 버튼은 48px이다(`/home/ubuntu/workspaces/kirothon/src/main/resources/static/css/review.css:16,35`). 핀의 시각 크기는 24~36px이지만 `::after`가 44px 클릭 영역을 만든다(`/home/ubuntu/workspaces/kirothon/src/main/resources/static/css/app.css:61-65`, `/home/ubuntu/workspaces/kirothon/src/main/resources/static/css/my.css:18-19`). 실제 모바일 브라우저에서 의사 요소 바깥 영역도 일관되게 눌리는지는 수동 확인이 필요하다(추정).
- **다크 히어로 가독성:** 검정 배경 `#0B0B12`에서 제목·본문 흰색은 19.61:1, 안내·재방문 링크 `#A78BFA`는 7.21:1이다(`/home/ubuntu/workspaces/kirothon/src/main/resources/static/css/landing.css:2,7-23`). 보라 CTA의 흰색도 5.70:1이므로 현재 텍스트 대비는 충분하다.

## 권장 재검증 순서

1. 키보드만 사용해 평가 화면에서 핀 추가, 코멘트 입력, 핀 선택·삭제, 총점 선택, 제출까지 완주한다.
2. NVDA+Firefox 또는 VoiceOver+Safari에서 업로드 실패, 미리보기 처리 중, 평가 제출 오류·완료, 핀 추가·삭제 공지를 확인한다.
3. 320px 및 375px 폭에서 핀과 모든 버튼의 실제 44×44px 터치 영역, sticky 제출 바에 가려지는 포커스 대상 여부를 확인한다.
4. `prefers-reduced-motion: reduce`를 켠 상태에서 내 이력서 핀 선택 시 즉시 이동하는지 확인한다.
5. 수정 후 색상 표의 실패 두 항목과 UI 경계 3:1을 자동 대비 검사 및 수동 화면으로 다시 확인한다.
