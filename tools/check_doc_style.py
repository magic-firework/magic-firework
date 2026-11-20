#!/usr/bin/env python3
"""文档风格门禁：约束见 constraints.md。

用法：
    python3 tools/check_doc_style.py               # 检查 DEFAULT_TARGETS
    python3 tools/check_doc_style.py docs/*.md     # 只查列出的文件或目录
    python3 tools/check_doc_style.py --list        # 打印实际生效的阈值
    python3 tools/check_doc_style.py --json        # 输出 JSON

阈值是规则，不是建议。改 THRESHOLDS / LIMITS / FACTS 等于改规则，需要第二人批准。
退出码 0 = 通过；1 = 有违规。
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# 通用预算：所有文档先套这一份，键含义见 constraints.md §1。
THRESHOLDS = {
    "cjk": 2200,        # 汉字上限。超出说明该删内容，不是该放宽阈值。
    "table_ratio": 0.25,
    "bold_per_1k": 8,
    "arrows": 3,
}

# 逐文件覆盖：键是相对仓库根的路径，值是 THRESHOLDS 的子集。
# 例：{"docs/spec.md": {"cjk": 1600}, "README.md": {"table_ratio": 0.1}}
# 放宽要写理由：规则类文档本身就是一张表，所以表格占比单独给高。
LIMITS: dict[str, dict] = {
    "constraints.md": {"table_ratio": 0.45},
    "comments.md": {"table_ratio": 0.40},
}

# 默认检查范围：文件或目录，不存在的只提示不报错。
DEFAULT_TARGETS = ("README.md", "docs", "constraints.md", "comments.md")

# 跨文件事实唯一性：事实字符串 -> 唯一允许写它的文件。留空则跳过该检查。
# 例：{"3 次重试": "docs/spec.md"}
FACTS: dict[str, str] = {}

SKIP_DIRS = {"third_party", "vendor", "generated", "dist", "build", "out", "node_modules", ".git"}
MAX_PARAGRAPH_LINES = 4
MAX_EM_DASH = 2

# 装饰性措辞：出现即违规，除非能给出判据（那就改写，而不是加豁免）。
BANNED = [
    "一句话", "一页速览", "一页速记", "不要假装不存在", "诚实清单", "说得清",
    "最重要的", "核心资产", "任何时刻都成立", "落地物", "骨架", "这就是为什么",
    "众所周知", "不难发现", "值得强调的是", "总而言之", "完美地", "彻底解决",
]

NUMBER_RE = re.compile(r"\d+(?:\.\d+)?\s*%|\d+\s*个\s*(?:用例|测试|函数|类|接口)")
EVIDENCE_RE = re.compile(r"`[^`]+`|\]\(|/|python3|npm|pytest|make|bash|cargo")
CODE_SPAN = re.compile(r"`[^`]*`")
LIST_RE = re.compile(r"^\s*(?:[-*+]|\d+\.)\s")


def load(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def body_lines(text: str) -> list[str]:
    """去掉围栏代码块与空行。"""
    out, in_fence = [], False
    for line in text.splitlines():
        if line.strip().startswith("```"):
            in_fence = not in_fence
            continue
        if not in_fence and line.strip():
            out.append(line)
    return out


def cjk(text: str) -> int:
    return len(re.findall(r"[\u4e00-\u9fff]", text))


def paragraphs(lines: list[str]) -> list[list[str]]:
    """连续非空、非标题、非表格、非列表的行视为一段。"""
    paras, cur = [], []
    for line in lines:
        solid = (
            LIST_RE.match(line)
            or line.lstrip().startswith(("|", "#", ">"))
            or line[:1].isspace()
        )
        if solid and cur:
            paras.append(cur)
            cur = []
        if not solid:
            cur.append(line)
    if cur:
        paras.append(cur)
    return paras


def check_file(limits: dict, text: str) -> list[str]:
    lines = body_lines(text)
    bad: list[str] = []
    c, bold = cjk(text), len(re.findall(r"\*\*[^*\n]+\*\*", text))

    if c > limits["cjk"]:
        bad.append(f"字数 {c} > {limits['cjk']} 汉字：删内容，别放宽阈值")
    table = sum(1 for l in lines if l.lstrip().startswith("|")) / max(len(lines), 1)
    if table > limits["table_ratio"]:
        bad.append(f"表格占非空行 {table:.0%} > {limits['table_ratio']:.0%}：改用句子写因果")
    density = bold * 1000 / max(c, 1)
    if density > limits["bold_per_1k"]:
        bad.append(f"加粗密度 {density:.1f}/千字 > {limits['bold_per_1k']}：加粗只留标题级重点")
    arrows = text.count("→")
    if arrows > limits["arrows"]:
        bad.append(f"箭头 {arrows} 个 > {limits['arrows']}：流程写成一两句，或改用编号")
    if text.count("——") > MAX_EM_DASH:
        bad.append(f"破折号 {text.count('——')} > {MAX_EM_DASH}：删掉修辞性插入")

    prose = [CODE_SPAN.sub("", line) for line in text.splitlines()]
    for word in BANNED:
        for i, line in enumerate(prose, 1):
            if word in line:
                bad.append(f"禁用措辞「{word}」 行 {i}：删掉或换成判据")

    for para in paragraphs(lines):
        if len(para) > MAX_PARAGRAPH_LINES:
            bad.append(f"段落 {len(para)} 行 > {MAX_PARAGRAPH_LINES}：行 {para[0][:24]}…")

    for i, line in enumerate(text.splitlines(), 1):
        if re.search(r"≤|≥|不超过|不大于|以上|以内|上限|阈值|预算", line):
            continue  # 阈值声明，不是测量值
        if NUMBER_RE.search(line) and not EVIDENCE_RE.search(line):
            bad.append(f"数字无出处 行 {i}：{line.strip()[:40]}")

    return bad


def check_facts(texts: dict[str, str]) -> list[str]:
    """跨文件事实只准一个文件写，其余改成链接（constraints.md §3）。"""
    bad = []
    emphasis = re.compile(r"[*_]")
    stripped = {n: emphasis.sub("", CODE_SPAN.sub("", t)) for n, t in texts.items()}
    for fact, owner in FACTS.items():
        hits = [n for n, t in stripped.items() if fact in t and n != owner]
        if hits:
            others = ", ".join(hits)
            bad.append(f"事实「{fact}」同时出现在 {others}：只在 {owner} 写，其余改成链接")
    return bad


def display(path: Path) -> str:
    try:
        return path.resolve().relative_to(ROOT).as_posix()
    except ValueError:
        return path.as_posix()


def expand(targets: list[str]) -> tuple[list[Path], list[str]]:
    files: list[Path] = []
    missing: list[str] = []
    for target in targets:
        p = Path(target)
        if not p.is_absolute() and not p.exists():
            p = ROOT / target
        if p.is_dir():
            files.extend(
                sorted(f for f in p.rglob("*.md") if not (SKIP_DIRS & set(f.parts)))
            )
        elif p.is_file():
            files.append(p)
        else:
            missing.append(target)
    return files, missing


def limits_for(name: str) -> dict:
    merged = dict(THRESHOLDS)
    merged.update(LIMITS.get(name, {}))
    return merged


def main(argv: list[str]) -> int:
    as_json = "--json" in argv
    args = [a for a in argv if not a.startswith("-")]

    if "--list" in argv:
        print(json.dumps({"thresholds": THRESHOLDS, "limits": LIMITS, "facts": FACTS},
                         ensure_ascii=False, indent=2))
        return 0

    targets = args or list(DEFAULT_TARGETS)
    files, missing = expand(targets)
    if missing and args:
        print(f"缺少文件或目录: {', '.join(missing)}")
        return 1
    if not files:
        print(f"没有找到可检查的文档：{', '.join(targets)}")
        return 0

    names = [display(f) for f in files]
    texts = {n: load(f) for n, f in zip(names, files)}
    failed = 0
    payload: dict[str, list[str]] = {}
    for name in names:
        bad = check_file(limits_for(name), texts[name])
        payload[name] = bad
        if as_json:
            continue
        print(f"[{'FAIL' if bad else 'ok'}] {name}")
        for item in bad:
            print(f"    - {item}")
        failed += bool(bad)

    total = len(names)
    if FACTS:
        extra = check_facts(texts)
        if not as_json:
            print(f"[{'FAIL' if extra else 'ok'}] 跨文件事实唯一性")
            for item in extra:
                print(f"    - {item}")
        payload["__facts__"] = extra
        failed += bool(extra)
        total += 1

    if as_json:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    print(f"\n{total - failed}/{total} 通过")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
