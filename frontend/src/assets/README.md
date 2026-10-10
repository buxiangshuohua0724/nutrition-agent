# 前端视觉素材清单（frontend/src/assets）

本目录存放 AI 食品营养师前端全部**自绘**视觉素材（矢量 SVG）。

> **版权声明：本目录下所有 SVG 素材均为本项目自行绘制，可自由使用，不包含任何第三方素材、字体、位图或 base64 内嵌资源，无版权风险。**
> 素材配色与 `frontend/src/design/tokens.css` 中定义的色板协调（主色 `#3B82F6`，辅助色 `#10B981` / `#F59E0B` / `#EF4444` / `#6B7280`）。
> 所有文件均为合法独立 XML，含 `xmlns` 与 `viewBox`，不含脚本、`<foreignObject>`、外部字体或位图引用。

---

## 一、图标 `icons/`（33 个）

统一规格：`viewBox="0 0 64 ..."` → 实为 **`0 0 24 24`**，`width/height="24"`，`fill="none"`，
`stroke="currentColor"`、`stroke-width="1.8"`、`stroke-linecap="round"`、`stroke-linejoin="round"`。

**颜色不写死**：全部使用 `currentColor`，跟随父元素 `color`（建议 `color: var(--na-color-text-secondary)`，
激活态用 `var(--na-color-primary-500)`）。`heart-filled` 的填充同样使用 `currentColor`。

| 文件名 | 用途 | viewBox |
| --- | --- | --- |
| `home.svg` | 侧边栏「首页」 | `0 0 24 24` |
| `search.svg` | 侧边栏「食品搜索」/ 搜索按钮 | `0 0 24 24` |
| `category.svg` | 侧边栏「分类浏览」 | `0 0 24 24` |
| `image.svg` | 侧边栏「图片识别」 | `0 0 24 24` |
| `chat.svg` | 侧边栏「AI 问答」 | `0 0 24 24` |
| `health.svg` | 侧边栏「我的健康」 | `0 0 24 24` |
| `settings.svg` | 侧边栏底部「设置」/ 系统设置 | `0 0 24 24` |
| `admin.svg` | 管理后台入口（带滑块的列表） | `0 0 24 24` |
| `plus.svg` | 新增食品 / 新建对话 | `0 0 24 24` |
| `edit.svg` | 食品、营养数据编辑 | `0 0 24 24` |
| `trash.svg` | 删除对话 / 收藏 / 记录 | `0 0 24 24` |
| `upload.svg` | 上传食品图片 | `0 0 24 24` |
| `check.svg` | 识别结果确认 / 审核通过 | `0 0 24 24` |
| `close.svg` | 关闭弹窗 / 取消 | `0 0 24 24` |
| `chevron-right.svg` | 列表项进入 / 分页下一页 | `0 0 24 24` |
| `chevron-down.svg` | 下拉菜单 / 折叠面板 | `0 0 24 24` |
| `chevron-left.svg` | 返回 / 分页上一页 | `0 0 24 24` |
| `heart.svg` | 收藏（未收藏，空心） | `0 0 24 24` |
| `heart-filled.svg` | 收藏（已收藏，实心 `currentColor`） | `0 0 24 24` |
| `share.svg` | 分享食品详情 | `0 0 24 24` |
| `send.svg` | 发送问答消息 | `0 0 24 24` |
| `sparkle.svg` | AI 星标（回答首字/智能标识） | `0 0 24 24` |
| `warning.svg` | 注意事项 / 高风险提示 | `0 0 24 24` |
| `info.svg` | 数据来源说明 / 不确定性提示 | `0 0 24 24` |
| `clock.svg` | 更新时间 / 对话时间 / 加载耗时 | `0 0 24 24` |
| `refresh.svg` | 重新上传 / 重新加载 / 重建索引 | `0 0 24 24` |
| `filter.svg` | 分类筛选 / 条件过滤 | `0 0 24 24` |
| `sort.svg` | 搜索结果排序 | `0 0 24 24` |
| `user.svg` | 个人中心 / 用户头像占位 | `0 0 24 24` |
| `logout.svg` | 退出登录 | `0 0 24 24` |
| `chart.svg` | 营养分析报告 / 每日汇总图表 | `0 0 24 24` |
| `leaf.svg` | 品牌绿叶标识 / 营养健康 | `0 0 24 24` |
| `menu.svg` | 移动端汉堡菜单 | `0 0 24 24` |

