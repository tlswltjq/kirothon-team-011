# 아트 디렉션: 디자인 비평과 CSS 수정 목록

- 대상: `artifacts/design/{mobile,desktop}-*.png` 10장, `src/main/resources/static/css/*.css`, `*.html`
- 제약: 외부 폰트·CDN·인라인 style 금지, id·클래스·선택자 변경 금지(HTML·JS는 그대로 두고 CSS만 고친다), 전체 예산 60분
- 예산 배분: P0 20분, P1 30분, P2 10분. "추가" 항목은 남는 시간에만 한다

## 1. 총평

색은 맞게 골랐지만 화면이 "보라색 부트스트랩"으로 보인다. 정체성 모티브 두 개 중 '검은 가림 막대'는 랜딩 그림과 게시 완료 카드에만 있고, '보라 핀'은 정작 평가 화면에서 주황색(`#e8590c`)으로 나온다. 데모의 핵심 두 장면(평가자가 핀을 꽂는 장면, 주인이 점수 추이를 보는 장면)에 눈에 띄는 결함이 있다.

데모 인상을 깎는 결함(모두 CSS로 고칠 수 있다):

1. 내 이력서 추이 카드에서 `v1 4.00` 옆에 빈 회색 알약이 있고, 평가가 있는데도 "기다리는 동안 평가하기" 버튼이 보인다. 원인: `.my-delta { display: inline-block }`과 `.btn { display: inline-flex }`가 `hidden` 속성을 덮어쓴다. JS(`my.js` 162~163행)는 숨기라고 지시하고 있다.
2. 평가 화면 핀이 세로로 찌그러진 타원이다. 원인: 핀이 `<button>`이라 전역 `button { min-height: 44px }`를 받아 높이 28~36px 지정이 무시된다. 터치 영역은 이미 `.pin::after` 44px로 확보되어 있다.
3. 핀 색이 화면마다 다르다: 랜딩 그림 보라, 평가 화면 주황, 선택 테두리 노랑(`#ffd43b`), 내 이력서 보라·파랑·초록(평가자별).
4. 한글 굵기가 거의 안 보인다. `font-family` 첫 항목 `"Noto Sans KR"`은 이 호스트에 없다(`fc-list` 결과: `Noto Sans CJK KR` Regular·Bold만 있음). 그래서 system-ui로 떨어지고, 제목·버튼 강조가 본문과 구분되지 않는다.
5. 한글 단어가 줄 끝에서 쪼개진다("피 / 드백", "사람들 / 의", "자 / 동으로"). `word-break: keep-all`이 없어서다.
6. 총점 라디오가 OS 기본 파란 점으로 나와 보라 버튼 위에서 튄다.
7. 모바일 게시 바가 화면 높이의 약 35%(약 230px)를 덮어 "+ 지원" 칩과 미리보기를 가린다. 확인해야 할 이미지를 확인 버튼이 가리는 구조다.

## 2. 시각 언어 규칙 (이번 수정의 기준)

| 요소 | 역할 | 규칙 |
|---|---|---|
| 검정 `--ink` | 가림·무대 | 가려진 것과 평가 대상 이미지를 올리는 무대에만 쓴다 |
| 보라 `--purple` | 행동·핀 | 주 버튼, 현재 상태, 모든 핀. 핀 기본색은 보라 하나 |
| 흰색 `--white`·`--paper` | 종이 | 카드와 이력서 페이지 |
| 초록·빨강·주황 | 상태 문구 | 성공·오류·경고 문구에만 쓴다. 장식과 핀에는 쓰지 않는다 |

**가림 막대 규칙**: 바탕과 반대색의 직각 사각형, 모서리 2px, 높이 약 0.9em. 밝은 바탕에서는 검정 막대, 검은 바탕(히어로·내비·무대)에서는 흰 막대(반전)다. 둥근 알약(999px)은 칩·핀에만 쓰고 막대에는 쓰지 않는다.

**모서리 반지름**: 막대 2px, 입력·버튼 8px, 카드 12px, 칩·핀 999px. 지금은 4·6·8·10·12·999가 섞여 있다(P2, 예산 밖이면 생략).

**타이포**: 글자 크기 16px 하한은 유지한다(대부분 16px로 고정된 걸 보아 접근성 기준으로 보인다). 위계는 크기를 줄이지 않고 제목 크기·굵기·자간을 올려서 만든다. 설치된 굵기는 400과 700뿐이므로 600·800은 쓰지 않는다.

## 3. 공통 (`css/app.css`)

