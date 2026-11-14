# kirothon

Kirothon 2026 개인 참가 저장소. 제품 코드는 주제 공개 후 작성한다.

- 계획·절차: `HACKATHON.md`
- Kiro 규칙: `.kiro/steering/`, 프로젝트 에이전트: `.kiro/agents/kirothon.json`
- 에이전트 실행: `scripts/kiro.sh [spec|impl|test|check|review] ["지시"]`. 지시가 없으면 대화형. `--trust-all-tools`로 승인 없이 실행하고, 역할별 쓰기 범위는 에이전트 권한 규칙으로 막는다. 역할표: `.kiro/steering/agents.md`
- 템플릿: `.kiro/templates/` (spec, product, deploy)
- Kiro 활용 기록: `python3 scripts/kiro_log.py status|add`, `KIRO-USAGE.md`

## 점검
```sh
python3 scripts/tools.py doctor
python3 -B -m unittest discover -s tests -v
```
