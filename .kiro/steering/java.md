---
inclusion: fileMatch
fileMatchPattern: "**/*.{java,kt}"
name: java
description: Java/Kotlin 코드와 테스트 작성 규칙
---
# Java/Kotlin 규칙
- import는 단순 클래스명으로 쓴다. 다른 패키지의 동명 클래스가 충돌할 때만 FQN을 쓴다.
- 컨트롤러는 입력 검증(Bean Validation)과 HTTP 상태 코드를 명확히 한다. 오류 응답은 안정적인 `code` 필드를 갖는다.
- 외부 연동은 인터페이스 뒤에 두고 타임아웃·실패 시 샘플 응답 구현을 함께 만든다.
- 새 동작마다 HTTP 수준 테스트(MockMvc 또는 실제 포트)를 추가한다. 테스트 이름은 요구사항 번호를 포함한다.
