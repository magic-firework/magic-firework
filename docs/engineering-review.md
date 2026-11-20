# Magic Firework 工程问题诊断报告

> 本文件是评审报告：评审轮不改源码、构建脚本或 CI。执行阶段（B0–B2）的提交与验证见顶部“执行状态”，问题登记册与路线图保留评审时的口径。

- 评审基线：`HEAD = 28cc133`（`fix(fabric.mod.json): use English punctuation in the description`）
- 评审时工作区状态：`git status --porcelain` 为 **14 个已修改 + 9 个未跟踪**；`git diff --stat` 为 14 files changed, 458 insertions(+), 59 deletions(-)
- 问题总数：**18**（P0 5 条、P1 4 条、P2 9 条）
- 批次：**B0–B4**，见第 4 章

## 执行状态（本轮）

评审后的工作区（14 M + 9 ??）已经落成 3 个功能提交，当前分支为 `content/retune-flight-speeds`，工作区干净。

| 批次 | 状态 | 提交 | 验证 |
|---|---|---|---|
| B0 | 完成 | `34e2170` `b505a94` `f17097b` `b5671bb` `b4fb2b0` | 3 个功能提交各自 `./gradlew test` 通过；读侧上限与校验同提交 |
| B1 | 完成（ENG-04 运行期断言留 B3；ENG-02 决策用纯接缝测试，ItemStack 网格走查留运行期） | `eede96a` `11c7a2d` `637d4d0` `cee15b9` `e619db2` `54cbc31` | 6 个提交各自 `./gradlew test` 通过 |
| B2 | 完成 | `cc32bf7` `77dd3e7` | `SchemaParityTest` + 共享夹具 + `check_docs.py`；Python 32/32、3 个坏夹具被拒、边界夹具通过 |
| B3 | 部分（门禁/可复现完成；运行期冒烟未完成） | `abb4376` `61b1afe` `217be80` `77874d9` `d150e66` | Spotless/ErrorProne/JaCoCo/锁定/校验/版本已落；干净 clone 的 `build --offline` 通过 |
| B4 | 完成（ENG-10 的 recipe 例外；覆盖率达标） | `61ff4f4` `a8de33b` `99a9df3` `a65eb67` `161d545` `59063ad` `127122b` `2a44360` `05f297d` `39206f6` | 7/7 项完成；`check` 通过；覆盖率 50.5% |

当前验证：JDK 25（`/usr/lib/jvm/java-25-openjdk-amd64`）下 `./gradlew build --offline` 在干净 clone 中通过；`./gradlew check --offline --no-configuration-cache --no-parallel --max-workers=1`（含 Spotless、ErrorProne `-Werror`、JaCoCo）通过；`./gradlew test` 共 **236** 个测试、0 失败；instruction coverage **50.5%**，阈值 45%；Python 32/32、坏夹具被拒、`check_docs.py` 通过。

运行期观察：`./gradlew runServer` 已走到 `firework runtime started` 与内容加载，世界生成超过本地超时（无崩溃栈）；B3 的 light/pending 冒烟断言仍未完成。

观察：开启 configuration cache 时曾出现 `EOFException` / `NoSuchFileException`（`build/test-results/test/binary/...`），改用 `--no-configuration-cache` 后稳定；CI 默认仍开启，B3 应观察或关闭缓存以排除测试结果写入竞争。本轮在 WSL/7.8GB 环境下关闭配置缓存后仍偶发一次 `EOFException`（完整套件，重跑通过，单包分组全部通过），与测试结果二进制写入竞争一致；`--max-workers=1` 与停止多余 daemon 后未再复现。§1.3 的“未执行”项指评审时状态，执行阶段已补上 `build`/`test`，但未启动客户端或完成专用服务器/GameTest 冒烟。

---

## 1. 范围与局限

### 1.1 已阅读的材料

| 类别 | 数量 / 对象 |
|---|---|
| 主源码 | 79 个 `src/main/java` 文件 |
| 客户端源码 | 6 个 `src/client/java` 文件 |
| 测试 | 25 个测试文件，158 个 `@Test` |
| 构建 | `build.gradle`、`settings.gradle`、`gradle.properties`、wrapper（Gradle 9.8.2）、`gradle/gradle-daemon-jvm.properties` |
| CI / 仓库 | `.github/workflows/build.yml`、`.gitignore`、`fabric.mod.json` |
| 内容契约 | `src/main/resources/schemas/*.schema.json`、`src/main/resources/data/**`、`tools/validate_content.py` |
| 历史 | `git log`、`git show afb5c08 -- README.md`、`git diff`、`git status` |

核心域（`program/Interpreter`、`entity/RocketBrain`、`platform/TickLedger`、`content/NbtTree`、`core/eval/Evaluate`）的实现质量高：纯函数边界清楚、预算与种子显式、注释解释“为什么”。本报告的问题集中在**契约一致性、半成品交付、门禁缺位**三层，而不是核心算法。

### 1.2 已执行的检查

1. `python3 tools/validate_content.py` → `32/32 files valid`，退出码 0。
2. 用同一套 schema 做了 5 组夹具探测（`jsonschema 4.25.1` + `Draft202012Validator`）。Python 侧结果如下；“Java 解析器”列为按 `ProgramParser` 源码逐条判读的结论：

| 夹具 | Python/schema | Java `ProgramParser` | 说明 |
|---|---|---|---|
| `{"op":"burst","colour":1}` | 拒绝 | 拒绝（`checkKeys`，`ProgramParser.java:97-108`） | 未知键 |
| `{"op":"spawn","profile":"a:b","children":[0]}` | 拒绝 | 拒绝（`:143-146`） | 两种命名方式互斥 |
| `{"op":"wait","ticks":0}` | 拒绝 | 拒绝（`:121-124`） | wait 至少 1 tick |
| `{"op":"spawn","children":[0..64]}`（65 项） | 拒绝 | **接受**（`:175-188`，上限 128） | **契约漂移**，见 ENG-05 |
| `{"op":"spawn","children":[127]}` | 拒绝 | **接受**（slot 0..127） | **契约漂移**，见 ENG-05 |

3. `git status --porcelain`、`git diff --stat`、`wc -l`、`grep -rn`、`git show` 用于核对每一条证据的行号与语义。

### 1.3 未验证项（明确声明）

- **评审时未执行 `./gradlew build`**：当时只有 JDK 17（`openjdk 17.0.20.1`），而 CI 注释与 Loom 1.19 要求构建 JVM 为 **JDK 25**（`.github/workflows/build.yml:33-36`）。执行阶段已在 JDK 25 上跑通 `build` 与 `test`（见“执行状态”），本节其余未验证项仍成立。
- **未启动客户端或专用服务器**，未执行 Fabric GameTest；所有涉及世界状态、粒子、网络收发的结论均为静态阅读加 schema 探测。
- **未做性能实测**：ENG-15 的“每 tick 点数”“序列化次数”是按代码路径计数，不是毫秒级 benchmark。
- **未审计 Fabric API / Loom 兼容矩阵**：ENG-12 只锁定可复现性，不评估版本升级风险。
- **未在 JVM 内验证** ENG-16 的 `ByteTag`/`IntTag` 相等性与哈希差异；该结论来自 `AttributeBag.equals`/`hashCode` 的实现与 Minecraft 1.20.1 的 NBT 值语义，属于源码级结论。
- 行号以评审时的工作区为准（HEAD + 未提交改动）。B0 落盘后行号会漂移，复核时以符号名（类/方法）重新定位。

---

