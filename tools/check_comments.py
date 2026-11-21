#!/usr/bin/env python3
"""注释门禁：规则见 comments.md。

用法：
    python3 tools/check_comments.py src
    python3 tools/check_comments.py --json src lib

ERROR 是确定性检查（会让 CI 红）；WARN 是启发式提示（只打印，不影响退出码）。
"""
from __future__ import annotations

import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

# 规则参数（改这里 = 改规则，需第二人批准）
MAX_DENSITY = 0.15          # 注释行 / 非空行
MIN_LINES_FOR_DENSITY = 150  # 小文件谈密度没有统计意义，交给块长规则；常量/接口表靠 @contract:
MAX_BLOCK = 6               # 连续注释块行数上限
MAX_HEADER = 5              # 文件头注释行数上限
MAX_TODO_PER_KLOC = 3
BANNED = [
    "显然", "注意", "此处", "这里我们", "本函数用于", "该函数用于", "如上所述",
    "如下所示", "可以看到", "简单来说", "需要注意的是", "暂时", "众所周知",
    "obviously", "as mentioned above", "simply put",
]
CODE_SUFFIXES = {
    ".c", ".h", ".cc", ".cpp", ".hpp", ".cs", ".java", ".kt", ".py",
    ".ts", ".tsx", ".js", ".jsx", ".go", ".rs", ".rb", ".php", ".swift",
    ".glsl", ".vert", ".frag",
}
SKIP_DIRS = {"third_party", "vendor", "generated", "dist", "build", "out", "node_modules", ".git"}
CONTRACT_MARK = "@contract:"

BANNER_RE = re.compile(r"^\s*(?://+|#+|/\*)\s*([-=*_#~])\1{3,}")
DATE_RE = re.compile(r"\b20\d\d[-/.年]\s?\d{1,2}\b")
AUTHOR_RE = re.compile(r"@author|作者[:：]|created by|modified by|修改[于日]", re.I)
TODO_RE = re.compile(r"\b(TODO|FIXME|XXX|HACK)\b")
TODO_OK_RE = re.compile(r"TODO\(\s*[\w.@-]+\s*,\s*#\d+\s*\)")
COMMENTED_CODE_RE = re.compile(
    r"^\s*(?://|#)\s*(?:"
    r"(?:if|for|while|return|else|int|float|double|void|bool|struct|class)\b"
    r"|[A-Za-z_][\w.>\[\]]*\s*\([^)]*\)\s*;"
    r"|[A-Za-z_]\w*\s*=\s*[^=]"
    r"|#(?:include|define)"
    r"|[{}]"
    r")"
)
RESTATE_VERB_RE = re.compile(
    r"^\s*(设置|获取|创建|生成|初始化|计算|遍历|循环|判断|检查|调用|返回|绘制|编译|链接|绑定|更新|读取|写入|打开|关闭)"
)
ASCII_TOKEN_RE = re.compile(r"[A-Za-z_]\w{2,}")


@dataclass
class Report:
    errors: list[str] = field(default_factory=list)
    warns: list[str] = field(default_factory=list)


def split_lines(
    text: str, suffix: str
) -> tuple[list[tuple[int, str]], list[tuple[int, str]], list[tuple[int, str]]]:
    """返回 (整行注释, 行内注释, 代码)。行内注释不参与密度与块长判定。"""
    hash_comments = suffix == ".py"
    comments, inline, code = [], [], []
    in_block = False
    for no, raw in enumerate(text.splitlines(), 1):
        line = raw.rstrip()
        stripped = line.strip()
        if not stripped:
            continue
        if in_block:
            comments.append((no, stripped))
            if "*/" in stripped:
                in_block = False
            continue
        if stripped.startswith("/*"):
            comments.append((no, stripped))
            in_block = "*/" not in stripped
            continue
        if stripped.startswith("//") or (hash_comments and stripped.startswith("#")):
            comments.append((no, stripped))
            continue
        if "//" in line:
            head, tail = line.split("//", 1)
            code.append((no, head.strip()))
            if tail.strip():
                inline.append((no, tail.strip()))
        elif hash_comments and "#" in stripped:
            head, tail = stripped.split("#", 1)
            code.append((no, head.strip()))
            if tail.strip():
                inline.append((no, tail.strip()))
        else:
            code.append((no, stripped))
    return comments, inline, code


def blocks(comments: list[tuple[int, str]]) -> list[list[tuple[int, str]]]:
    out: list[list[tuple[int, str]]] = []
    for item in comments:
        if out and item[0] == out[-1][-1][0] + 1:
            out[-1].append(item)
        else:
            out.append([item])
    return out