> 注：`viewBox` 列中「用途」与「viewBox」两栏均在表中给出；所有图标 viewBox 值一致，均为 `0 0 24 24`。

---

## 二、分类插画 `categories/`（10 个）

统一规格：`viewBox="0 0 64 64"`，**圆形柔和底色（`r=28`）+ 中心图形**，用于首页与分类页的分类入口。

| 文件名 | 分类名称 | category.slug | viewBox | 底色 |
| --- | --- | --- | --- | --- |
| `vegetables.svg` | 蔬菜 | `vegetables` | `0 0 64 64` | `#E7F6EC` 绿 |
| `fruits.svg` | 水果 | `fruits` | `0 0 64 64` | `#FDEBEC` 红/粉 |
| `meat.svg` | 肉类 | `meat` | `0 0 64 64` | `#FBE9E7` 红棕 |
| `aquatic.svg` | 水产 | `aquatic` | `0 0 64 64` | `#E6F1FB` 蓝 |
| `eggs.svg` | 蛋类 | `eggs` | `0 0 64 64` | `#FDF5E0` 黄 |
| `grains.svg` | 谷物 | `grains` | `0 0 64 64` | `#FBF0DA` 麦黄 |
| `dairy.svg` | 乳制品 | `dairy` | `0 0 64 64` | `#F3F4EF` 米白 |
| `nuts.svg` | 坚果 | `nuts` | `0 0 64 64` | `#F5EDE4` 棕 |
| `legumes.svg` | 豆制品 | `legumes` | `0 0 64 64` | `#EBF5E4` 浅绿 |
| `others.svg` | 其他 | `others` | `0 0 64 64` | `#ECEFF4` 灰蓝 |

> 底色值已同步登记为 `tokens.css` 中的 `--na-color-cat-*-bg`，如需统一调整请同时修改两处。
> 插画文件名即 `food_category.slug`，前端可按 `slug` 直接拼路径：`/src/assets/categories/${slug}.svg`。

---

## 三、食品插画 `foods/`（20 个）

统一规格：`viewBox="0 0 160 120"`（4:3，对应 `--na-ratio-food`），**浅色圆角底（`rx=14`）+ 主图形 + 高光/阴影层次**，用于食品卡片与食品详情页。