## 2. 结论

18 条问题可以归到三层，每层一句话：

- **spec（契约层）—— 同一份契约有多份真相，且没有任何自动检查拦住漂移。** 格式同时由 JSON Schema、Python 校验器、Java 解析器和 Javadoc 描述；作者向文档被删除；测试与 CI 不覆盖接缝。
  **IDs：ENG-01、ENG-05、ENG-06、ENG-07、ENG-11、ENG-12。**
- **intent（意图层）—— 已承诺的能力没有闭环交付。** “写好的蓝图”没有消费端；工作区里跨核心管线的半成品没有进入 CI。
  **IDs：ENG-02、ENG-13。**
- **impl（实现层）—— 信任边界、生命周期与结构存在欠账。** 网络读侧无上限、light block 无持久化清理、运行时全局可达、上帝类、编译期常量、每玩家重序列化、64-bit 身份、无界 pending。
  **IDs：ENG-03、ENG-04、ENG-08、ENG-09、ENG-10、ENG-14、ENG-15、ENG-16、ENG-17、ENG-18。**

优先级口径：**P0** = 行为错误或信任边界；**P1** = 契约、文档、可验证性；**P2** = 结构、工程化、性能。归属层口径：**intent** = 产品承诺与交付差距；**spec** = 文档/模式/测试/门禁与实现不一致；**impl** = 代码行为、边界、结构、性能。

---

## 3. 问题登记册

字段固定为：严重度、归属层、证据、影响、已锁定方向、建议标签/里程碑，外加一段**判定式**。所有“已锁定方向”都是决策，不是候选菜单；被否方案集中在第 6 章。

### ENG-01 · 重载丢弃解析失败的 profile，Javadoc 却承诺保留旧版

| 字段 | 内容 |
|---|---|
| 严重度 | P0 |
| 归属层 | spec |
| 证据 | `registry/DefinitionLoader.java:27-28`（“a broken file keeps the previous version”）、`:50-52`（catch 后只 WARN，不合并）；`program/ProgramParser.java:40`、`:224`（同一承诺） |
| 影响 | 数据包作者按注释预期“坏文件不影响线上”，实际重载后该 profile 直接消失；依赖它的火箭 `launch` 返回 `null`，故障表现从“内容没更新”变成“火箭不发射”，且只有一条 WARN。 |
| 已锁定方向 | 保留“整表替换 + 丢弃坏文件”，把三处 Javadoc/注释改成事实（丢弃并 WARN，消息带 `ResourceLocation`）；把 `load` 抽出可注入的纯入口 `load(Map<ResourceLocation, Tag>)`，让重载行为可单测。 |
| 标签 / 里程碑 | `p0` `area:content` · **B1** |

**判定式（可执行）**

```java
// 新增 DefinitionReloadTest：map = {ok: 合法, bad: 非法 program}
Definitions after = DefinitionLoader.load(Map.of(OK, validTag, BAD, invalidTag));
assertNull(after.get(BAD_ID));                 // 丢弃，而不是保留旧版
assertTrue(logCapture.contains(BAD_ID.toString())); // WARN 带 id
```

```bash
# 三处“保留旧版”的承诺必须消失（含 “previously loaded” 的变体）
! grep -rniE "keeps? the prev" src/main/java
```

### ENG-02 · written blueprint 没有消费端，`FormulaCost` 只被计算、从未被收取

| 字段 | 内容 |
|---|---|
| 严重度 | P0 |
| 归属层 | intent |
| 证据 | `recipe/ModRecipes.java:13-16`（声称回程“lives in the editor”）；`item/FireworkBlueprintItem.java:30-50`（唯一读取者是 tooltip）；`grep -rn BlueprintCraftingRecipe src` 无命中；`formula cost` 只在 `recipe/BlueprintWritingRecipe.java:79-83` 与 `command/FireworkCommand.java:207` 计算并写进 NBT，`FireworkEditorMenu` 没有任何 blueprint 分支 |
| 影响 | 玩家花材料写出一张蓝图后，只能看 tooltip；账单永远不扣，蓝图不可兑现。`FormulaCost` 的单测因此只证明“算得对”，不证明“收得走”。 |
| 已锁定方向 | 补真实消费端：`BlueprintCrafting.pay(bill, available)` 纯账单服务（足额才扣、原 map 不变），加 `BlueprintCraftingRecipe`：一份 written blueprint + 恰好其冻结账单 → `assemble` 出等值 `RocketSpec`，缺料/多料拒绝；注册为动态 `magic-firework:blueprint_crafting`。原计划的“编辑器动作”被否，因为 1.20.1 的 `assemble` 没有 level/server 句柄。 |
| 标签 / 里程碑 | `p0` `area:craft` · **B1** |

**判定式（可执行）**

```java
// 新增 BlueprintCraftingTest（CraftInventory 是测试替身）
CraftInventory full = CraftInventory.ofBill(written);
CraftResult ok = BlueprintCrafting.craft(written(sample), full);
assertTrue(sameSpec(ok.firework(), sample));       // 等值 RocketSpec
assertEquals(1, full.countOf(Items.PAPER));        // 账单被扣，不是只读CraftResult denied = BlueprintCrafting.craft(written(sample), CraftInventory.empty());
assertFalse(denied.success());
assertTrue(denied.blueprintKept());                // 拒绝且不消耗蓝图
```

### ENG-03 · 客户端协议读侧无长度上限，客户端也没有每 tick 点数预算

| 字段 | 内容 |
|---|---|
| 严重度 | P0 |
| 归属层 | impl |
| 证据 | `platform/CloudProtocol.java:54-57`（`readVarInt` 后直接 `new ArrayList<>(Math.max(0, count))`）、`:74-76`（flash 同样）；`network/FireworkNetworking.java:143-150` 直接转交；`FireworkLimits.java:65` 的 `maxPointsPerShow = 2048` 在写侧有效、读侧未用；`client/CloudPlayer.java:37-38` 只限制 64 条云，不限每条云的点数 |
| 影响 | 恶意或错版本的服务端用一个 varint 让客户端按 `Integer.MAX_VALUE` 预分配并读循环，轻则卡顿、重则 OOM；同一 tick 内多条云可把粒子发射量推到任意高。 |
| 已锁定方向 | 读侧按 `maxPointsPerShow` 与新增 `MAX_FLASHES` 校验，超限抛 `DecoderException` 且在任何大数组分配之前；客户端按 tick 累计发射点数，超过 `FireworkLimits.maxClientPointsPerTick = 4096` 即丢弃并计数（不排队）。 |
| 标签 / 里程碑 | `p0` `area:network` `area:client` · **B1** |

**判定式（可执行）**

```java
// CloudProtocolReadLimitTest
FriendlyByteBuf hostile = new FriendlyByteBuf(Unpooled.buffer());
hostile.writeVarInt(Integer.MAX_VALUE);
assertThrows(DecoderException.class, () -> CloudProtocol.read(hostile));
assertTimeoutPreemptively(Duration.ofMillis(100), () -> assertThrows(...));
// 边界仍可往返
assertDoesNotThrow(() -> CloudProtocol.read(roundTrip(points(FireworkLimits.maxPointsPerShow))));
```

```java
// PointBudgetTest：一 tick 到期 10_000 点
budget.beginTick();
int emitted = emitAll(duePoints(10_000), budget);
assertTrue(emitted <= FireworkLimits.maxClientPointsPerTick);
assertEquals(10_000 - emitted, budget.droppedThisTick());
```