def check_file(path: Path) -> Report:
    rep = Report()
    text = path.read_text(encoding="utf-8", errors="replace")
    comments, inline, code = split_lines(text, path.suffix)
    if not code:
        return rep

    total = len(comments) + len(code)
    density = len(comments) / total
    if total >= MIN_LINES_FOR_DENSITY and density > MAX_DENSITY:
        rep.errors.append(
            f"注释密度 {density:.0%} > {MAX_DENSITY:.0%}（{len(comments)} 行注释 / {total} 非空行）：先删复述类"
        )

    for no, body in comments + inline:
        for word in BANNED:
            if word in body:
                rep.errors.append(f"行 {no}: 废话词「{word}」")
        if DATE_RE.search(body):
            rep.errors.append(f"行 {no}: 注释含日期，历史属于 git")
        if AUTHOR_RE.search(body):
            rep.errors.append(f"行 {no}: 注释含作者/修改记录")
        if body.startswith("TODO") and not TODO_OK_RE.search(body):
            rep.errors.append(f"行 {no}: TODO 未写 TODO(owner, #issue)")

    for no, body in comments:
        if BANNER_RE.match(body):
            rep.errors.append(f"行 {no}: 分隔线注释，删掉")
        for hit in TODO_RE.finditer(body):
            if hit.group(1) == "TODO" and TODO_OK_RE.search(body):
                continue
            rep.errors.append(f"行 {no}: {hit.group(1)} 未写 TODO(owner, #issue)")
        if COMMENTED_CODE_RE.match(body):
            rep.errors.append(f"行 {no}: 注释掉的代码，删（git 有历史）")

    for block in blocks(comments):
        span = len(block)
        if span <= MAX_BLOCK:
            continue
        if any(CONTRACT_MARK in body for _, body in block):
            continue
        if block[0][0] == 1:
            if span > MAX_HEADER:
                rep.warns.append(
                    f"文件头 {span} 行 > {MAX_HEADER}：留用途、输入输出、陷阱，其余挪到 README/报告"
                )
            continue
        rep.errors.append(f"行 {block[0][0]}: 注释块 {span} 行 > {MAX_BLOCK}，拆或挪进文档")

    # 启发式：注释复述紧随其后的代码（只提示，不拦）
    code_at = dict(code)
    for no, body in comments:
        nxt = code_at.get(no + 1)
        if not nxt:
            continue
        plain = body.lstrip("/#* ").strip()
        if not plain:
            continue
        comment_idents = set(ASCII_TOKEN_RE.findall(plain))
        ident = set(ASCII_TOKEN_RE.findall(nxt))
        is_statement = not nxt.startswith(("if", "for", "while", "return", "}", "#"))
        if comment_idents and ident:
            overlap = comment_idents & ident
            if len(overlap) >= 2 and len(overlap) / len(ident) >= 0.6:
                rep.warns.append(f"行 {no}: 疑似复述代码（与下一行重名 {sorted(overlap)[:3]}）")
        elif not comment_idents and is_statement and RESTATE_VERB_RE.match(plain):
            rep.warns.append(f"行 {no}: 疑似复述代码（动词开头的单语句），删了试试")

    todos = sum(1 for _, b in comments if TODO_RE.search(b))
    if len(code) >= 200 and todos * 1000 / len(code) > MAX_TODO_PER_KLOC:
        rep.errors.append(f"TODO {todos} 个 / {len(code)} 行代码，超过每千行 {MAX_TODO_PER_KLOC}")
    return rep


def collect(targets: list[str]) -> list[Path]:
    files: list[Path] = []
    for target in targets:
        p = Path(target)
        if p.is_file() and p.suffix in CODE_SUFFIXES:
            files.append(p)
        elif p.is_dir():
            for f in sorted(p.rglob("*")):
                rel = f.relative_to(p)
                if f.suffix in CODE_SUFFIXES and not (SKIP_DIRS & set(rel.parts)):
                    files.append(f)
    return files


def main(argv: list[str]) -> int:
    as_json = "--json" in argv
    targets = [a for a in argv if not a.startswith("-")] or ["."]
    files = collect(targets)
    if not files:
        print(f"没有找到可检查的源文件：{', '.join(targets)}")
        return 0

    def by_line(items: list[str]) -> list[str]:
        def key(text: str) -> int:
            hit = re.search(r"行 (\d+)", text)
            return int(hit.group(1)) if hit else 0

        return sorted(items, key=key)

    total_e = total_w = 0
    payload = {}
    for f in files:
        rep = check_file(f)
        total_e += len(rep.errors)
        total_w += len(rep.warns)
        payload[str(f)] = {"errors": rep.errors, "warns": rep.warns}
        if as_json or not (rep.errors or rep.warns):
            continue
        tag = "FAIL" if rep.errors else "warn"
        print(f"[{tag}] {f}")
        for item in by_line(rep.errors):
            print(f"    - {item}")
        for item in by_line(rep.warns):
            print(f"    ~ {item}")

    if as_json:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    print(f"\n{len(files)} 个文件：{total_e} 个错误，{total_w} 个提示")
    return 1 if total_e else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
