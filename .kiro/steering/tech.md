---
inclusion: always
name: tech
description: kirothon 기술 스택, 검증 명령, 안전 규칙
---
# 기술 규칙
- 개인 해커톤. 주제는 당일 공개. 핵심 사용자 흐름 하나를 먼저 완주한다.
- Java/Kotlin + Spring. JDK 21. 언어/Boot 버전은 앱 생성 때 확정하고 Gradle Wrapper 배포 SHA-256을 고정한다.
- 기본 검증: `./gradlew test bootJar`. 앱 실행 후 `python3 scripts/tools.py smoke --port 18080`(prod 구성은 `--management-port 18081` 추가).
- 서버는 항상 `SERVER_ADDRESS=127.0.0.1`로 바인딩한다. 외부 포트 공개, 방화벽 변경 금지. 예외: 2026-11-14 사용자가 직접 켠 Tailscale Funnel(:8443 → 127.0.0.1:18080, 심사 기간 한정). 컨테이너(`compose.prod.yaml`) 안에서는 포트 매핑이 닿도록 `0.0.0.0`에 바인딩하되, 호스트 공개는 `127.0.0.1:` 포트 매핑으로만 한다(production-readiness spec).
- 프로젝트 루트 밖 파일, 기존 DB/프로세스, 전역 설정은 변경하지 않는다.
- 새 의존성은 필요성과 호환성을 확인한다. 비밀 파일을 읽거나 키를 출력하지 않는다.
- 외부 API에는 타임아웃·실패 처리·시연용 샘플 응답을 넣는다. 실제 호출 성공과 mock 성공을 구분해 보고한다.
- 검증 통과를 추측하지 않는다. 완료 보고: 변경 내용, 실행 명령과 종료 코드, 미검증 항목, 남은 문제.
- 기능과 무관한 리팩토링은 분리한다.