| 우선 | 지금 | 바꿀 것 | 시간 |
|---|---|---|---|
| P0 | `[hidden]` 요소가 `.btn`·`.my-delta`의 `display`에 덮여 보인다 | 파일 맨 아래에 `[hidden] { display: none !important; }` | 2분 |
| P0 | 한글이 음절 단위로 줄바꿈된다 | `body`에 `word-break: keep-all; overflow-wrap: break-word;` | 1분 |
| P0 | `font-family: "Noto Sans KR", ...`이 없는 글꼴을 가리켜 굵기가 안 보인다 | `font-family: "Noto Sans KR", "Noto Sans CJK KR", "Apple SD Gothic Neo", "Malgun Gothic", system-ui, sans-serif;` (로컬 설치 글꼴이라 외부 폰트 금지에 걸리지 않는다). `h1, h2 { font-weight: 700; letter-spacing: -0.02em; }` `h1 { font-size: 26px; line-height: 1.3; }`, 640px 이상에서 `h1 { font-size: 30px; }`. 컨테이너에는 fonts-noto-cjk가 들어가므로 같은 이름이 잡힌다 | 4분 |
| P1 | 버튼 상태 변화가 즉시 바뀌고 누를 때 반응이 없다 | 아래 "마이크로인터랙션" 블록 추가 | 7분 |
| P2 | 브랜드 표시가 보라 점 하나라 모티브가 없다 | `header.nav a.brand::before { content: ""; flex: none; width: 18px; height: 8px; margin-right: 5px; background: var(--white); border-radius: 2px; }` → "흰 막대 + 보라 핀" 로고(검은 내비에서 반전 막대). `a.brand`가 이미 `display: flex`라 바로 붙는다 | 3분 |

### 마이크로인터랙션 블록 (P1, `app.css` 맨 아래)

```css
button, .btn { transition: background-color .15s ease, border-color .15s ease, color .15s ease, transform .08s ease; }
button:active:not(:disabled), .btn:active { transform: translateY(1px); }

/* 핀이 꽂힐 때: 새로 꽂은 핀은 active라서 그 핀만 튄다 */
@keyframes pin-pop {
  0%   { transform: translate(-50%, -50%) scale(.4); }
  70%  { transform: translate(-50%, -50%) scale(1.15); }
  100% { transform: translate(-50%, -50%) scale(1); }
}
.page-wrap .pin.active { animation: pin-pop .22s cubic-bezier(.3, 1.6, .5, 1) both; }

/* 업로드 처리 중: 검은 막대가 칠해지는 진행 표시 */
@keyframes paint-bar { from { transform: scaleX(.08); } to { transform: scaleX(1); } }
#upload-loading::before { content: ""; display: block; height: 10px; margin-bottom: 8px; background: var(--ink);
  border-radius: 2px; transform-origin: left; animation: paint-bar 1.2s ease-in-out infinite alternate; }

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration: .01ms !important; animation-delay: 0s !important;
    animation-iteration-count: 1 !important; transition-duration: .01ms !important; }
}
```

- 모션 감소 설정에서는 모든 애니메이션이 최종 상태로 바로 간다. `both`로 채운 애니메이션은 끝 상태, `paint-bar`는 기본값(꽉 찬 막대)으로 멈춘다.
- 확인할 것: `review.js`가 코멘트 입력마다 핀을 다시 그리면 active 핀이 타이핑할 때마다 튄다. 그렇다면 `pin-pop`을 지우고 버튼 반응만 남긴다.

## 4. 랜딩 (`index.html`, `css/landing.css`)

검은 히어로, 보라 빛, 가려진 이력서 그림은 첫인상으로 좋다. 그 아래가 약하다. 세 단계 카드는 흔한 템플릿 모양이고, 안심 문구 위 40×6px 검은 선은 구분선으로 읽혀 모티브로 보이지 않는다. 제목의 `.redact`는 둥근 모서리와 보라 테두리 때문에 입력칸이나 선택된 탭처럼 보인다.