### ENG-04 · `LightSources.clear()` 只清内存，light block 永久残留；放置记录不持久化

| 字段 | 内容 |
|---|---|
| 严重度 | P0 |
| 归属层 | impl |
| 证据 | `effect/LightSources.java:66-68`（`clear()` 只 `placed.clear()`）；`:36-37`（`setBlockAndUpdate` 后无任何落盘）；`platform/FireworkRuntime.java:91-95`（`close()` 只清集合）；全库 `grep -rn SavedData src/main/java` = 0 |
| 影响 | 正常停服时 `close()` 也只清记录、不删方块；崩溃/杀进程时连记录都没了。世界里的 `minecraft:light` 永久留下，且无法区分玩家放置的 light。 |
| 已锁定方向 | 放置记录落 `SavedData`（维度 → 位置 + 到期 tick）；服务端启动/维度加载时清理记录内仍是 `Blocks.LIGHT` 的方块；`close()` 主动回收；崩溃路径由下一次启动的清理覆盖。清理前校验 `state.is(Blocks.LIGHT)`，避免误删已被替换的方块。 |
| 标签 / 里程碑 | `p0` `area:world` · **B1 单测 + B3 冒烟** |

**判定式（可执行）**

```java
// LightLedgerTest：SavedData 往返
LightLedger saved = LightLedger.fromTag(ledger.save(new CompoundTag()));
assertEquals(placedPositions, saved.positions(DIMENSION));
// 启动清理：记录里有位置，world 中仍是 LIGHT → 被移除
assertEquals(0, cleanupOnStart(fakeAccessWithLights(placedPositions)));
```

```text
# B3 冒烟（专用服务器/GameTest）
放置 light → kill -9 服务端 → 重启 → 统计该维度 Blocks.LIGHT 数量
断言 = 0（记录中的位置全部被清）
```

### ENG-05 · 格式有三份真相：schema、Python 校验器、Java 解析器；注释还引用已删除的类

| 字段 | 内容 |
|---|---|
| 严重度 | P1 |
| 归属层 | spec |
| 证据 | `tools/validate_content.py:30`（“Keep in sync with ContentStore / DefinitionStore”，两个类已不存在）；`schemas/firework.schema.json:185-194`（`children` `maxItems: 64`、slot `maximum: 63`）；`config/FireworkLimits.java:50`（`maxNestingNodes = 128`）；`program/ProgramParser.java:43`（“Kept in step with the schema”）、`:175-188`（允许 128 项 / slot 0..127）；`content/ContentSchemaTest.java` 只校验 `$schema` 声明与 schema 文件格式，不做语义对拍 |
| 影响 | 同一份 `spawn.children` 在两端口径不同：schema 拒绝 65 项或 slot 127，Java 接受。作者按 schema 写内容会在运行时得到不同结果；反之，Java 能加载的内容过不了 CI。 |
| 已锁定方向 | 以 `schemas/` 为规范真源：先把 schema 的 `children` 上界修正为与 `FireworkLimits.maxNestingNodes` 一致（128 项、slot 0..127），再新增 `SchemaParityTest` 读取 schema JSON，断言 `maxItems == maxNestingNodes`、`items.maximum == maxNestingNodes - 1`，任何一侧改动都会让 CI 红；删除 `validate_content.py:30` 的陈旧注释；新增 `tools/fixtures/{valid,invalid}/fireworks/` 共享夹具，Python 与 Java 测试跑同一批文件。 |
| 标签 / 里程碑 | `p1` `area:content` `area:docs` · **B2** |

**判定式（可执行）**

```bash
# 3 个坏夹具：两侧都必须拒绝（当前 schema 侧已满足，Java 侧补测试）
# 夹具放在名为 fireworks 的目录下，validate_content.py 才能按目录选 schema
for f in unknown-op-key spawn-both wait-zero; do
  ! python3 tools/validate_content.py "tools/fixtures/invalid/fireworks/$f.json"
done
```

```java
// SchemaParityTest：同一批坏夹具 + 边界夹具
assertThrows(ProgramException.class, () -> parse(fixture("invalid/fireworks/unknown-op-key.json")));
assertEquals(128, schemaChildrenMaxItems());   // 与 FireworkLimits.maxNestingNodes 一致
assertDoesNotThrow(() -> parse(fixture("valid/fireworks/spawn-128-children.json")));
```

```bash
! grep -rn "ContentStore\|DefinitionStore" tools src
```

### ENG-06 · 唯一的作者向文档（README `## Programs`，63 行）被 `afb5c08` 删除，语义没有契约

| 字段 | 内容 |
|---|---|
| 严重度 | P1 |
| 归属层 | spec |
| 证据 | `git show afb5c08 -- README.md`：该提交删除 63 行（diff 统计为 `63 ---------`），提交信息为“the content format is documented by the JSON Schemas and the code, not duplicated in the README”；当前 `README.md:1-27` 无 `## Programs`，也无 `docs/` 目录 |
| 影响 | 8 个 op 的语义、`flight.ticks` 与 `wait` 的关系、方向属性、`v² / 0.16` 的高度、碰撞结束 wait 只存在于代码与 schema 的描述文字里；作者无法从入口文档学到行为，schema 只能表达结构。 |
| 已锁定方向 | 新增 `docs/content-format.md` 作为语义契约（英文，与 README/代码一致；不复述 schema 的结构约束）；新增 `tools/check_docs.py` 做 token 断言（接受可选路径参数，默认 `docs/content-format.md`），并作为 CI 步骤紧跟 `validate_content.py`。 |
| 标签 / 里程碑 | `p1` `area:docs` · **B2** |

**判定式（可执行）**

```bash
python3 tools/check_docs.py   # 断言以下 token 全部出现在 docs/content-format.md
# 8 个 op：launch wait burst spawn call sound light end
# flight.ticks、flight.direction.yaw、flight.direction.pitch
# 高度公式：v² / 0.16
# 碰撞语义：Hitting a block ends the current wait
```

```bash
# 删除任一 token 的临时副本必须让脚本非 0
cp docs/content-format.md /tmp/doc && sed -i 's/v² \/ 0.16//' /tmp/doc
! python3 tools/check_docs.py /tmp/doc
```

### ENG-07 · 7 个接缝零测试；`ContentLibrary` 有测试但不覆盖原子写/时钟

| 字段 | 内容 |
|---|---|
| 严重度 | P1 |
| 归属层 | spec |
| 证据 | 对 `LightSources`、`ScheduledEffects`、`CloudPlayer`、`FireworkCommand`、`BlueprintWritingRecipe`、`FireworkEditorScreen`、`MagicFireworkEntity` 逐个 `grep -rln`，`src/test` 均无命中；`src/test` 共 25 个文件/158 个 `@Test`；`ContentLibraryTest` 存在，但只覆盖 save/load 快乐路径（`:32-43`） |
| 影响 | B1 的 4 条 P0 修复没有落点；`LightSources`/`ScheduledEffects` 的生命周期、`CloudPlayer` 的预算、编辑器布局数学、命令权限、配方组装都无法在不启动游戏的情况下回归。 |
| 已锁定方向 | 先抽纯逻辑再接 Minecraft 类型：`LightSourcesTest`、`ScheduledEffectsTest`、`CloudCursorTest`（从 `CloudPlayer` 抽出播放游标）、`EditorLayoutTest`（从 `FireworkEditorScreen` 抽出布局/步进判定）、`MagicFireworkLaunchTest`（用 fake runtime）、`FireworkCommandTest`（权限/参数解析）、`BlueprintCraftingTest`；每个接缝至少 1 个测试文件，覆盖率基线由 ENG-11 记录。 |
| 标签 / 里程碑 | `p1` `area:test` · **B0（先补 3 条）+ B1/B4（其余）** |

