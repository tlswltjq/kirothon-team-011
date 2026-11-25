# Kiro Crew dynamic workflow: UX 흐름 설계 → 교차 평가 → 수정 반복.
# 실행: workflow_run(source=<이 파일 내용>, args={"brief": "...", "max_rounds": 3, "target": 8.5})
# 설계자(claude-opus-5.5/high)만 ux-flow.md를 쓴다. 평가자(gpt-5.6-terra/high)는 읽기 전용.

META = {
    "name": "kirothon-ux-design-loop",
    "description": "사용자 흐름 설계(claude-opus-5.5) → 사용성 교차 평가(gpt-5.6-terra) → 수정을 기준 점수까지 반복",
    "phases": ["설계", "평가"],
}

REPO = "/home/ubuntu/workspaces/kirothon"
DOC = ".kiro/specs/resume-review/ux-flow.md"

CRITIQUE_SCHEMA = {
    "type": "object",
    "properties": {
        "scores": {
            "type": "object",
            "properties": {
                "first_use_clarity": {"type": "number"},
                "task_efficiency": {"type": "number"},
                "trust_and_privacy": {"type": "number"},
                "feedback_and_errors": {"type": "number"},
                "consistency": {"type": "number"},
                "demo_impact": {"type": "number"},
                "feasibility": {"type": "number"},
            },
        },
        "average": {"type": "number"},
        "issues": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "severity": {"type": "string"},
                    "where": {"type": "string"},
                    "problem": {"type": "string"},
                    "suggestion": {"type": "string"},
                },
            },
        },
        "strengths": {"type": "array", "items": {"type": "string"}},
    },
    "required": ["scores", "average", "issues"],
}

CONTEXT = (
    "프로젝트: " + REPO + " ('이게 다 너 잘되라고 하는 소리야', 익명 이력서 평가 서비스, KIROTHON 2026). "
    "먼저 읽어라: .kiro/steering/product.md, .kiro/specs/resume-review/requirements.md, design.md(특히 API 표와 '요청·응답 JSON 계약'), "
    "현재 화면 src/main/resources/static/*.html, js/*.js, css/*.css, 현재 화면 스크린샷 artifacts/e2e/*.png(가상 인물 샘플, 개인정보 없음). "
    "demo-private/ 폴더는 개인정보라 절대 열지 마라. "
)


def issues_text(items):
    out = []
    for i, it in enumerate(items or []):
        out.append(
            str(i + 1) + ". [" + str(it.get("severity", "")) + "] " + str(it.get("where", ""))
            + " - " + str(it.get("problem", "")) + " / 제안: " + str(it.get("suggestion", ""))
        )
    return "\n".join(out)


async def workflow(ctx):
    brief = str(ctx.args.get("brief", ""))
    max_rounds = int(ctx.args.get("max_rounds", 3))
    target = float(ctx.args.get("target", 8.5))
    feedback = ""
    rounds = []
    final = None

    for r in range(1, max_rounds + 1):
        ctx.phase("라운드 " + str(r) + ": 설계")
        task = (
            CONTEXT
            + "너는 UX 설계자다. " + DOC + " 하나만 쓰거나 고친다(다른 파일 수정 금지, 커밋 금지). "
            + "사용자 요구:\n" + brief + "\n\n"
            + "문서에 담을 것: 1) 핵심 원칙 3~5개, 2) 두 흐름(평가하기, 평가받기)의 단계별 화면 흐름(mermaid flowchart), "
            + "3) 화면별 명세: 목적, 첫눈에 보이는 것, 주요 버튼 문구(실제 한국어 문구), 상태(로딩·빈 상태·오류·완료), 모바일 배치, "
            + "4) 익명 토큰을 언제 어떻게 만드는지(첫 클릭에서 마찰을 최소화), 5) 신뢰·개인정보 안심 장치(미리보기 확인, 원본 삭제 안내), "
            + "6) 3분 데모에서 보여 줄 '와' 순간, 7) 백엔드 변경이 필요한 부분(가능하면 기존 API 그대로, 꼭 필요한 최소 변경만 명시), "
            + "8) 구현 작업 목록(화면 파일 단위, 1시간 안에 끝낼 범위로 우선순위). "
            + "범위: MVP 기능(마스킹·익명 평가·주인의 버전별 평가 확인)은 유지하고 새 큰 기능은 넣지 마라. "
            + "문서 끝에 '변경 이력' 절을 두고 이번 라운드에서 무엇을 왜 바꿨는지 적어라."
        )
        if feedback:
            task = task + "\n\n직전 평가에서 받은 지적(반영하고, 반영하지 않으면 변경 이력에 이유를 적어라):\n" + feedback
        await ctx.agent(task, label="design r" + str(r), model="claude-opus-5.5", effort="high", cwd=REPO)

        ctx.phase("라운드 " + str(r) + ": 평가")
        crit = await ctx.agent(
            CONTEXT
            + "너는 사용성 평가자다(설계는 다른 모델이 했다). 파일을 수정하지 마라. " + DOC + "를 읽고 평가하라. "
            + "관점: (a) 처음 온 취업 준비자가 설명 없이 30초 안에 첫 행동을 하는가, (b) 평가하기 루프가 빠르고 즐거운가, "
            + "(c) 평가받기에서 개인정보가 가려졌다는 확신을 주는가, (d) 로딩·빈 상태·오류 안내, (e) 화면 간 일관성, "
            + "(f) 해커톤 심사위원이 3분 데모로 보고 감탄할 장면이 있는가, (g) 남은 시간(약 1시간)에 정적 HTML/JS로 구현 가능한가. "
            + "각 항목 0~10점, 평균, 심각도(high/medium/low)별 문제와 구체적 수정 제안, 잘한 점을 보고하라. "
            + "사용자 요구: " + brief,
            label="critique r" + str(r),
            schema=CRITIQUE_SCHEMA,
            model="gpt-5.6-terra",
            effort="high",
            cwd=REPO,
        )
        avg = crit.get("average", 0) if isinstance(crit, dict) else 0
        issues = crit.get("issues", []) if isinstance(crit, dict) else []
        highs = [i for i in issues if str(i.get("severity", "")).lower() == "high"]
        rounds.append({"round": r, "average": avg, "high": len(highs), "issues": len(issues),
                       "scores": crit.get("scores", {}) if isinstance(crit, dict) else {}})
        ctx.log("라운드 " + str(r) + ": 평균 " + str(avg) + ", high " + str(len(highs)) + ", 전체 지적 " + str(len(issues)))
        final = crit
        if avg >= target and not highs:
            break
        feedback = issues_text(issues)

    return {"rounds": rounds, "final": final, "doc": DOC}
