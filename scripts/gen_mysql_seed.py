"""从 H2 种子脚本生成 MySQL 版种子数据（docs/db/data.sql）。

为什么需要这个脚本
------------------
docs/db/schema.sql 是 MySQL 语法（供建库用），但种子数据只有 H2 那一份
（backend/src/main/resources/db/h2/data.sql）—— 因为 dev/test 走 H2 profile 时
由 spring.sql.init 自动加载。mysql profile 的 sql.init.mode 是 never，
应用既不建表也不灌种子，于是 MySQL 库里表结构齐备、数据却全空，注册时直接炸：

    INSERT INTO sys_user_role (user_id, role_id) VALUES (?, 1)
    → Cannot add or update a child row: a foreign key constraint fails
      (`nutrition_agent`.`sys_user_role`, CONSTRAINT `fk_ur_role`
       FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`))

（sys_role 里没有 id=1 那行。UserService.USER_ROLE_ID = 1。）

为什么是"生成"而不是"再写一份"
------------------------------
手写第二份种子必然与 H2 那份漂移：加了食品忘了同步、改了某条营养数值两边不一致，
而这类漂移在 CI 里不可见（测试只跑 H2）。所以这里把 H2 种子当作唯一事实来源，
逐行机械转换，转换规则只有一条：

    H2 2.x 的自增列不因显式插入而推进，故末尾统一用
        ALTER TABLE t ALTER COLUMN id RESTART WITH n;
    重置；MySQL 的等价语句是
        ALTER TABLE t AUTO_INCREMENT = n;

其余语句（纯 INSERT、固定时间戳字面量 '2025-01-01 00:00:00'、SQL 标准的
两个单引号转义）两边通用，原样保留。遇到未登记形态的语句直接抛错退出，
绝不静默丢弃 —— 悄悄少插入一张表比生成失败危险得多。

用法
----
    python scripts/gen_mysql_seed.py            # 生成/更新 docs/db/data.sql
    python scripts/gen_mysql_seed.py --check    # 只校验是否与 H2 种子同步（CI 门禁用）

导入 MySQL：
    mysql -uroot -p nutrition_agent < docs/db/data.sql
"""

import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "backend", "src", "main", "resources", "db", "h2", "data.sql")
DST = os.path.join(ROOT, "docs", "db", "data.sql")

# H2：ALTER TABLE food ALTER COLUMN id RESTART WITH 24;
RESTART_RE = re.compile(r"^ALTER TABLE\s+(\w+)\s+ALTER COLUMN id RESTART WITH\s+(\d+);\s*$")

HEADER = """\
-- =============================================================================
-- MySQL 演示种子数据
-- =============================================================================
-- ⚠️ 本文件由 scripts/gen_mysql_seed.py 从
--    backend/src/main/resources/db/h2/data.sql 自动生成，请勿手工编辑。
--    改了 H2 那份之后重新跑一次脚本即可，--check 模式会拦住未同步的提交。
--
-- 用法（先由 docs/db/schema.sql 建好表结构）：
--     mysql -uroot -p nutrition_agent < docs/db/data.sql
--
-- 【数据性质声明】
--   本文件是 DEMO 演示数据，仅用于功能演示、前端联调与自动化测试。
--   所有营养数值的来源（source_id）一律指向 data_source.id = 1（code = DEMO-SEED），
--   即《AI 食品营养师演示数据集（非真实来源）》。数值不代表真实营养成分，
--   严禁对外发布，严禁用作任何营养建议或健康决策的依据。
--   data_source 中登记的 CFCT-6 / CN-DIETARY-GUIDE-2022 / WS-T-578-2017 /
--   FOOD-SAFETY-LAW 仅是"来源档案"条目本身，未编造页码、URL，也未声称任何具体
--   数值出处；其 url 列一律为 NULL，nutrition_value.source_detail 一律为 NULL。
--
-- 【与 H2 版的唯一差异】
--   末尾的 {reset_count} 条自增序列重置：H2 写作
--       ALTER TABLE t ALTER COLUMN id RESTART WITH n;
--   MySQL 写作
--       ALTER TABLE t AUTO_INCREMENT = n;
--   其余语句逐字相同。另在本文件开头补一条 SET NAMES utf8mb4，
--   避免 Windows 下 mysql 客户端按 latin1 解析导致中文变乱码。
-- =============================================================================

SET NAMES utf8mb4;

"""