**判定式（可执行）**

```bash
for t in LightSourcesTest ScheduledEffectsTest CloudCursorTest EditorLayoutTest \
         MagicFireworkLaunchTest FireworkCommandTest BlueprintCraftingTest; do
  find src/test -name "$t.java" | grep -q . || exit 1
done
./gradlew test   # 7 个类必须被执行（CI 日志可见）
```

### ENG-08 · `FireworkEditorScreen` 815 行上帝类；4 空格缩进与全库 tab 不一致

| 字段 | 内容 |
|---|---|
| 严重度 | P2 |
| 归属层 | impl |
| 证据 | `client/.../screen/FireworkEditorScreen.java`：815 行、62 个方法、28 个 `private static final int` 布局/交互常量、4 对 `canIncrease*/canDecrease*`（`:613-646`）；该文件 4 空格缩进行 211 行、tab 缩进行 0 行，而 `entity/MagicFireworkEntity.java` 等全库文件使用 tab |
| 影响 | 布局、输入、网络动作、渲染混在一个类里；格式不一致让 diff 噪声大、review 成本高；步进判定重复 8 份，改一处规则要改 8 个方法。 |
| 已锁定方向 | 拆出 `EditorLayout`（常量与命中测试）、`StepperRow`（把 4 对 `can*` 合并为一张 `(attr, min, max, step)` 判定表）、`DirectionDial`（角度绘制/命中）；文件 ≤400 行；全库统一 tab（`.editorconfig` + Spotless，见 ENG-11）。 |
| 标签 / 里程碑 | `p2` `area:client` · **B4** |

**判定式（可执行）**

```bash
test "$(wc -l < src/client/java/com/magic/firework/client/screen/FireworkEditorScreen.java)" -le 400
test "$(grep -cE 'private boolean can(Increase|Decrease)' \
  src/client/java/com/magic/firework/client/screen/FireworkEditorScreen.java)" -eq 0
./gradlew spotlessCheck
```

### ENG-09 · `content/` 与 `registry/` 概念重复：两个 loader、两套 snapshot

| 字段 | 内容 |
|---|---|
| 严重度 | P2 |
| 归属层 | impl |
| 证据 | `content/ContentLoader.java`（74 行，inks/shows）与 `registry/DefinitionLoader.java`（84 行，fireworks）各自实现“列资源 → 裁前缀 → 解析 → 逐文件失败降级”；`content/ContentSnapshot.java` 与 `registry/Definitions.java` 是两套不可变快照；`DataPackTest` 又手写了一遍读文件+解析 |
| 影响 | 前缀裁剪、坏文件降级、日志格式三处各写一遍；加一种内容要改两个 loader；测试夹具无法复用。 |
| 已锁定方向 | 抽 `JsonResourceLoader`：`load(manager, directory, idPrefix, mapper)` 负责列资源、裁前缀、逐文件 try/catch + WARN；两个 loader 只保留各自的 `mapper`；`DataPackTest` 复用同一个 helper。 |
| 标签 / 里程碑 | `p2` `area:content` · **B4** |

**判定式（可执行）**

```bash
test $(( $(wc -l < src/main/java/com/magic/firework/content/ContentLoader.java) \
        + $(wc -l < src/main/java/com/magic/firework/registry/DefinitionLoader.java) )) -le 110
./gradlew test --tests '*DataPackTest'   # 行为不变
```

### ENG-10 · `RuntimeRegistry.current()` 深调用；entity 层直接取全局

| 字段 | 内容 |
|---|---|
| 严重度 | P2 |
| 归属层 | impl |
| 证据 | `platform/RuntimeRegistry.java:31-33`；调用点：`entity/MagicFireworkEntity.java:69,129,270`、`command/FireworkCommand.java:120`、`screen/FireworkEditorMenu.java:99`、`recipe/BlueprintWritingRecipe.java:74`、`item/MagicFireworkItem.java:60`、`network/FireworkNetworking.java:61,79` |
| 影响 | `launch` 必须存在一个运行中的服务器才能测；entity/命令/菜单/配方都隐式依赖单例，测试只能起服务器，接缝测试因此被跳过。 |
| 已锁定方向 | 运行时由平台边界注入：`MagicFireworkEntity.launch(..., FireworkRuntime runtime)`、命令/菜单/配方从调用方拿 runtime；`RuntimeRegistry.current()` 只保留在 `MagicFirework` 与 `FireworkNetworking` 这两个平台入口。 |
| 标签 / 里程碑 | `p2` `area:platform` · **B4** |

**判定式（可执行）**

```bash
! grep -rn 'RuntimeRegistry.current()' \
  src/main/java/com/magic/firework/entity \
  src/main/java/com/magic/firework/command \
  src/main/java/com/magic/firework/screen \
  src/main/java/com/magic/firework/recipe \
  src/main/java/com/magic/firework/item
```

```java
// MagicFireworkLaunchTest：fake runtime 构造 launch 解析，不起服务器
LaunchPlan plan = MagicFireworkEntity.resolveLaunch(fakeRuntime(definitions), spec, null);
assertEquals(profileId, plan.profileId());
```

### ENG-11 · 无格式/lint/覆盖率门禁；`./gradlew build` 只到编译 + 单测

| 字段 | 内容 |
|---|---|
| 严重度 | P2 |
| 归属层 | spec |
| 证据 | `build.gradle:1-91` 无 Spotless/ErrorProne/JaCoCo/Checkstyle；`.github/workflows/build.yml:21-55` 只有一个 `build` job（Python 校验 + `./gradlew build`），无格式、覆盖率、GameTest、服务器启停；`gradle.properties` 只有内存/并行/配置缓存 |
| 影响 | 格式漂移（ENG-08）、覆盖率下降（ENG-07）、light block 泄漏（ENG-04）都不会让流水线变红；`build` 绿灯被误读为“可交付”。 |
| 已锁定方向 | 引入 `.editorconfig`（tab、LF、去尾随空格）+ Spotless（Eclipse formatter profile，tab、120 列）+ ErrorProne（只开 `-Werror` 的默认检查集）+ JaCoCo（阈值 = 当前 baseline 向下取整，每批 +5%）；CI 改跑 `./gradlew check`，并加 `gametest`/`runServer` 冒烟 job（专用服务器启停 + Fabric GameTest 世界断言）。 |
| 标签 / 里程碑 | `p2` `area:build` · **B3** |

**判定式（可执行）**

```text
在三条分支上各注入一种故障，CI 必须分别变红：
1) 在任意 .java 末尾加两个空格                 → spotlessCheck 失败
2) 删除一个已有测试（覆盖率下降）              → jacocoTestCoverageVerification 失败
3) 关掉 LightSources 的启动清理               → gametest/runServer 冒烟的残留断言失败
```

```bash
./gradlew check   # 本地/CI 均以它为准，而不是 build
```

### ENG-12 · 构建不可复现：`loom_version=1.19-SNAPSHOT`、无依赖锁定/校验、版本写死

