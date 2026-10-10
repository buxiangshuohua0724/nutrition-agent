#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""校对 docs/er-diagram.md 里对 docs/db/schema.sql 的行号引用。

为什么需要它
------------
er-diagram.md 成稿时 schema.sql 是 560 行，之后又加过生成列、CHECK、注释等，
现在（603 行）行号已经整体漂移。更麻烦的是漂移量**不是常数**：
食物域只差 1~2 行，营养域整段差 24 行，所以「统一加个偏移」这种修法必然出错，
而一个指错行的引用在页面上依然可以点开、依然显示一段 SQL，肉眼完全看不出问题。

判据
----
旧坐标取 `git show <BASE_REV>:docs/db/schema.sql`。最容易走错的一步是拿「这一行
的内容变了没有」当判据——给 7 张表补 `gd_deleted_key` 生成列之后，这些表的每一行
都换了行号、内容也确实变了，但引用一处处都没错。**「内容变了」不等于「引用错了」。**

本脚本用的是**内容指纹 + 表归属**：
1. 取该行号指着的那一行，去掉首尾空白后做 MD5 指纹，到旧坐标里找它的唯一落点；
2. 看那个落点当年属于哪张表，再与这个号现在落在哪张表比对；
3. 两张表不同才判为「这个号已经指到别的表上去了」。

「唯一」很关键：`PRIMARY KEY (`id`),` 这类行在十来张表里逐字重复，不做唯一性约束
就会把任意旧号映射到第一张同名行（sys_user），从而把全部引用误判成错的。

只在旧坐标里找不到唯一落点时（表外引用、后来新增的行），才退到「位置是否没变」
这种弱判据；这时若指纹在旧坐标里出现过就认，否则报「无从核对」交人回读原文。

文档自身结构是第二道判据：§4 的小节写着「定义位置 schema.sql:A-B」，该小节内的
其它引用若落在 A 命中的表之外，就是指向了别的表。这个约束**只在本节内生效**——
§4 之后还有 §6/§8，那里的引用本来就是跨表正文引用。

机器只能证明「行号表示的还是原来那一行」，证明不了「原来那一行就是这段文字想
说的事」。所以本脚本报 OK 之后仍需人类回读 schema 原文。

**已知盲区（实测过，必须承认）**：若被引用的号在新旧两版里都恰好是合法内容
（例如 `display_order`、`created_at` 这类在旧坐标更早处就出现过的行），把引用改到
那个号上，脚本会放行。它只保证「这个号在新坐标里站着实在内容、没指到别的表」，
不保证「它就是作者当初想指的那一行」。真实例子：把 `schema.sql:362`
（`uk_nv_variant_nutrient`）错写成 `schema.sql:300`（`nutrient.display_order`）时
`--check` 报 0 处问题。要抓住这类错误只能靠人工核对被引用的**对象名**。

用法
----
    python scripts/fix_erd_links.py --check   # 只报告，有待更新/对不上则非零退出
    python scripts/fix_erd_links.py --write   # 就地改写