| 우선 | 지금 | 바꿀 것 | 시간 |
|---|---|---|---|
| P1 | `.redact`: `--ink-2` 바탕 + `box-shadow: 0 0 0 2px var(--purple-glow)` + 4px 모서리 → 입력칸처럼 보인다 | 반전 막대로 바꾸고, 막대가 왼쪽에서 칠해지는 효과를 넣는다(아래 코드) | 5분 |
| P1 | `.step-no` 3개 모두 보라 원 | 1·2는 검은 막대 번호, 3만 보라 원(핀)으로 해서 "가리고 → 핀 꽂기" 순서를 그림으로 보여 준다(아래 코드) | 2분 |
| P1 | `.assure`: 회색 문장 + 위에 외톨이 검은 선 | 검은 띠 위 흰 글자로 바꾼다: `.landing .steps .assure { display: inline-block; max-width: 100%; padding: 10px 16px; background: var(--ink); color: var(--white); border-radius: 2px; }` `.landing .assure::before { content: none; }` | 2분 |
| P1 | 단계 제목 `h2` 18px이 본문과 굵기 차이가 없다 | 공통 P0 글꼴 수정으로 해결. 추가 작업 없음 | 0분 |
| 추가 | 버튼 hover가 색만 바뀐다 | `.landing .big-btn { transition: transform .15s ease, background-color .15s ease; }` `.landing .big-btn:hover { transform: translateY(-2px); }` | 1분 |

```css
.landing .redact { padding: 0 .12em; margin-right: .08em; border-radius: 2px; box-shadow: none; color: var(--ink);
  background: linear-gradient(var(--white), var(--white)) left center / 100% 100% no-repeat;
  -webkit-box-decoration-break: clone; box-decoration-break: clone;
  animation: redact-swipe .7s .3s cubic-bezier(.2, .7, .2, 1) both; }
@keyframes redact-swipe {
  from { background-size: 0% 100%; color: var(--white); }
  60%  { color: var(--white); }
  to   { background-size: 100% 100%; color: var(--ink); }
}

.landing .step-no { min-width: 44px; height: 24px; line-height: 24px; border-radius: 2px; background: var(--ink); }
.landing .steps li:nth-child(3) .step-no { min-width: 32px; height: 32px; line-height: 32px; border-radius: 50%; background: var(--purple); }
```

## 5. 평가하기 (`review.html`, `css/review.css`)

검은 무대 위 흰 페이지 구성은 좋다. 집중을 깨는 것은 찌그러진 주황 핀, 핀보다 큰 빨간 "삭제" 상자, 모바일에서 주 버튼만큼 큰 "이 이력서 건너뛰기", 파란 라디오다. 모바일 핀 카드에서 "삭제" 버튼이 폭의 2/3를 차지한다(`button { width: 100% }` 상속).

| 우선 | 지금 | 바꿀 것 | 시간 |
|---|---|---|---|
| P0 | 핀이 전역 `min-height: 44px`로 세로 타원, 주황 `#e8590c`, 선택 시 노란 테두리 | `app.css`: `.pin { min-height: 0; background: var(--pin-color, var(--purple)); }` `.pin:hover { background: var(--pin-color, var(--purple)); }` `.pin.active { outline: 3px solid var(--purple-glow); outline-offset: 1px; }`. 원이 되도록 `review.css`의 `.review-image .pin`에 `padding: 0 6px;` | 4분 |
| P0 | 라디오가 OS 파란 점 | `.score input { accent-color: var(--purple); }` `.score label:has(input:checked) input { accent-color: var(--white); }`. 입력은 숨기지 않는다(E2E가 라디오를 직접 누를 수 있으므로) | 2분 |
| P0 | 핀 카드의 "삭제": 빨간 테두리 상자, 모바일 폭 2/3 | 조용한 텍스트 버튼으로: `.pin-item .head button.danger { flex: none; width: auto; min-width: 44px; padding: 0 10px; background: transparent; border-color: transparent; color: var(--muted); }` `.pin-item .head button.danger:hover { background: #FDECEC; border-color: transparent; color: var(--err); }` | 3분 |
| P1 | `#other-btn` "이 이력서 건너뛰기"가 모바일에서 풀폭 보라 테두리 → 주 행동처럼 보인다 | `#other-btn { width: auto; align-self: flex-start; padding: 0 4px; border-color: transparent; text-decoration: underline; }` `#other-btn:hover { background: var(--purple-soft); color: var(--purple-dark); border-color: transparent; }` | 2분 |
| P2 | 선택한 점수 버튼이 색만 바뀐다 | `.score label { transition: transform .12s ease, background-color .12s ease; }` `.score label:has(input:checked) { transform: translateY(-2px); }` (모션 감소 블록이 적용됨) | 1분 |
| P1 | 무대 위 페이지 그림자가 흐린 검정이라 랜딩 그림과 다른 물건처럼 보인다 | `.review-image .page-wrap { box-shadow: 6px 6px 0 var(--purple-dark); }` → 랜딩 그림의 오프셋 그림자와 같은 "종이" (아래 7절 무대 통일과 함께) | 1분 |