| 字段 | 内容 |
|---|---|
| 严重度 | P2 |
| 归属层 | spec |
| 证据 | `gradle.properties:10`（`loom_version=1.19-SNAPSHOT`）、`:13`（`version=1.0.0`）；`find . -name gradle.lockfile -o -name verification-metadata.xml` 无结果；`build.gradle:2` 用 `${loom_version}`；无 CHANGELOG/发布流程 |
| 影响 | 同一 commit 在不同日期可能解析到不同 Loom，构建结果不可复现；依赖被替换无法发现；发布版本与 tag 无关。 |
| 已锁定方向 | Loom pin 到具体 release（不以 `-SNAPSHOT` 结尾）；启用 `dependencyLocking` 并提交 `gradle.lockfile`；提交 `gradle/verification-metadata.xml`；`version` 由 tag 驱动（CI `-Pmod_version=${GITHUB_REF_NAME}`，本地默认 `0.0.0-dev`）；新增 `CHANGELOG.md`，发布时由 tag 生成条目。 |
| 标签 / 里程碑 | `p2` `area:build` · **B3** |

**判定式（可执行）**

```bash
! grep -q 'SNAPSHOT' gradle.properties
./gradlew build --offline          # 已缓存环境必须通过
./gradlew dependencies --write-locks && git diff --exit-code   # 锁文件稳定
test -f gradle.lockfile && test -f gradle/verification-metadata.xml
```

### ENG-13 · 工作区半成品：14 改 + 9 未跟踪，横跨核心管线；CI 跑不到新特性

| 字段 | 内容 |
|---|---|
| 严重度 | P1 |
| 归属层 | intent |
| 证据 | `git status --porcelain` = 14 M + 9 ??；`git diff --stat` = 14 files, +458/−59；未跟踪的 `client/particle/`、`core/show/Spark.java`、`registry/ModParticles.java`、`data/**/vanilla_large_ball.json`、`content/VanillaFireworkShowTest.java`；CI 只构建已跟踪内容 |
| 影响 | 新粒子/大球 show 只存在于本机；`VanillaFireworkShowTest` 与 `CloudProtocolTest` 的新往返用例不会在 CI 执行；“本地绿、CI 绿、发布缺功能”三者不一致。 |
| 已锁定方向 | B0 先落盘，≤3 个原子提交：<br>1. `feat(show): bake vanilla sparks and flashes` —— `Spark/Stamp/Show/Evaluate/FrameStream/Rng/TagCodec/CloudProtocol/FireworkNetworking` + `show.schema.json` + 相关测试；**不含** `data/` 改动（否则 `vanilla_ball.json` 会引用尚未提交的 ink）。<br>2. `feat(client): replay sparks with a vanilla firework particle` —— `FireworkSparkParticle`、`CloudPlayer`、`MagicFireworkClient`、`ModParticles`、`MagicFirework` 注册、`assets/.../particles/`。<br>3. `content(vanilla): add the small and large ball shows` —— 修改后的 `shows/vanilla_ball.json`、新增 inks/shows/fireworks、`VanillaFireworkShowTest`。<br>同时补 `CloudProtocol` 读侧上限测试（ENG-03）。 |
| 标签 / 里程碑 | `p1` `area:build` · **B0** |

**判定式（可执行）**

```bash
test -z "$(git status --porcelain)"                 # 工作区干净
# 3 条提交信息全部匹配 Conventional Commits
test "$(git log --format=%s -3 | grep -cE '^(feat|content)\(.+\): ')" -eq 3
# 在 JDK 25 环境逐个提交构建（临时 worktree，不移动当前分支）
for rev in $(git rev-list --reverse HEAD~3..HEAD); do
  git worktree add --detach "/tmp/fw-$rev" "$rev" &&
    (cd "/tmp/fw-$rev" && ./gradlew build) || exit 1
  git worktree remove --force "/tmp/fw-$rev"
done
```

### ENG-14 · 限制全是编译期常量，运维无法调

| 字段 | 内容 |
|---|---|
| 严重度 | P2 |
| 归属层 | impl |
| 证据 | `config/FireworkLimits.java:3`（“wrap them in a config later”）、`:16`（`maxChildren`）、`:29`（`maxSpeed`）、`:50`（`maxNestingNodes`）、`:63`（`allowLightBlocks`）、`:65-67`（点数预算） |
| 影响 | 服务器想关 light、收紧子火箭或点数预算只能改源码重编译；不同整合包只能各自 fork。 |
| 已锁定方向 | 新增 `config/magic-firework.json`（服务端），字段与现值同名同默认；`FireworkLimits` 从静态常量改为 `FireworkConfig` 的默认值来源，启动时读取；文件缺失 = 全默认，不写文件、不改变今天的行为。 |
| 标签 / 里程碑 | `p2` `area:config` · **B4** |

**判定式（可执行）**

```java
// FireworkConfigTest
assertEquals(FireworkConfig.defaults(), FireworkConfig.load(missingPath()));
assertEquals(1, FireworkConfig.load(pathWith("maxChildren", 1)).maxChildren());
```

```java
// 运行时：maxChildren=1 时，一次 spawn 2 个只生成 1 个；allowLightBlocks=false 时 Blocks.LIGHT 计数为 0
```

### ENG-15 · 每玩家单独重序列化整场；预算不足的截断 plan 被缓存（客户端峰值见 ENG-03）

| 字段 | 内容 |
|---|---|
| 严重度 | P2 |
| 归属层 | impl |
| 证据 | `platform/ShowService.java:75-82` 对每个 `ServerPlayer` 调 `sendCloud`；`network/FireworkNetworking.java:126-131` 每次调用都 `CloudProtocol.write`；`ShowService.java:60-64` 用可能不足的 `points` 烘焙、`:115-123` 无条件 `plans.put`；`core/eval/Plan.java:15-27` 没有截断标记；`FireworkLimits.java:67` 的服务端预算不约束客户端 |
| 影响 | 8 名玩家 = 同一场被序列化 8 次（CPU + 分配峰值）；预算不足时烘焙出的“半场”被当作完整结果缓存，重放时静默缺段且无日志；客户端没有点数上限（见 ENG-03）。 |
| 已锁定方向 | 一次编码、多播：把 `CloudProtocol.write` 的结果固化为 `byte[]`，每个玩家用 `new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes))` 发送，序列化只发生一次；`Plan` 增加 `truncated`（由 `Meter` 耗尽/`MAX_STEPS_PER_SHOW` 触发），`ShowService` 只缓存 `!truncated` 的 plan，并在截断时 WARN 带 show id；客户端预算归 ENG-03 的 `PointBudget`，本项只做交叉断言。 |
| 标签 / 里程碑 | `p2` `area:network` `area:client` · **B4** |

**判定式（可执行）**

```java
// CloudBroadcastTest：8 个 fake recipient
int encodes = broadcast(origin, stream, flicker, eightRecipients());
assertEquals(1, encodes);                       // 序列化一次
```

```java
// ShowPlanCacheTest：预算不足
Plan truncated = evaluateWithBudget(show, 1);
assertTrue(truncated.truncated());
service.burst(...);                              // 走截断路径
assertEquals(0, cache.size());                   // 不缓存
```

### ENG-16 · 内容身份用 64-bit FNV-1a；JSON 布尔→`IntTag` 与 `withBoolean`→`ByteTag` 不一致

