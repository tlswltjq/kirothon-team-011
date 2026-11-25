# Kiro Crew dynamic workflow: 교차 코드 리뷰 → 수정 → 테스트 반복 루프.
# 실행: workflow_run(source=<이 파일 내용>, args={"initial_findings": "...", "max_rounds": 3})
# 역할 분리: fix(유일한 쓰기, claude-sonnet-5.5/medium) → test(읽기, claude-haiku-5.5/low)
#            → review(읽기, gpt-5.6-terra/high). 구현 모델과 리뷰 모델 계열을 다르게 둔다.

META = {
    "name": "kirothon-review-fix-loop",
    "description": "교차 코드 리뷰(gpt-5.6-terra) → 수정(claude-sonnet-5.5) → 테스트(claude-haiku-5.5)를 깨끗해질 때까지 반복",
    "phases": ["수정", "테스트", "교차 리뷰"],
}

REPO = "/home/ubuntu/workspaces/kirothon"
AUTHOR = "git -c user.name='Jiseop, Shin' -c user.email=41179427+tlswltjq@users.noreply.github.com"

FIX_SCHEMA = {
    "type": "object",
    "properties": {
        "fixed": {"type": "array", "items": {"type": "string"}},
        "skipped": {"type": "array", "items": {"type": "object"}},
        "commit": {"type": "string"},
        "build_exit": {"type": "integer"},
    },
    "required": ["fixed", "skipped", "build_exit"],
}

TEST_SCHEMA = {
    "type": "object",
    "properties": {
        "build_exit": {"type": "integer"},
        "tests": {"type": "integer"},
        "failures": {"type": "array", "items": {"type": "string"}},
    },
    "required": ["build_exit", "tests", "failures"],
}

REVIEW_SCHEMA = {
    "type": "object",
    "properties": {
        "findings": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "severity": {"type": "string"},
                    "file": {"type": "string"},
                    "line": {"type": "string"},
                    "scenario": {"type": "string"},
                    "fix": {"type": "string"},
                },
            },
        },
        "verdict": {"type": "string", "enum": ["clean", "needs_fix"]},
    },
    "required": ["findings", "verdict"],
}

COMMON = (
    "작업 폴더: " + REPO + " (Git main). 먼저 .kiro/steering/*.md 와 "
    ".kiro/specs/resume-review/requirements.md, design.md 의 관련 절을 읽어라. "
    "push 금지. tasks.md 수정 금지. 다른 worktree 폴더는 건드리지 마라. "
)


def findings_text(items):
    lines = []
    for i, f in enumerate(items or []):
        lines.append(
            str(i + 1) + ". [" + str(f.get("severity", "")) + "] " + str(f.get("file", ""))
            + ":" + str(f.get("line", "")) + " - " + str(f.get("scenario", ""))
            + " / 제안: " + str(f.get("fix", ""))
        )
    return "\n".join(lines)


async def workflow(ctx):
    max_rounds = int(ctx.args.get("max_rounds", 3))
    todo = str(ctx.args.get("initial_findings", "")).strip()
    rounds = []
    remaining = []
    status = "remaining"

    for r in range(1, max_rounds + 1):
        ctx.phase("라운드 " + str(r) + ": 수정")
        fix = None
        if todo:
            fix = await ctx.agent(
                COMMON
                + "너는 구현 담당이다. 아래 지적을 하나씩 확인하고, 실제 결함이면 최소 수정과 회귀 테스트를 추가하라. "
                + "결함이 아니거나 범위 밖이면 고치지 말고 이유를 skipped에 적어라. "
                + "끝나면 ./gradlew --no-daemon test bootJar 를 실행해 종료 코드를 확인하고, 변경이 있으면 "
                + AUTHOR + " commit -am 또는 git add 후 commit 으로 커밋하라(메시지: 'Review round "
                + str(r) + ": <요약>').\n\n지적 목록:\n" + todo,
                label="fix r" + str(r),
                schema=FIX_SCHEMA,
                model="claude-sonnet-5.5",
                effort="medium",
                cwd=REPO,
            )
        else:
            ctx.log("라운드 " + str(r) + ": 반영할 지적 없음, 수정 생략")

        ctx.phase("라운드 " + str(r) + ": 테스트")
        test = await ctx.agent(
            COMMON
            + "너는 점검 담당이다. 파일을 수정하지 마라. ./gradlew --no-daemon test bootJar 를 실행하고 "
            + "종료 코드, 전체 테스트 수(build/test-results/test/*.xml 의 tests 합), 실패한 테스트 이름만 보고하라.",
            label="test r" + str(r),
            schema=TEST_SCHEMA,
            model="claude-haiku-5.5",
            effort="low",
            cwd=REPO,
        )

        ctx.phase("라운드 " + str(r) + ": 교차 리뷰")
        review = await ctx.agent(
            COMMON
            + "너는 교차 리뷰 담당이다(구현은 다른 모델). 파일을 수정하지 마라. "
            + "git log -p -5 로 최근 커밋과 src/main/java/dev/kirothon/resume/ 코드를 직접 읽고, "
            + "다음 관점의 심각도 높음·중간 결함만 보고하라: 접근 경계(원본 PDF·원문·타인 데이터·평가자 정보 노출), "
            + "마스킹 무결성(게시 검증 우회), 업로드 자원 한도(페이지 크기·타임아웃), 트랜잭션·동시성, "
            + "평가 API 규칙(중복·자기 평가·좌표 범위·초안 노출). 재현하지 못한 것은 scenario에 '추정'을 붙여라. "
            + "이미 고쳐진 것을 다시 보고하지 마라. 결함이 없으면 verdict='clean'.",
            label="review r" + str(r),
            schema=REVIEW_SCHEMA,
            model="gpt-5.6-terra",
            effort="high",
            cwd=REPO,
        )

        build_exit = test.get("build_exit", -1) if isinstance(test, dict) else -1
        failures = test.get("failures", []) if isinstance(test, dict) else ["test 단계 결과 없음"]
        found = review.get("findings", []) if isinstance(review, dict) else []
        verdict = review.get("verdict", "needs_fix") if isinstance(review, dict) else "needs_fix"
        rounds.append({
            "round": r,
            "fixed": fix.get("fixed", []) if isinstance(fix, dict) else [],
            "skipped": fix.get("skipped", []) if isinstance(fix, dict) else [],
            "commit": fix.get("commit", "") if isinstance(fix, dict) else "",
            "build_exit": build_exit,
            "tests": test.get("tests", 0) if isinstance(test, dict) else 0,
            "failures": failures,
            "review_findings": len(found),
            "verdict": verdict,
        })
        ctx.log("라운드 " + str(r) + ": build_exit=" + str(build_exit) + ", 리뷰 지적 " + str(len(found)) + "건, " + verdict)

        if build_exit == 0 and verdict == "clean":
            status = "clean"
            remaining = []
            break

        todo = findings_text(found)
        if failures:
            todo = todo + "\n실패한 테스트(먼저 고칠 것): " + ", ".join(failures)
        remaining = found

    return {"status": status, "rounds": rounds, "remaining": remaining}