## 6. 평가받기 (`upload.html`, `css/upload.css`)

흐름은 분명하지만 마스킹 화면에서 "가렸다"는 느낌이 약하다. 사용자가 지정한 가릴 단어와 자동으로 가린 개수가 보라 알약으로만 나와, 결과(검게 칠해짐)가 화면 언어로 표현되지 않는다. 미리보기 페이지만 검은 무대 없이 흰 카드에 놓여 평가 화면과 다르게 보인다. 게시 완료 카드 아래 장식 막대 두 줄은 절반쯤 찬 진행 막대처럼 보여 "아직 처리 중"으로 읽힌다.

| 우선 | 지금 | 바꿀 것 | 시간 |
|---|---|---|---|
| P0 | 모바일 게시 바 약 230px, 문단 정렬이 가운데·왼쪽으로 섞임(`.sticky-bar { align-items: center }`) | 639px 이하에서: `.publish-bar { align-items: stretch; text-align: left; padding: 10px 12px; gap: 6px; border-radius: 12px 12px 0 0; }` `.publish-bar p { margin: 2px 0; }` `#confirm-hint:empty { display: none; }` `.publish-bar .tiny { display: none; }`(24시간 자동 삭제 문구는 완료 화면과 랜딩에도 있어 모바일에서는 뺀다) | 4분 |
| P1 | `#terms .chip`: 보라 알약 | 가린 단어는 검은 막대로: `#terms .chip { background: var(--ink); color: var(--white); border-color: var(--ink); border-radius: 2px; }` `#terms .chip button { color: var(--purple-glow); }` `#terms .chip.nomatch { background: var(--warn-bg); color: var(--warn); border-color: #ECD28A; }` | 2분 |
| P1 | `.count-chip` "이메일 1" 등이 보라 알약뿐 | 앞에 작은 막대: `.count-chip::before { content: ""; display: inline-block; width: 14px; height: 8px; margin-right: 6px; vertical-align: middle; background: var(--ink); border-radius: 2px; }` `.count-chip.zero::before { background: var(--line); }` | 1분 |
| 추가 | 이름 후보 버튼 "+ 김하늘"을 눌러야 결과를 안다 | 누르기 전에 막대 미리보기: `#name-candidate button:hover { background: var(--ink); border-color: var(--ink); color: var(--white); }` | 1분 |
| P1 | `#done-card::after` 장식 막대 → 진행 막대로 오해 | `#done-card::after { content: none; }` `#done-card { padding-bottom: 18px; }`. 모티브는 검은 막대 제목 "v1을 게시했어요"가 이미 맡는다 | 1분 |
| 추가 | 모바일 단계 표시: "2 가린 결과 확인"이 두 줄이 되며 999px 알약이 부풀고 글자가 위로 붙는다 | `.steps li { display: flex; align-items: center; justify-content: center; line-height: 1.3; border-radius: 10px; }` | 2분 |
| 추가 | `.page-wrap.loading`: 연보라 빈 상자 | 가려진 이력서 뼈대: `.page-wrap.loading { background: linear-gradient(var(--ink), var(--ink)) 10% 10% / 35% 3% no-repeat, linear-gradient(var(--line), var(--line)) 10% 18% / 70% 2% no-repeat, linear-gradient(var(--line), var(--line)) 10% 24% / 60% 2% no-repeat, var(--white); }` | 3분 |

## 7. 내 이력서 (`my.html`, `css/my.css`)

데모 첫 30초의 결과 화면이므로 가장 중요하다. 지금은 같은 숫자가 네 번 나온다(큰 숫자 `v1 4.00`, "v1 평균 4.00점 · 평가 1개", 목록 "평가 1개", 버전 행 "평균 4.00점 평가 1개", 상세 제목). 같은 "고친 이력서 올리기 (v2)" 버튼도 두 번 나온다. 섹션 사이 간격이 8~16px로 붙어 있어 어디까지가 한 덩어리인지 보이지 않는다. 페이지 이미지는 6px 검은 테두리 액자로, 평가 화면의 검은 무대와 다른 물건처럼 보인다.

