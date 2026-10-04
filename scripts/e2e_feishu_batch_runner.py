#!/usr/bin/env python3
"""Fast batch runner for Feishu clean-test E2E scenarios.

Posts all scenarios first, waits once, then evaluates one docker log window.
This avoids the slow sequential wait of e2e_feishu_scenario_runner.py.
"""
from __future__ import annotations

import argparse
import concurrent.futures
import json
import re
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

import e2e_feishu_scenario_runner as base

REPO_ROOT = Path(__file__).resolve().parents[1]

# Synthetic replay uses fake Feishu message IDs, so Feishu reply API will log
# "id not exist" errors. They are harness artifacts, not business failures.
HARNESS_ARTIFACTS = [
    r"The request you send is not a valid \{open_message_id\}",
    r"Invalid ids: \[om_E2E_",
    r"id not exist",
]


def strip_harness_artifacts(logs: str) -> str:
    kept = []
    skip_next_stack = False
    for line in logs.splitlines():
        if any(re.search(p, line) for p in HARNESS_ARTIFACTS):
            skip_next_stack = True
            continue
        if skip_next_stack and (line.startswith("org.springframework.web.client.HttpClientErrorException") or line.startswith("\tat ")):
            continue
        skip_next_stack = False
        kept.append(line)
    return "\n".join(kept)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default=base.DEFAULT_HOST)
    parser.add_argument("--key", default=base.DEFAULT_KEY)
    parser.add_argument("--container", default=base.DEFAULT_CONTAINER)
    parser.add_argument("--url", default=base.DEFAULT_URL)
    parser.add_argument("--out", default=str(REPO_ROOT / "docs" / "e2e-feishu-scenario-report.md"))
    parser.add_argument("--wait", type=int, default=120)
    parser.add_argument("--only")
    args = parser.parse_args()

    run_id = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S") + "_" + uuid.uuid4().hex[:6]
    selected = base.SCENARIOS
    if args.only:
        ids = {x.strip() for x in args.only.split(",") if x.strip()}
        selected = [s for s in selected if s.sid in ids]

    start_epoch = int(time.time())
    posts_by_sid = {s.sid: [] for s in selected}

    def post_one(s: base.Scenario, i: int) -> tuple[str, dict]:
        prefix = f"E2E_{run_id}_{s.sid}"
        sender = s.sender if s.concurrent == 1 else f"ou_e2e_user_{i}"
        chat = s.chat if s.concurrent == 1 else f"oc_e2e_chat_{i}"
        base_msg_id = f"om_{prefix}"
        msg_id = base_msg_id if s.repeat_same_message_id > 1 else f"{base_msg_id}_{i}_{uuid.uuid4().hex[:6]}"
        text = f"[{prefix}] {s.text}"
        payload = base.event_payload(run_id, s, msg_id, sender, chat, text)
        rc, out = base.post_payload(args.host, args.key, args.url, payload)
        return s.sid, {"message_id": msg_id, "sender": sender, "chat": chat, "http": out.strip().replace("\n", " | "), "rc": str(rc)}

    jobs = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as ex:
        for s in selected:
            count = s.concurrent if s.concurrent > 1 else s.repeat_same_message_id
            for i in range(1, count + 1):
                jobs.append(ex.submit(post_one, s, i))
        for fut in concurrent.futures.as_completed(jobs):
            sid, post = fut.result()
            posts_by_sid[sid].append(post)

    time.sleep(args.wait)
    since = max(1, int(time.time()) - start_epoch + 5)
    cp = base.ssh_cmd(args.host, args.key, f"docker logs --since {since}s {args.container} 2>&1 || true", timeout=120)
    raw_logs = cp.stdout
    logs = strip_harness_artifacts(raw_logs)

    results = []
    for s in selected:
        prefix = f"E2E_{run_id}_{s.sid}"
        scenario_lines = "\n".join([ln for ln in logs.splitlines() if prefix in ln or (s.sid == "duplicate_same_message_id" and "重复推送已忽略" in ln)])
        expect_results = []
        forbid_results = []
        for pat in s.expect:
            expect_results.append({"pattern": pat, "ok": bool(re.search(pat, scenario_lines, re.I | re.M))})
        for pat in s.forbid:
            # Generic ERROR/Exception is handled after artifact stripping; keep specific danger checks.
            if pat in {r"ERROR", r"Exception"}:
                count = len(re.findall(pat, scenario_lines, re.I | re.M))
            else:
                count = len(re.findall(pat, scenario_lines, re.I | re.M))
            forbid_results.append({"pattern": pat, "count": count, "ok": count == 0})
        ok = all(x["ok"] for x in expect_results) and all(x["ok"] for x in forbid_results) and all(p.get("rc") == "0" for p in posts_by_sid[s.sid])
        results.append({
            "sid": s.sid,
            "title": s.title,
            "severity": s.severity,
            "ok": ok,
            "posts": sorted(posts_by_sid[s.sid], key=lambda x: x["message_id"]),
            "expect": expect_results,
            "forbid": forbid_results,
            "log_excerpt": "\n".join(scenario_lines.splitlines()[-30:]),
            "note": s.note,
        })

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(base.report_markdown(results, run_id), encoding="utf-8")
    summary = {
        "run_id": run_id,
        "total": len(results),
        "passed": sum(r["ok"] for r in results),
        "failed": sum(not r["ok"] for r in results),
        "harness_artifact_reply_errors": len(re.findall(r"Invalid ids: \[om_E2E_", raw_logs)),
        "report": str(out),
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0 if summary["failed"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
