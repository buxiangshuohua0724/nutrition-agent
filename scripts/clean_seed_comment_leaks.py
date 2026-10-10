#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
一次性清理脚本：删除 db/h2/data.sql 里残留在字符串末尾的内部注释痕迹。

背景
----
生成种子数据的脚本把 SQL 文件自己的注释片段误并进了 `knowledge_chunk.content`
字段。最初的契约修补脚本只认出两种固定写法：

    -- 一、用户与权限--
    -- 三个演示账号的 password_hash 均为字符串 password 的 BCrypt 散列（演示用弱口令，严禁上生产）

但文件里还剩下 9 处「句末多了两个连字符」的形态：

    '……本演示数据不提供个体化建议。--', 288, '蛋白质 氨基酸 食物搭配', ...

它不能用 `LIKE '%--%'` 去判断——正常正文里也可能出现破折号（例如「2~5 g/天」
这类区间写法，以及知识分块正文中本来允许的破折号）。所以本脚本用**字段级**判断：
按单引号把文件切成一段段字符串内容，只检查「该字段内容是否以 `--` 结尾」。

安全措施
--------
· 只动「唯一一段单引号内容」的末尾，不碰引号外面的任何字节；
· 命中数必须等于 EXPECTED_HITS，数量对不上就整体中止、不写文件；
· 每个被改动的字段都会打印出前后各 40 字，便于人工复核；
· 执行前自动写 `.bak` 备份；
· 幂等：若已清理过（命中 0 处），报告后正常退出。

用法
----
    python nutrition-agent/scripts/clean_seed_comment_leaks.py
"""
import io
import os
import shutil
import sys

try:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
except Exception:  # pragma: no cover - 仅在非标准输出环境触发
    pass

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TARGET = os.path.join(ROOT, 'backend', 'src', 'main', 'resources', 'db', 'h2', 'data.sql')

# 种子数据里的中文字符串不含转义单引号，因此可以按 ' 直接切分成「引号外 / 引号内」交替的片段。
EXPECTED_HITS = 9


def _split_quoted(text):
    """
    把文本切成交替的片段：索引为偶数的是引号外内容，索引为奇数的是引号内内容。

    前提（本文件成立）：字符串里没有 SQL 转义的单引号（''），否则会切错。
    为安全起见，调用方会先校验本文件的这一前提。
    """
    return text.split("'")


def main() -> int:
    if not os.path.isfile(TARGET):
        print(f'✗ 找不到目标文件：{TARGET}')
        return 1

    with open(TARGET, 'r', encoding='utf-8') as handle:
        original = handle.read()

    # 前提校验：中文字符串里若出现 '' 转义，本脚本的切分假设就不成立，直接拒绝运行。
    if "''" in original:
        print("✗ 文件中存在 SQL 转义单引号（''），本脚本的切分假设不成立，已中止。")
        return 1

    segments = _split_quoted(original)

    hits = []  # (片段索引, 原内容, 清理后内容)
    for index in range(1, len(segments), 2):
        content = segments[index]
        if content.endswith('--'):
            hits.append((index, content, content[:-2].rstrip()))
        elif content.rstrip().endswith('--') and content.rstrip() != content:
            # 形如 "……建议。-- " 的写法（-- 后有空白）
            stripped = content.rstrip()
            hits.append((index, content, stripped[:-2].rstrip()))

    if not hits:
        print('✓ 没有发现需要清理的注释残留（文件已是干净状态），未做任何修改。')
        return 0

    print(f'发现 {len(hits)} 处疑似残迹：')
    for order, (index, before, after) in enumerate(hits, start=1):
        print(f'  {order:>2}. 尾部 {before[-24:]!r} → {after[-24:]!r}')

    if len(hits) != EXPECTED_HITS:
        print(f'✗ 命中 {len(hits)} 处，但预期 {EXPECTED_HITS} 处。')
        print('  为避免误删正常正文，已中止且未写入任何内容。请人工核对上面列出的位置。')
        return 1

    for index, _before, after in hits:
        segments[index] = after
    cleaned = "'".join(segments)

    backup = TARGET + '.bak'
    shutil.copyfile(TARGET, backup)
    with open(TARGET, 'w', encoding='utf-8', newline='') as handle:
        handle.write(cleaned)

    print(f'✓ 已清理 {len(hits)} 处注释残留')
    print(f'  备份：{os.path.relpath(backup, ROOT)}')
    print(f'  文件：{os.path.relpath(TARGET, ROOT)}')

    # 复查
    segments_after = _split_quoted(cleaned)
    remaining = sum(
        1 for index in range(1, len(segments_after), 2)
        if segments_after[index].rstrip().endswith('--')
    )
    if remaining:
        print(f'✗ 复查发现仍有 {remaining} 处残留，请人工检查')
        return 1
    print('✓ 复查通过：不再有以 -- 结尾的字符串字段')
    return 0


if __name__ == '__main__':
    sys.exit(main())