| 字段 | 内容 |
|---|---|
| 严重度 | P2 |
| 归属层 | impl |
| 证据 | `content/NbtTree.java:37-52`（64-bit FNV-1a）；`content/ContentRef.java:55-63`（`equals`/`hashCode` 只看 fingerprint）；`platform/ContentService.java:32-38`（inline cache 以 fingerprint 为键）；`content/JsonToNbt.java:79-80`（JSON boolean → `IntTag(1/0)`）；`attribute/AttributeBag.java:118-121`（`withBoolean` → `putBoolean`/`ByteTag`）、`:150-157`（`tag.equals`/`tag.hashCode`） |
| 影响 | 语义相同的文档（一个来自 JSON `true`，一个来自 `withBoolean(true)`）`AttributeBag.equals` 为 false、hash 不同；inline 缓存以 64-bit 指纹作唯一身份，构造碰撞即可命中错误内容。 |
| 已锁定方向 | 布尔统一表示：`AttributeBag.load` 对布尔属性键把 `0/1` 的 `Byte/Short/Int/Long` 规范化为 `ByteTag`（覆盖旧 item NBT）；`Inline` 身份改为 128-bit 摘要（两路不同 offset 的 FNV-1a，拼成 `long[2]`），摘要相等时再比对 `document`，缓存键使用 128-bit 值。 |
| 标签 / 里程碑 | `p2` `area:content` · **B4** |

**判定式（可执行）**

```java
// AttributeBagBooleanTest
AttributeBag fromJson = AttributeBag.load(JsonToNbt.convert(json("{\"flicker\":true}")));
AttributeBag fromApi = AttributeBag.EMPTY.withBoolean("flicker", true);
assertEquals(fromApi, fromJson);
assertEquals(fromApi.hashCode(), fromJson.hashCode());
```

```java
// InlineIdentityTest：注入恒定 64-bit 指纹的 Fingerprint 策略
ContentRef.Inline a = inline(docA, collidingFingerprint());
ContentRef.Inline b = inline(docB, collidingFingerprint());
assertNotEquals(a, b);                          // 摘要相同也必须比对 document
assertNotEquals(service.show(a).id(), service.show(b).id()); // 两个文档各自解析，不串缓存
```

### ENG-17 · `ContentLibrary` 非原子写、时钟不可注入

| 字段 | 内容 |
|---|---|
| 严重度 | P0 |
| 归属层 | impl |
| 证据 | `content/ContentLibrary.java:132`（`System.currentTimeMillis()`）；`:169-176`（`NbtIo.write` 直接写目标文件）；`ContentLibraryTest.java:32-55` 只覆盖正常 save/load 与 timestamp 保持不变 |
| 影响 | 写入中途崩溃/磁盘满会留下半个 `.nbt`，下次 `load()` 要么抛异常中断整个库加载（`:75`），要么把半条目当有效内容；`created` 依赖系统时钟，测试无法固定。 |
| 已锁定方向 | 同目录临时文件（`<name>.nbt.tmp`）+ `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`；写入失败删除临时文件，目标保持旧内容；`Clock` 由构造注入（默认 `Clock.systemUTC()`），`created` 只从注入时钟取。 |
| 标签 / 里程碑 | `p0` `area:content` · **B1** |

**判定式（可执行）**

```java
// ContentLibraryAtomicTest
ContentLibrary lib = new ContentLibrary(dir, 32, Clock.fixed(INSTANT, UTC));
Entry e = lib.save(id, doc, AUTHOR);
assertEquals(INSTANT.toEpochMilli(), e.created());

// 注入在 write 中途抛 IOException 的 writer：目标不存在/仍是旧内容
assertThrows(IOException.class, () -> failingLib.save(id, doc2, AUTHOR));
assertFalse(Files.exists(dir.resolve("x.nbt.tmp")));
ContentLibrary reopened = new ContentLibrary(dir);
reopened.load();
assertEquals(1, reopened.size());
assertEquals(1, reopened.get(id).orElseThrow().document().getInt("marker"));
```

### ENG-18 · `ScheduledEffects.pending` 无上界且持有 `ServerLevel`；`LightSources` 同样持有维度引用

| 字段 | 内容 |
|---|---|
| 严重度 | P2 |
| 归属层 | impl |
| 证据 | `platform/ScheduledEffects.java:26`（无界 `ArrayList`）、`:32-39`（每次 burst 追加，无上限）、`:63`（`Pending` record 持有 `ServerLevel`）；`effect/LightSources.java:23`（`IdentityHashMap<ServerLevel, ...>`）；全库无 `ServerLevelEvents.UNLOAD` / 维度卸载处理 |
| 影响 | 大量长时长 show 可让 pending 线性增长；维度卸载后条目仍持有 level，直到到期或停服，阻止卸载对象被回收；恶意内容可放大内存。 |
| 已锁定方向 | `pending` 硬上限 `MAX_PENDING = 1024`，超出丢弃并计数（不排队）；队列元素改存 `(dimension, at, event, origin)`，不直接持有 `ServerLevel`；监听维度卸载，按 key 清空该维度全部条目；`LightSources.placed` 同样按维度 key 清理。 |
| 标签 / 里程碑 | `p2` `area:platform` · **B4** |

**判定式（可执行）**

```java
// ScheduledEffectsLimitTest
for (int i = 0; i < 1025; i++) queue.schedule(DIM, origin, oneEvent(), now);
assertEquals(1024, queue.size());
assertEquals(1, queue.dropped());
queue.onDimensionUnload(DIM);
assertEquals(0, queue.size());
```

```text
# B3 冒烟/GameTest：维度卸载后
assert scheduled.size() == 0;
assert lightSources.placedCount(DIM) == 0;
```

---

## 4. 治理路线图

五批，顺序执行；每批都有进入条件、退出条件（可执行）和依赖。批次不是按严重度排序，而是按“先让测试可见，再修正确性，再上闸门，最后动结构”。

### B0 · 工作区落盘与测试可见性

- **范围**：ENG-13；首批 3 条可见测试：`CloudProtocol` 读侧上限（与校验同提交）、`FrameStream` 的 spark/flash 覆盖、`VanillaFireworkShowTest` 入库。
- **进入条件**：当前工作区（14 M + 9 ??）为唯一代码状态；`VanillaFireworkShowTest` 已写好但未跟踪。
- **动作**：按 ENG-13 的 3 个原子提交落盘；把已写好的 `CloudProtocol` spark/flash 往返测试一并提交，并在 `FrameStreamTest` 补 spark 字段保留 + flash 时间排序；新增读侧上限测试并在同一次提交里落地 `CloudProtocol.read` 校验（ENG-03 的读侧部分）；把 `VanillaFireworkShowTest` 随数据提交。
- **退出条件**：
  - `git status --porcelain` 为空；
  - `git log --format=%s -3` 为 3 条 Conventional Commits；
  - 在 JDK 25 环境（或 CI）对 3 个提交逐个执行 `./gradlew build` 均通过；
  - CI 日志能看到 `VanillaFireworkShowTest` 与 `CloudProtocolReadLimitTest`。
- **依赖**：无。评审时 JDK 17 的阻塞已由 JDK 25 解除。

### B1 · 正确性与信任边界

- **范围**：ENG-01、ENG-02、ENG-03（客户端预算；读侧已在 B0 落地）、ENG-04（单测部分）、ENG-17。
- **进入条件**：B0 完成，测试已进入 CI。
- **动作**：修 Javadoc + 抽 `DefinitionLoader.load(Map<..., Tag>)`；`BlueprintCrafting.matchesBill` 抽成配方的纯判定；`PointBudget`；`LightLedger` + 启动清理 + `close()` 回收；`ContentLibrary` 临时文件 + `Clock`。
- **退出条件**：ENG-01/02/03/04（单测）/17 的判定式全绿；`./gradlew check` 通过；无新增失败测试。ENG-02 的 ItemStack 网格走查需要真实 item 注册表（`Bootstrap` 先冻结，`ModRegistries` 无法在 JUnit JVM 注册），因此只把配方的决策抽成 `BlueprintCrafting.matchesBill` 做纯接缝测试，网格收集计数仍由运行期覆盖。
- **依赖**：B0（测试可见）。ENG-04 的端到端（杀进程/重启、世界内 light 计数）在 B3 冒烟里完成，本批只保证持久化与清理调用存在且可单测。