"""

from __future__ import print_function

import argparse
import difflib
import hashlib
import io
import os
import re
import subprocess
import sys

try:
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
except AttributeError:  # pragma: no cover - 老 Python
    pass

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ERD = os.path.join(ROOT, 'docs', 'er-diagram.md')
SCHEMA_REL = 'docs/db/schema.sql'

# er-diagram.md 成稿时的 schema.sql 版本。行号以它为准。
BASE_REV = 'dfbf480'

CITE_RE = re.compile(r'schema\.sql:(\d+)(?:-(\d+))?')
HEAD_RE = re.compile(r'^#{2,4}\s*(\d+(?:\.\d+)*)\s')
RANGE_RE = re.compile(r'(\d+)\s*-\s*(\d+)')


def read_schema(rev=None):
    """读 schema。rev 为 None 时读工作区，否则读 git 里的那一版。

    一律按字节取出再自己 decode：`git show | Out-File -Encoding utf8` 会带 BOM，
    带 BOM 的行会整体错位一格，比错行号更难发现。
    """
    if rev is None:
        with io.open(os.path.join(ROOT, SCHEMA_REL), 'rb') as fh:
            return fh.read().decode('utf-8').lstrip('\ufeff').splitlines()
    out = subprocess.Popen(['git', 'show', '%s:%s' % (rev, SCHEMA_REL)],
                           cwd=ROOT, stdout=subprocess.PIPE).communicate()[0]
    return out.decode('utf-8').lstrip('\ufeff').splitlines()


def table_spans(lines):
    """每张表的 (起, 止)，1 基。止 = `) ENGINE=` 那一行。"""
    starts = {}
    for i, line in enumerate(lines):
        m = re.match(r'^CREATE TABLE `([a-z_]+)`', line)
        if m:
            starts[m.group(1)] = i
    spans = {}
    for name, s in starts.items():
        e = s
        while not lines[e].startswith(') ENGINE'):
            e += 1
        spans[name] = (s + 1, e + 1)
    return spans


def owner(spans, n):
    for name, (lo, hi) in spans.items():
        if lo <= n <= hi:
            return name
    return None


def line_map(old, new):
    """旧行号 -> 新行号。只认 difflib 判定为逐字相同的 equal 段。"""
    sm = difflib.SequenceMatcher(None, old, new, autojunk=False)
    mapping = {}
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag == 'equal' and (i2 - i1) == (j2 - j1):
            for k in range(i2 - i1):
                mapping[i1 + k + 1] = j1 + k + 1
    return mapping


def tag(s):
    return hashlib.md5(s.strip().encode('utf-8')).hexdigest()[:12]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--write', action='store_true', help='就地改写 er-diagram.md')
    ap.add_argument('--check', action='store_true', help='只报告（默认行为）')
    args = ap.parse_args()

    old = read_schema(BASE_REV)
    new = read_schema(None)
    spans_old, spans_new = table_spans(old), table_spans(new)
    mapping = line_map(old, new)
    old_tags = set(tag(l) for l in old if l.strip())

    with io.open(ERD, 'rb') as fh:
        doc = fh.read().decode('utf-8')
    lines = doc.split('\n')

    # 每行的「内容指纹 -> 行号」，只有唯一时才敢用来做锚点
    uniq_new, uniq_old = {}, {}
    for i, l in enumerate(new, 1):
        uniq_new.setdefault(tag(l), []).append(i)
    for i, l in enumerate(old, 1):
        uniq_old.setdefault(tag(l), []).append(i)
    uniq_new = {k: v[0] for k, v in uniq_new.items() if len(v) == 1}
    uniq_old = {k: v[0] for k, v in uniq_old.items() if len(v) == 1}

    # §4 各小节的「定义位置」区间，用来判断小节内其它引用是不是跑到了别的表。
    # 只认 `表注释：…；定义位置 [\`…schema.sql:A-B\`]` 这一种写法，比解析标题层级稳。
    sections = []
    for i, line in enumerate(lines, 1):
        if '定义位置' in line:
            m = CITE_RE.search(line)
            if m:
                a = int(m.group(1))
                b = int(m.group(2)) if m.group(2) else a
                lo, hi = (a, b) if b >= a else (b, a)
                sections.append({'line': i, 'lo': lo, 'hi': hi, 'end': None})
    for k, s in enumerate(sections):
        # 本段管到下一段的「定义位置」之前；下一段自己那行仍归它自己管
        s['end'] = sections[k + 1]['line'] if k + 1 < len(sections) else len(lines) + 1

    def section_of(n):
        """n 行归哪一段「定义位置」管：n 之前最近的一个，且中间没有再起新标题。

        中间起过标题就作废——§4 之后还有 §6/§8，那些小节里的引用是跨表正文引用，
        拿 §4 最后一张表的区间去要求它们必然全错。
        """
        hit = None
        for s in sections:
            if s['line'] <= n:
                hit = s
            else:
                break
        if hit is None or n >= hit['end']:
            return None
        for i in range(hit['line'], n):
            if HEAD_RE.match(lines[i - 1].strip().lstrip('> ').replace('**', '')):
                return None
        return hit

    def usable(n):
        """当前 schema 的 n 行是不是实在内容（空行、分隔线不算）。"""
        return 1 <= n <= len(new) and bool(new[n - 1].strip()) and new[n - 1].strip() != '--'

    def owns(n, sec):
        """n 的行号在当前 schema 里是否落在「该管的」那张表里。

        判据分两步，缺一不可：
        ① **表归属**：§4 的小节亲手写下「定义位置 A-B」，说明这一段里的引用只应该
           落在 A 命中的那张表里。这一步必须先做——`display_order`、`created_at`
           这类行在好几张表里逐字重复，只靠内容指纹根本认不出来（指纹不唯一时
           下面的 `back` 会直接是 None），只有表归属拦得住。
        ② **内容指纹**：拿 n 指着的这一行去旧坐标里找同内容的位置，那个位置按
           difflib 换算到新坐标应当还是 n。若算出来是别的号，说明这行号还停在
           旧坐标上。
        返回 (ok, note)；ok=False 时 note 说明为什么不敢认。
        """
        if not usable(n):
            return False, '该号在当前 schema 里是空行/分隔线'
        name = owner(spans_new, n)
        if sec is not None:
            want = owner(spans_new, sec['lo'])
            if want is not None and name != want:
                return False, '该号落在 `%s`，本节的「定义位置」是 `%s`' % (name, want)
        t = tag(new[n - 1])
        back = uniq_old.get(t)
        if back is None:
            # 内容在旧坐标里没有唯一落点：要么是表外引用（开头几行、脚本收尾的
            # SET FOREIGN_KEY_CHECKS），要么是重复行，要么是后来新增/改写过的行。
            if not (1 <= n <= len(old)):
                in_old = t in old_tags
                return in_old, ('' if in_old else '该号超出旧坐标范围，且内容在旧坐标里不存在')
            if name is None and t in old_tags:
                return (uniq_old.get(t) == n), '该号指向表外内容，位置已变'
            return True, ''
        on = owner(spans_old, back)
        if on is None or name is None:
            return True, ''            # 有一侧在表外：无法拿表归属定罪
        if on != name:
            return False, '该号在旧坐标里属于 `%s`，而内容是 `%s` 的 :%d' % (on, name, back)
        if mapping.get(back) != n:
            # 内容确实是同一张表里的，但位置换算不回 n：这行的行号还没换过来。
            return False, '该号的内容在旧坐标里是 :%d，换算到新坐标应为 :%d' % (back, mapping[back])
        return True, ''

    def relocate_anchor(n, sec):
        """把「当前架构里 n 这一行的内容」在旧坐标里的位置换回新坐标。

        注意方向：引用里的号已经指到某一行了，要判断它是不是「旧的号」，就得拿
        **它在当前文件里指着的这一行**去旧坐标里找同一个内容，再看那个旧位置
        按 difflib 换到新坐标是几号。若换出来的号不是 n，说明 n 这个号属于旧坐标。
        """
        if not (1 <= n <= len(new)):
            return None
        t = tag(new[n - 1])
        if not new[n - 1].strip():
            return None
        o = uniq_old.get(t)
        if o is None or o not in mapping:
            return None
        # 内容在旧坐标里只有一处，且这一处确实是「n 这一行」——才算锚点
        return mapping[o] if owner(spans_old, o) is not None else None

    def relocate_range(a, b, sec):
        """区间端点单独算：写错一行的右端不能拖着整段一起作废。"""
        na = relocate_anchor(a, sec)
        if na is None or na == a:
            return None
        if b is None:
            return (na, None)
        nb = relocate_anchor(b, sec)
        if nb is None and sec is not None and sec['hi'] == b:
            nb = sec['hi']
        return (na, nb) if nb is not None else (na, None)

    todo, mismatch, ok = [], [], 0

    for idx, text in enumerate(lines, start=1):
        sec = section_of(idx)
        for m in CITE_RE.finditer(text):
            a = int(m.group(1))
            b = int(m.group(2)) if m.group(2) else None
            aok, anote = owns(a, sec)
            bok, bnote = (True, '') if b is None else owns(b, sec)
            if aok and bok:
                ok += 1
                continue
            note = anote or bnote
            if aok:
                # 只有右端不对：左端已经在新坐标上，单独把右端拉回来
                got = relocate_range(b, None, sec) if b else None
                if got:
                    todo.append((idx, a, b, a, got[0]))
                    continue
            got = relocate_range(a, b, sec)
            if got is None:
                here = new[a - 1].strip()[:60] if 0 < a <= len(new) else '<越界>'
                mismatch.append((idx, a, b, note or here))
                continue
            todo.append((idx, a, b, got[0], got[1]))

    total = len(CITE_RE.findall(doc))
    print('er-diagram.md 共 %d 处 schema.sql 引用；旧坐标 = %s（%d 行），当前 %d 行'
          % (total, BASE_REV, len(old), len(new)))
    print('  行号与新坐标一致（不代表指向的对象正确）：%d 处' % ok)
    print('  待更新（仍停在旧坐标）：%d 处' % len(todo))
    for idx, a, b, na, nb in todo:
        print('    doc:%-5d :%s -> :%s' % (idx, '%d-%d' % (a, b) if b else a,
                                          '%d-%d' % (na, nb) if nb else na))
    print('  对不上（需人工回读 schema 原文裁定）：%d 处' % len(mismatch))
    for idx, a, b, txt in mismatch:
        print('    doc:%-5d :%s  当前该行是 %s' % (idx, '%d-%d' % (a, b) if b else a, txt))
    if ok + len(todo) + len(mismatch) != total:
        print('  计数异常：%d + %d + %d != %d（有引用没被检查到，必须查）'
              % (ok, len(todo), len(mismatch), total))

    if args.write and todo:
        for idx, a, b, na, nb in todo:
            old_tok = 'schema.sql:%d-%d' % (a, b) if b else 'schema.sql:%d' % a
            new_tok = 'schema.sql:%d-%d' % (na, nb) if nb else 'schema.sql:%d' % na
            lines[idx - 1] = lines[idx - 1].replace(old_tok, new_tok)
        with io.open(ERD, 'wb') as fh:
            fh.write('\n'.join(lines).encode('utf-8'))
        print('已改写 %d 处' % len(todo))

    return 1 if (todo or mismatch) else 0


if __name__ == '__main__':
    sys.exit(main())