| 우선 | 지금 | 바꿀 것 | 시간 |
|---|---|---|---|
| P0 | 빈 회색 알약, 숨겨야 할 "기다리는 동안 평가하기" | 공통 P0 `[hidden]` 수정으로 해결 | 0분 |
| P1 | 추이 카드와 버전 목록에 업로드 버튼 2개 | 단일 버전 안내가 보일 때만 아래쪽 링크를 숨긴다: `main:has(#trend-card:not([hidden]) #trend-single:not([hidden])) #versions-section p:has(#new-version-link) { display: none; }` | 2분 |
| P2 | 섹션 간격이 붙어 있다 | `.my-main > section { margin-top: 28px; }` `.my-main > section#trend-card { margin-top: 0; }` `#trend-single-text { margin: 4px 0 0; color: var(--purple-glow); }` (중복 숫자를 보조 정보로 낮춘다) | 2분 |
| P1 | 페이지: `.my-page { border: 6px solid var(--ink-2) }` 액자 | 7절 아래 "무대 통일" 블록 적용 | 0분(아래에 포함) |
| P2 | 추이 선 2px, 최신 점이 작아 v1→v2 변화가 약하다 | `.my-line { stroke-width: 3; }` `.my-dot.latest { stroke-width: 4; }` `.my-val { font-size: 14px; }` | 2분 |
| P2 | 코멘트를 누르면 핀이 1.5배로 즉시 커진다 | `.my-pin { transition: transform .15s ease; }` + 링: `@keyframes pin-ring { from { box-shadow: 0 0 0 0 rgba(167, 139, 250, .9); } to { box-shadow: 0 0 0 14px rgba(167, 139, 250, 0); } }` `.my-pin.my-pin-active { animation: pin-ring .9s ease-out 2; }` | 2분 |

### 무대 통일 (P1, 5분): 마스킹 페이지는 세 화면 모두 "검은 무대 + 흰 종이 + 보라 오프셋 그림자"

```css
/* upload.css: 미리보기 */
#preview-card .pages { padding: 14px 10px; background: var(--ink); border-radius: 12px; }
#preview-card .pages .page-label { color: var(--purple-glow); }
#preview-card .page-wrap { border: 0; border-radius: 4px; box-shadow: 6px 6px 0 var(--purple-dark); }

/* my.css: 결과 */
.my-pages { padding: 14px; background: var(--ink); border-radius: 12px; }
.my-pages .my-pagelabel { color: var(--purple-glow); }
.my-page { border: 0; border-radius: 4px; background: var(--white); box-shadow: 6px 6px 0 var(--purple-dark); }
```

`.my-overlay`는 `inset: 0`이라 테두리를 없애도 핀 위치는 이미지 기준 그대로다.

## 8. 예산표

| 우선 | 항목 | 분 |
|---|---|---|
| P0 | `[hidden]`, keep-all, 글꼴 스택·제목 굵기 | 7 |
| P0 | 핀 모양·보라, 라디오 accent, 삭제 버튼 | 9 |
| P0 | 모바일 게시 바 압축 | 4 |
| P1 | 마이크로인터랙션 블록 + 모션 감소 | 7 |
| P1 | 랜딩 막대 제목, 단계 번호, 안심 띠 | 9 |
| P1 | 건너뛰기, 가린 단어 칩, 개수 칩 막대, 완료 카드 장식 제거 | 6 |
| P1 | 무대 통일(평가 화면 그림자 포함), 업로드 링크 중복 | 8 |
| P2 | 브랜드 막대, 섹션 간격, 핀 링, 점수 선택 반응, 차트 선 | 10 |
| | 합계 | 60 |

"추가"로 표시한 항목(랜딩 버튼 hover, 이름 후보 hover, 모바일 단계 표시, 로딩 뼈대, 약 7분)은 시간이 남을 때만 한다.

예산 밖(이번에 하지 않음):
- `my.js` 7행 평가자 색 팔레트에 초록·빨강·주황이 섞여 성공·오류 색과 겹친다. 보라·남색 계열로 바꾸려면 JS를 고쳐야 하므로 이번 범위가 아니다.
- 카드 그림자(`--shadow`의 보라 번짐)를 없애고 테두리만 남기기, 반지름 토큰 통일.

## 9. 수정 후 확인

- 같은 도구로 `artifacts/design/` 10장을 다시 찍어 1절의 결함 1~7이 사라졌는지 본다. 특히 `mobile-my-resume.png`(회색 알약·버튼), `mobile-review-pins.png`(핀 원형·삭제 버튼), `mobile-upload-preview.png`(게시 바 높이).
- 운영체제 모션 감소를 켠 상태(또는 개발자 도구 `prefers-reduced-motion: reduce` 에뮬레이션)에서 랜딩 제목 막대가 처음부터 칠해져 있는지 본다.
- id·클래스·HTML·JS는 바꾸지 않으므로 Playwright E2E 선택자는 영향이 없다. 그래도 `./gradlew test bootJar`와 E2E를 다시 돌린다.