### B2 · 契约与文档

- **范围**：ENG-05、ENG-06。
- **进入条件**：B0 完成；`tools/validate_content.py` 可在 CI 跑（现状已满足）。
- **动作**：schema `children` 上界与 `FireworkLimits.maxNestingNodes` 对齐；`SchemaParityTest`；共享夹具；删除 `ContentStore/DefinitionStore` 注释；`docs/content-format.md` + `tools/check_docs.py` + CI 步骤。
- **退出条件**：3 个坏夹具双端拒绝；128 项边界双端一致；`check_docs.py` 在 CI 必跑且能因缺失 token 变红；`grep ContentStore|DefinitionStore` 无命中。
- **依赖**：与 B1 无代码依赖，可与 B1 并行；B2 在 B3 之前合入，避免与 CI 步骤的文件级冲突。

### B3 · 门禁与可复现（含运行期验证）

- **范围**：ENG-11、ENG-12；并在此批完成 ENG-04/ENG-18 的端到端冒烟。
- **进入条件**：B0 完成（测试可见）；B1 的 `LightLedger` 与启动清理已合入（供冒烟断言）；B2 的 `check_docs.py` 已进 CI。
- **动作**：`.editorconfig` + Spotless + ErrorProne + JaCoCo 阈值；CI 改跑 `./gradlew check`；新增 `gametest` 与 `runServer` 冒烟 job（先写 `eula=true`，执行放置/清理/停服/重启断言）；Loom pin、依赖锁定、校验元数据、tag 驱动版本、CHANGELOG。
- **执行结果（部分）**：Spotless（ratchet 自 `9493d51`）、ErrorProne 2.36 + `-Werror`、JaCoCo 0.8.13（45% instruction floor）、依赖锁定 + SHA-256 校验（Loom 本地 remap 的 group 走 `trusted-artifacts`）、版本来自 `-Pmod_version`，提交 `d150e66`；CI 仍跑 `build`（内含 `check`）。`runServer` 到达 runtime start，但冒烟 job 与“重启后 light=0 / 卸载后 pending=0”两条断言未完成。
- **退出条件**：ENG-11 的三类注入故障分别让 CI 变红；`./gradlew build --offline` 在缓存环境通过；`gradle.lockfile` 与 `gradle/verification-metadata.xml` 入库；`gradle.properties` 无 `SNAPSHOT`；冒烟 job 断言“重启后 light 计数 = 0”“卸载维度后 pending/placed = 0”。**已满足前四项（Spotless 坏文件已实测变红；JaCoCo/ErrorProne 已接线）；冒烟两项未满足。**
- **依赖**：B0（测试可见）、B1（清理实现）、B2（文档检查进 CI）。ENG-12 与其余部分无依赖，可并行。

### B4 · 结构与性能

- **范围**：ENG-08、ENG-09、ENG-10、ENG-14、ENG-15、ENG-16、ENG-18。ENG-18 的队列上限、维度 key、卸载释放属于本批；B3 只做“重启后 light=0 / 卸载后条目=0”的冒烟断言。
- **进入条件**：B1、B3 完成；覆盖率基线与格式门禁已生效。
- **动作**：拆 `FireworkEditorScreen`；抽 `JsonResourceLoader`；运行时注入；`FireworkConfig`；一次编码多播 + `Plan.truncated` 不入缓存；布尔规范化 + 128-bit 身份；pending 上限与维度卸载释放。
- **执行结果**：ENG-08（`FireworkEditorScreen` 815→308 行；`EditorLayout`/`EditorSteps`/`EditorText`/`EditorChrome`/`DirectionPreview`/`EditorRenderer`，can* 方法 0）、ENG-09（`JsonResourceLoader`，两个 loader 32 + 78 行）、ENG-14（`config/magic-firework.json` + `FireworkConfig`）、ENG-15（一次编码 + `Plan.truncated` 不入缓存）、ENG-16（布尔规范化 + SHA-256 128-bit 身份）、ENG-18（pending 1024 + 维度 id + 卸载释放）完成。ENG-10：entity/command/screen/item/network 不再调用 `RuntimeRegistry.current()`，改为 server-scoped `of(server)` + `launch(runtime)`；`BlueprintWritingRecipe.assemble` 因签名没有 level/server 句柄保留 `current()`。ENG-07 补充：`LightSources` 抽出 `LightSurface` 后新增 `LightSourcesTest`（放置/到期/维度卸载/启动清理），另有 `TickLedgerTest`、`BlueprintCraftingRecipeTest` 与 `TagCodecBranchesTest`；`CloudCursorTest`/`EditorLayoutTest` 受 test 源集看不到 client 源集限制、`MagicFireworkLaunchTest`/`FireworkCommandTest` 需要活 level/命令分发器，仍未写。
- **退出条件**：ENG-08/09/10/14/15/16/18 的判定式全绿；`spotlessCheck` 通过；覆盖率不低于 B3 基线（每批 +5% 的目标在 B4 结束时仍成立）；`DataPackTest` 行为不变。**已满足：08/09/10（recipe 例外）/14/15/16/18；覆盖率 50.5%（B3 46.3%，B4 目标 50% 达成）；236 个测试、0 失败。**
- **依赖**：B3（Spotless/覆盖率/冒烟）、B1（先修正确性再重构）、B2（文档 token 不受重构影响）。

---

## 5. 判定式总表

逐条勾选用；每行的完整上下文见第 3 章同名条目。

