# 이게 다 너 잘되라고 하는 소리야

**익명으로 이력서를 평가받고, 버전마다 받은 첨삭을 모아 보는 서비스.** Kirothon 2026 개인 참가작(주제: 내 일상에 바로 적용할 수 있는 서비스).

## 문제
- 채용 플랫폼과 연계된 이력서 평가는 이름과 원문을 공개해야 해서 부담스럽다. 지인에게 부탁하면 평가자가 몇 명뿐이다.
- 개인정보를 지우려고 텍스트만 복사해 보여 주면 이력서의 디자인(가시성·시인성)은 평가받지 못한다.
- 이력서는 버전마다 평가가 달라지는데, 받은 피드백은 메신저와 메일에 흩어진다.

## 해결
1. **PDF 업로드 → 자동 마스킹:** 이메일·전화·URL·사진을 자동으로, 이름·회사·학교는 지정한 단어로 찾아 페이지 이미지 위에 검은 박스를 칠한다. 레이아웃은 그대로 남는다.
2. **미리보기 확인 후 게시:** 원본 PDF는 게시 후 삭제하고, 평가자에게는 마스킹된 PNG만 보낸다.
3. **익명 무작위 평가:** 다른 사용자가 무작위로 받은 이력서 위를 클릭해 그 위치에 첨삭 핀을 남기고 총평·총점(1~5)을 준다.
4. **버전별 평가 모아 보기:** 주인은 버전마다 모든 평가자의 핀을 페이지 위에 겹쳐 보고 총평·평균 총점을 확인한다.

## 기술
Java 21, Spring Boot 4.1.2, Gradle 8.14.3(Wrapper SHA-256 고정), Apache PDFBox 3.0.7, H2, 정적 HTML/JS(외부 CDN 없음, CSP 적용). 외부 API와 AI 없이 동작한다.

## 실행
```sh
./gradlew test bootJar
SERVER_ADDRESS=127.0.0.1 SERVER_PORT=18080 java -jar build/libs/app.jar
# http://127.0.0.1:18080
```
- 데이터는 `DATA_DIR`(기본 `./data`)의 H2 파일 DB에 저장한다.
- Docker 로컬 배포는 [`DEPLOY.md`](DEPLOY.md)를 참고한다.
- 데모용 가상 인물 이력서: `src/main/resources/demo/`.

## Kiro 활용
spec 주도 개발, 역할별 에이전트(모델·추론 깊이·쓰기 범위 분리), hook, git worktree 병렬 서브에이전트, GPT 교차 리뷰, 마감 알림까지 Kiro로 진행했다.
- 보고서: [`docs/KIRO.md`](docs/KIRO.md)
- 실행 기록: [`KIRO-USAGE.md`](KIRO-USAGE.md)
- 명세: [`.kiro/specs/resume-review/`](.kiro/specs/resume-review/), 규칙: [`.kiro/steering/`](.kiro/steering/), 에이전트: [`.kiro/agents/`](.kiro/agents/)
- 진행·결정 기록: [`HACKATHON.md`](HACKATHON.md)

## 점검 도구
```sh
python3 scripts/tools.py doctor            # 도구·kiro-cli 로그인·에이전트 설정
python3 -B -m unittest discover -s tests   # 점검 도구 테스트
python3 scripts/kiro_log.py status         # spec 진행률과 Kiro 기능 기록
```