| 文件名 | 食品名称 | food.code | viewBox | 主色 |
| --- | --- | --- | --- | --- |
| `veg-tomato.svg` | 西红柿 | `VEG_TOMATO` | `0 0 160 120` | 红 `#EF4444` |
| `veg-cucumber.svg` | 黄瓜 | `VEG_CUCUMBER` | `0 0 160 120` | 深绿 `#2F8F46` |
| `veg-spinach.svg` | 菠菜 | `VEG_SPINACH` | `0 0 160 120` | 绿叶 `#3EA457` |
| `veg-carrot.svg` | 胡萝卜 | `VEG_CARROT` | `0 0 160 120` | 橙 `#F97316` |
| `veg-cabbage.svg` | 大白菜 | `VEG_CABBAGE` | `0 0 160 120` | 浅绿白 `#86C46A` |
| `veg-broccoli.svg` | 西兰花 | `VEG_BROCCOLI` | `0 0 160 120` | 深绿 `#2F8F46` |
| `veg-potato.svg` | 土豆 | `VEG_POTATO` | `0 0 160 120` | 土黄 `#D9A44E` |
| `veg-onion.svg` | 洋葱 | `VEG_ONION` | `0 0 160 120` | 紫 `#8B5CF6` |
| `veg-garlic.svg` | 大蒜 | `VEG_GARLIC` | `0 0 160 120` | 白 `#FFFFFF` |
| `veg-eggplant.svg` | 茄子 | `VEG_EGGPLANT` | `0 0 160 120` | 紫 `#6D28D9` |
| `fruit-apple.svg` | 苹果 | `FRUIT_APPLE` | `0 0 160 120` | 红 `#EF4444` |
| `fruit-banana.svg` | 香蕉 | `FRUIT_BANANA` | `0 0 160 120` | 黄 `#FACC15` |
| `fruit-blueberry.svg` | 蓝莓 | `FRUIT_BLUEBERRY` | `0 0 160 120` | 蓝紫 `#6366F1` |
| `fruit-orange.svg` | 橙子 | `FRUIT_ORANGE` | `0 0 160 120` | 橙 `#F97316` |
| `fruit-grape.svg` | 葡萄 | `FRUIT_GRAPE` | `0 0 160 120` | 紫 `#7E22CE` |
| `aquatic-salmon.svg` | 三文鱼 | `AQUATIC_SALMON` | `0 0 160 120` | 橙粉 `#FB7185` |
| `aquatic-bass.svg` | 鲈鱼（清蒸鱼） | `AQUATIC_BASS` | `0 0 160 120` | 灰白 `#94A3B8` |
| `egg-chicken-egg.svg` | 鸡蛋 | `EGG_CHICKEN_EGG` | `0 0 160 120` | 米白黄 `#FEF3C7` |
| `meat-beef.svg` | 牛肉 | `MEAT_BEEF` | `0 0 160 120` | 红棕 `#B91C1C` |
| `dairy-milk.svg` | 牛奶 | `DAIRY_MILK` | `0 0 160 120` | 白蓝 `#FFFFFF`/`#3B82F6` |

> **命名规则**：foods 文件名 = `food.code` 小写并把下划线换成连字符。
> 例：`VEG_TOMATO` → `veg-tomato.svg`。前端映射函数：
> `code.toLowerCase().replace(/_/g, '-')` + `.svg`。
> 未收录的 `food.code` 请使用 `.na-empty` 空状态或分类插画兜底，不要引用不存在的文件。

---

## 四、使用与维护约定

1. **图标**是单色描边素材，必须通过 `currentColor` 着色，禁止在 SVG 内写死颜色。
2. **分类插画与食品插画**是带固定配色的成品插画，颜色已与色板协调，不要用 CSS `filter` 或 `fill` 覆盖。
3. 引用于 `<img>` 或 `background-image` 时，插画的 `aria-label` / `<title>` 不会被读出，
   需要无障碍语义时请改用内联 `<svg>` 或在宿主元素上加 `alt` / `aria-label`。
4. 新增食品插画时必须同步更新本清单表，并保持「`food.code` → 文件名」的命名规则一致。
5. 素材与 `docs/db/schema.sql` 的 `food.code`、`food_category.slug` 对齐；若 schema 中新增食品或分类，
   本目录需要补齐对应插画，或明确走兜底方案。

---

## 五、校验方式

所有 SVG 已逐个通过 XML 解析校验，命令：

```bash
python -c "import xml.dom.minidom,sys; xml.dom.minidom.parse(sys.argv[1])" frontend/src/assets/icons/home.svg
```

批量校验（在项目根目录执行）：

```bash
python - <<'PY'
import glob, xml.dom.minidom
files = sorted(glob.glob('frontend/src/assets/**/*.svg', recursive=True))
bad = []
for f in files:
    try:
        xml.dom.minidom.parse(f)
    except Exception as e:
        bad.append((f, str(e)))
print(f'total={len(files)} ok={len(files)-len(bad)} bad={len(bad)}')
for f, e in bad:
    print('FAIL', f, e)
PY
```