| ID | 严重度 | 判定式（最小可执行断言） | 批次 |
|---|---|---|---|
| ENG-01 | P0 | `DefinitionLoader.load({ok,bad}).get(bad) == null` 且 WARN 含 id；`! grep -rniE "keeps? the prev" src/main/java` | B1 |
| ENG-02 | P0 | `BlueprintCrafting.pay` 足额扣料、缺料拒绝且不改输入；`BlueprintCrafting.matchesBill` 对 written+账单、缺料/多料/错分布拒绝；配方网格走查需真实 item 注册表，留运行期 | B1 |
| ENG-03 | P0 | `readVarInt(Integer.MAX_VALUE)` 抛 `DecoderException` 且 100ms 内返回；`PointBudget` 每 tick 发射 ≤ 4096 | B1 |
| ENG-04 | P0 | `LightLedger` 往返；`LightSources.recover` 移除记录内 `Blocks.LIGHT` 并清空账本（单测）；B3 冒烟重启后计数 = 0 | B1/B3 |
| ENG-05 | P1 | 3 个坏夹具双端拒绝；`children` 128 边界双端一致；`SchemaParityTest` 绿；无 `ContentStore/DefinitionStore` 引用 | B2 |
| ENG-06 | P1 | `check_docs.py` 断言 8 op + `flight.ticks` + 两个 direction + `v² / 0.16` + 碰撞语句；删除 token 即非 0 | B2 |
| ENG-07 | P1 | `LightSourcesTest`/`TickLedgerTest`/`BlueprintCraftingRecipeTest`/`ScheduledEffectsTest` 等已执行；`CloudCursorTest`/`EditorLayoutTest`/`MagicFireworkLaunchTest`/`FireworkCommandTest` 仍缺（client 源集/活 level） | B0/B1/B4 |
| ENG-08 | P2 | `wc -l` ≤ 400；`canIncrease/Decrease` 方法数 = 0；`spotlessCheck` 绿 | B4 |
| ENG-09 | P2 | 两个 loader 行数合计 ≤ 110；`DataPackTest` 不变 | B4 |
| ENG-10 | P2 | entity/command/screen/item/network 中 `RuntimeRegistry.current()` 命中数 = 0；recipe `assemble` 无 server 句柄，保留例外 | B4 |
| ENG-11 | P2 | 三类注入故障分别让 CI 红；CI 跑 `check` + 冒烟 job | B3 |
| ENG-12 | P2 | 无 `SNAPSHOT`；`build --offline` 通过；`gradle.lockfile` + `verification-metadata.xml` 入库；锁文件 diff 为空 | B3 |
| ENG-13 | P1 | `git status --porcelain` 为空；3 条提交各自 `build` 通过；提交信息匹配 Conventional Commits | B0 |
| ENG-14 | P2 | `load(missing) == defaults()`；`maxChildren=1` 只生成 1 个；`allowLightBlocks=false` 不放置 light | B4 |
| ENG-15 | P2 | 8 个接收者只编码 1 次；截断 plan 的 cache miss；客户端点数交叉断言 ≤ 4096 | B4 |
| ENG-16 | P2 | JSON boolean 与 `withBoolean` 的 bag 相等且 hash 相等；恒定指纹下不同 document 的 `Inline` 不相等 | B4 |
| ENG-17 | P0 | 注入固定 `Clock` 的 `created` 精确；写入抛错后目标不变、无 `.tmp`、`load()` 不产生半条目 | B1 |
| ENG-18 | P2 | 第 1025 条被丢弃且 `dropped()==1`；卸载维度后该维度条目 = 0 | B4 |

一致性自查：本章 18 行与第 3 章的 18 个 `### ENG-xx` 一一对应；无孤儿、无重复。

---

## 6. 假设与默认（ADR 式）

每条一行：选择 → 被否方案与理由。

1. **交付路径 = `docs/engineering-review.md` 单文件**；本轮不改源码、不改 CI。被否：直接开 issue / 直接改代码——用户要的是可跟踪的诊断报告。
2. **基线 = `28cc133` + 评审时工作区**；行号会随 B0 漂移，复核以符号名定位。被否：只按提交历史评审——会漏掉未跟踪的 `Spark`/`VanillaFireworkShowTest`。
3. **ENG-01 选“整表替换 + 丢弃 + WARN 带 id”**；被否：“保留旧版本”——需要跨 reload 的可变合并状态，与 `Definitions`/`ContentSnapshot` 的不可变快照语义冲突，且“旧版本”在首次加载时不存在。
4. **ENG-02 选“`BlueprintCrafting` 纯账单服务 + 动态 `BlueprintCraftingRecipe`”**；被否 A：“删除该特性”——已发布物品与 `BlueprintWritingRecipe` 会失去意义；被否 B：“编辑器按钮”——1.20.1 的 `assemble` 没有 level/server 句柄，编辑器动作还需要额外的 UI 入口；被否 C：保持 `ModRecipes` 的“回程不是配方”——特性仍然没有消费者。`ModRecipes` 的注释已改为两个方向都是配方。
5. **ENG-04 选“SavedData 持久化 + 启动清理 + close 回收”**；被否：“仅 close 时回收”——崩溃/杀进程路径覆盖不到，正是残留的主因。
6. **ENG-05 选“schema 为规范真源 + parity test 强制 Java 对齐”**；被否 A：“三份真相 + 人工同步”——漂移正是现状；被否 B：“从 schema 代码生成 Java 常量”——引入构建期代码生成，收益不抵复杂度。
7. **ENG-06 选“新增 `docs/content-format.md`（英文）”**；被否 A：“恢复 README 的 `## Programs`”——入口页会被 63 行语义淹没，且 README 已明确只留 requirements/build/license；被否 B：“只写注释”——没有任何检查能拦住再次删除。
8. **ENG-08 选“Eclipse formatter profile（tab、120 列）+ Spotless，ratchet 自 `9493d51`”**；被否：“google-java-format”——会把全库 tab 改成空格，产生 85 文件的纯格式 diff，破坏 blame。补充：一次性 Eclipse 全库格式化仍会改 30 个文件/约 2000 行，因此改为 ratchet；B4 触碰旧文件时再格式化。
9. **ENG-12 选“Loom pin 到实施时的最新 release + 依赖锁定 + 校验元数据 + tag 驱动版本”**；被否 A：继续 `-SNAPSHOT`——不可复现；被否 B：手写 `1.0.0`——发布版本与 tag 脱节。校验元数据对 Loom 本地 remap 的 `remapped.net.fabricmc.fabric-api` group 使用 `trusted-artifacts`（生成物没有稳定的发布校验和）；其余依赖仍校验 SHA-256。
10. **ENG-14 选 `config/magic-firework.json`，缺文件即默认、不写文件**；可调字段为 `maxChildren`、`maxDepth`、`maxSpeed`、`allowLightBlocks`、`maxPointsPerShow`、`maxPointsPerTick`，结构性上限保持编译期；被否 A：环境变量/系统属性——运维不可发现；被否 B：每世界配置——安全上限必须在世界加载前就生效。
11. **ENG-03 的客户端预算 = 4096 点/tick**（服务端全局 8192 的一半，已落地）；被否 A：直接复用 8192——客户端峰值会与服务器峰值叠加；被否 B：不设预算——信任边界不完整。ENG-15 只做同一常量的交叉断言，不另设第二个预算。
12. **ENG-18 的 pending 上限 = 1024 条**（已落地；队列存 dimension id，不存 `ServerLevel`）；被否 A：无界——内存可被长 show 放大；被否 B：只靠 TTL——到期前仍持有维度引用。
13. **覆盖率阈值 = 45% instruction（B2 基线 46.3%，B4 当前 50.5%），B4 目标 +5% 到 50% 已达成（236 tests）**；被否：一次性 80%——会让 CI 长期红，掩盖真正的回归。
14. **严重度口径**：P0 行为错误/信任边界；P1 契约/文档/可验证性；P2 结构/工程化/性能。被否：按“改动行数”排序——与用户风险无关。
15. **标签**：`p0/p1/p2` + `area:network|content|docs|build|client|world|craft|platform|test|config`；里程碑 B0–B4。被否：按文件路径打标签——跨层问题会被拆散。
16. **语言**：报告正文中文，代码标识符/路径/命令/新文档 `docs/content-format.md` 保持英文。被否：报告英文——团队沟通语言是中文；文档中文——与 README/代码注释不一致。
17. **ENG-16 选 SHA-256 前 128 位作为 Inline 摘要 + 布尔键规范化**；被否：继续 64-bit FNV——身份空间不足；把全部 0/1 数字都规范化——会破坏非布尔属性。
18. **ENG-15 选一次编码为 `byte[]` 后多播**；被否：每个玩家复制 `FriendlyByteBuf`——仍会重复序列化。
19. **ENG-10 选 server-scoped `of(server)` + `launch(runtime)`**；被否：继续用 `current()`——依赖单一全局状态；`BlueprintWritingRecipe.assemble` 保留 `current()`，因为 1.20.1 的配方签名没有 level/server 句柄。
20. **“无开放决策”**：第 3 章的“已锁定方向”即决策；被否方案只出现在本章，正文不保留未决选项。