def transform(text: str):
    """把 H2 种子的正文转成 MySQL 可执行形式，返回 (正文, 重置清单)。

    按「语句」而不是按「行」判定。种子里的多行 INSERT 会出现不带括号的续行
    （某个字段的中文描述独立成行，于是续行以引号开头），逐行套白名单一定会误杀。
    """
    lines = text.split("\n")

    # 丢掉 H2 版的头部注释块，直到第一条 INSERT —— 头部整体换成上面的 HEADER。
    try:
        first = next(i for i, line in enumerate(lines) if line.startswith("INSERT INTO"))
    except StopIteration:
        raise SystemExit("在 %s 里找不到任何 INSERT 语句，源头可能已被破坏" % SRC)

    out = []
    resets = []
    buf = []
    start = 0

    def flush():
        stmt = "\n".join(buf).strip()
        match = RESTART_RE.match(stmt)
        if match:
            table, n = match.group(1), match.group(2)
            out.append("ALTER TABLE %s AUTO_INCREMENT = %s;" % (table, n))
            resets.append((table, int(n)))
        elif stmt.startswith("INSERT INTO"):
            out.append(stmt)
        else:
            raise SystemExit(
                "data.sql:%d 出现未登记形态的语句，拒绝生成（宁可失败也不要悄悄漏插）：\n  %s"
                % (start, stmt.split("\n")[0][:120])
            )
        buf.clear()

    for lineno, line in enumerate(lines[first:], start=first + 1):
        if not buf:
            if line.strip() == "" or line.startswith("--"):
                out.append(line)  # 语句之间的注释与空行原样保留
                continue
            start = lineno
        buf.append(line)
        if line.rstrip().endswith(";"):
            flush()

    if buf:
        raise SystemExit("data.sql:%d 的语句没有以分号结束：%s" % (start, buf[0][:120]))
    if not resets:
        raise SystemExit("没有转换到任何自增序列重置语句 —— 源头的格式可能已变，请核对")
    return "\n".join(out), resets


def build() -> str:
    with open(SRC, encoding="utf-8") as handle:
        text = handle.read()
    body, resets = transform(text)
    header = HEADER.format(reset_count=len(resets))
    audit = "".join("--     %-24s AUTO_INCREMENT = %d\n" % (t, n) for t, n in resets)
    # 把重置清单附在头部声明之后，方便人工核对
    header = header.rstrip("\n") + "\n-- 【自增序列重置清单】\n" + audit + "\n"
    return header + body.lstrip("\n")


def main() -> int:
    check = "--check" in sys.argv[1:]
    generated = build()

    current = ""
    if os.path.exists(DST):
        with open(DST, encoding="utf-8") as handle:
            current = handle.read()
    if check:
        if current == generated:
            print("[OK] docs/db/data.sql 与 H2 种子同步")
            return 0
        print("[FAIL] docs/db/data.sql 已过期，请运行：python scripts/gen_mysql_seed.py")
        return 1

    if current == generated:
        print("[OK] docs/db/data.sql 无需改动")
        return 0
    # 无 BOM、LF 换行
    with open(DST, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(generated)
    inserts = sum(1 for line in generated.split("\n") if line.startswith("INSERT INTO"))
    print("[OK] 已生成 docs/db/data.sql：%d 条 INSERT、%d 条自增重置" % (inserts, generated.count("AUTO_INCREMENT")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
