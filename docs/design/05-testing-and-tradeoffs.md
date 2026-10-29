# 05 测试策略与设计取舍

> 本篇回答两个问题：怎么验证实现是对的（测试策略与覆盖清单）、设计为什么长这样（取舍与已知风险）。
> 取舍素材池攒下了 59 行完整登记（含命令级细节与出处行号），本篇第 3 节为 42 行精选版。

## 1. 测试策略

三层：集成测试为主、单元测试为辅、人工对账兜底。

- **集成测试（主）**：按 spec 要求为每个命令编写集成测试（spec:665），用 `testing/tester.py` 跑 `.in` 脚本（Capers 同款格式，`python3 testing/tester.py --verbose <文件>`，调试加 `--keep` 保留临时目录以检查 `.gitlet` 内部状态）。自有用例一律放 `testing/student_tests/`（不动 samples），预置文件放 `testing/src/`。每个命令至少覆盖：全部正常行为、每条失败消息逐字、失败后的状态不变性。
- **单元测试（辅）**：spec 不提供也不强求（实现相关）。原计划只对不依赖命令流程的纯件写 JUnit（实现时暂缓，最终未落地）：`Utils.sha1` 的稳定性与类型标签区分（`"commit"`/`"blob"` 不同输入出不同 ID）、Commit / StagingArea 序列化往返后字段不变。实际由 54 个集成测试覆盖。
- **人工对账（兜底）**：错误消息逐字对 [04 错误处理](04-error-handling.md)清单（不只对 spec）；`commit` 后 `.gitlet` 增量 ≤ 本次 staged 文件总大小；危险命令失败后五项状态（working directory / staging area / `.gitlet` / branch pointer / HEAD）与运行前逐字节一致。
- **验收锚点**：skeleton 自带 samples 四个 checkpoint 测试（init / 基本 checkout / 基本 log / 回溯 checkout）必须先过。

高危区（优先写、反复跑）：`merge`（split point 两边界、决策表五分支、冲突格式含一方删除的空段、merge commit 双 parent 与消息逐字）；危险命令的 untracked 挡路（`rm`/`checkout`/`reset`/`merge`）；缩写 ID 的 0/1/多分流；远程命令的对象完整性与快进检查。

## 2. 测试覆盖清单

按命令列关键场景；实现阶段每写一个 `.in` 用例勾掉一项。

**入口管线（每个命令抽一条即可）**

- [ ] 空输入报 `Please enter a command.`；未知命令报 `No command with that name exists.`；操作数数量/格式错误报 `Incorrect operands.`；未初始化目录里执行非 `init` 命令报 `Not in an initialized Gitlet directory.`

**`init`**

- [x] 首次成功建 `.gitlet` 骨架并自动提交 initial commit
- [x] 重复 `init` 报 `A Gitlet version-control system already exists in the current directory.`

**`add`**

- [ ] 新增文件暂存成功（blob 落盘、ID 入 addition）
- [ ] 同内容重复 `add` 无任何效果（不写盘）
- [ ] 暂存后把文件改回原版，`add` 自动取消暂存
- [ ] 覆盖已暂存的版本（同键更新）
- [ ] `add` 已被 `rm` 标记删除的文件 → 取消删除标记
- [ ] 文件不存在报 `File does not exist.`

**`commit`**

- [ ] 正常提交：新映射 = 父映射 + addition − removal
- [ ] 空消息报 `Please enter a commit message.`
- [ ] 无暂存改动报 `No changes added to the commit.`
- [ ] 增删改混合一次提交
- [ ] 提交不清工作目录文件

**`rm`**

- [ ] 已暂存新增 → 移出暂存（工作文件保留）
- [ ] 已追踪 → 标 removal + 删工作文件
- [ ] 两者皆无报 `No reason to remove the file.`
- [ ] 删除后提交，文件从新 commit 映射除名

**`log` / `global-log` / `find`**

- [ ] `log` 沿 firstParent 回溯到 initial，格式逐字（含 `Date:` 与块后空行）
- [ ] merge commit 多打 `Merge: <7位> <7位>` 行
- [ ] `global-log` 打乱创建顺序仍全量打印
- [ ] `find` 单匹配 / 多匹配全打 / 无匹配报 `Found no commit with that message.`

**`status`**

- [ ] 五节齐全（空节也打标题）、节间空行数正确
- [ ] 四种 modified/deleted 组合的后缀正确（含“已删未暂存”）
- [ ] untracked 收录（含 `rm` 后工作目录复活副本）
- [ ] 当前分支带 `*`、分支按字典序

**`checkout`（三用法）**

- [ ] 用法一取当前版本 / 用法二取历史版本（完整 ID + 缩写 + 缩写歧义报 `No commit with that id exists.`）
- [ ] 文件不在该 commit 报 `File does not exist in that commit.`
- [ ] 用法三切换分支：写目标文件、删多余追踪文件、清 stage、HEAD 最后切
- [ ] 用法三各失败：分支不存在 / 是当前分支 / untracked 挡路报 `There is an untracked file in the way; delete it, or add and commit it first.`
- [ ] 用法三不碰 untracked 文件

**`branch` / `rm-branch`**

- [ ] 新建分支指向当前 commit / 重名报 `A branch with that name already exists.`
- [ ] 删除分支 / 不存在报 `A branch with that name does not exist.` / 删当前分支报 `Cannot remove the current branch.`

**`reset`**

- [ ] 恢复旧版本文件 + 移动当前分支指针 + 清 stage
- [ ] untracked 挡路报 / ID 不存在报 / 缩写解析可用

**`merge`（最高危）**

- [ ] split point == 被合并分支 head：打印 `Given branch is an ancestor of the current branch.`，零改动
- [ ] split point == 当前分支 head：fast-forward，打印 `Current branch fast-forwarded.`，HEAD 不动
- [ ] 决策表五分支各一例：given 改我未改 → 采纳+暂存；given 删我未改 → 删除+removal；同改/同删 → 不动；异改 → 冲突
- [ ] 冲突格式逐字（`<<<<<<< HEAD`/`=======`/`>>>>>>>`），含一方删除时的空段
- [ ] 冲突时 merge commit 照常生成、之后打印 `Encountered a merge conflict.`
- [ ] merge commit 有两个 parent、消息逐字 `Merged [given] into [current].`
- [ ] 零改动时报 `No changes added to the commit.`
- [ ] 失败：暂存非空报 `You have uncommitted changes.` / 分支不存在 / 自合并 / untracked 挡路

**远程命令（加分项）**

- [ ] `add-remote` 新增 + 重名报 `A remote with that name already exists.`；首次执行创建 remotes 文件
- [ ] `rm-remote` 删除 + 名不存在报；删后可重加
- [ ] `fetch`：远程目录不存在与 remote 名未注册都报 `Remote directory not found.`；远程无此分支报 `That remote does not have that branch.`；首次建 `refs/remotes/<r>/<b>`；再 fetch 无新内容时不写对象；钻石历史复制完整
- [ ] `push`：快进成功（远程缺失对象补齐、指针前移）/ 非快进报 `Please pull down remote changes before pushing.` / 远程无此分支则新建
- [ ] `pull`：成功路径 = fetch + merge；merge 段失败时 fetch 成果保留

**通用回归**

- [ ] 失败原子性：每个失败场景执行后，五项状态与执行前逐字节一致
- [ ] `commit` 后 `.gitlet` 增量不超过本次 staged 文件总大小

## 3. 设计取舍

精选版（四列：决策 / 备选 / 理由 / 代价）。

**架构与对象模型**

| 决策 | 备选（被否） | 理由 | 代价 |
|---|---|---|---|
| Blob 不成 Java 类，内存中仅 `byte[]` | 为 blob 建类（带文件名/路径字段） | blob 只是文件内容；内容寻址只需字节，文件名归属由 commit 快照维护 | blob 无独立行为，操作全是 Utils 字节读写 |
| StagingArea 独立成类（addition Map + removal Set） | 塞进 Repository 静态字段 / 并入 Commit | 需保管两类条目并执行暂存规则；成类可整体序列化、init 直接 new 空对象 | 多一个类与一个 stage 文件 |
| Main 极薄，命令逻辑/入口检查/分发全在 Repository，入口 `Repository.run(args)` | Main 写分发 / 命令各成类 | 集中协调、依赖单向 | Repository 偏大 |
| Commit 存完整快照、构造后 immutable | 存相对父提交的 diff | checkout/reset 取一个 commit 即可还原，无需沿历史回放 | 映射条目每 commit 重存（blob 字节按内容寻址共享，代价仅限映射表） |
| HEAD 只存分支名字符串（无 detached HEAD） | HEAD 直指 commit ID | current commit 经 HEAD → 分支指针 → commit 两级查出，模型统一、无游离状态 | 不支持真实 Git 的 detached HEAD（spec 不要求） |
| Remote 映射只能整条新增/删除、不能改值 | 允许改已有 remote 的路径 | 符合 spec 语义；实现简单 | 改路径需删了重加 |

**哈希与持久化**

| 决策 | 备选（被否） | 理由 | 代价 |
|---|---|---|---|
| 对象 ID = SHA-1（类型标签首参 + 固定顺序字段），映射排序后逐参数、null → `""` | 裸拼字段字符串 / 不带类型标签 | 任一字段变化都改 ID；标签防 commit/blob 撞命名空间 | 算 ID 必须严格按序排序，编码纪律强 |
| commit ID 由 Repository 外部计算，Commit 保持纯数据类 | 构造方法内自算 | 哈希规则只在调用处出现一次 | 调用方须记得算 ID，不能 new 完直接拿 |
| objects 分 commits/、blobs/ 子目录 + 裸 40 位 ID 文件名 | 扁平目录 + 文件名前缀 `commit_`/`blob_` | 靠目录区分类型，与类型标签互证；裸 ID 即哈希本身 | 类型信息靠目录位置，挪文件会丢类型 |
| 对象平铺、不按前 2 位 fan-out 分片 | Git 式 fan-out（objects/ab/cdef…） | spec 豁免缩写查找计时；课程规模无目录项压力 | 单目录对象数理论上很多（实际无感） |
| 对象只增不改不删、不做 GC | 覆写 / 引用计数删除 | 只增天然幂等，简化并发与失败恢复 | 孤儿对象白占磁盘（spec 不检查不惩罚） |
| stage 单一状态文件、整条覆盖写 | 每暂存条目一个文件 | 一次 writeObject 保证暂存区自身一致 | 小改动也重写整个 stage（通常很小） |
| init 建空 stage、不建 remotes（读不到当空映射） | init 也建空 remotes | stage 是高频核心状态要免判空；remotes 是可选功能可能永不用 | remotes 读取处需容忍文件不存在 |
| 对象间只存 SHA-1 ID 字符串，不嵌套序列化、不用 transient | 嵌套对象图 | 每个 commit 独立成文件、读写不连锁（呼应 spec“顺藤摸瓜”） | 取关联对象需按 ID 再读文件（多次 IO） |
| 分支用 refs/heads 下独立 ref 文件（一文件一指针） | 单一 branches 大 Map 文件 | 增删分支 = 增删文件；与真实 Git 布局一致 | 列分支需列目录（plainFilenamesIn） |
| Repository 辅助方法刻意留白，随命令卡“第二张卡出现即提取” | 预先设计全套 helper | 签名应来自真实复用；提前列会漏组合或造无用方法 | 前几张卡局部重复（如三跳），待触发再抽 |

**错误处理与失败原子性**

| 决策 | 备选（被否） | 理由 | 代价 |
|---|---|---|---|
| 统一汇报链 `throw Utils.error(...)` → run catch → `message` → `System.exit(0)` | 各命令就地 println+return / 受检异常 | 非受检异常穿透多层到唯一出口；逐字消息只有一个打印点 | 控制流靠异常，须保持只在 run 捕获 |
| 入口四步管线顺序钉死（空输入 → 未知命令 → 操作数 → 未初始化），init 豁免末步 | 各入口随意排序 | 前一步是后一步前提；顺序固定可机械核对 | 新增命令必须遵守管线 |
| 三阶段检查 → 内存计算 → 落盘；多文件按只增对象 → 分支指针 → 清 stage 排序 | 边检查边写 / 先清 stage 再移指针 | 一旦落盘即无业务失败点；先清 stage 会丢 `add` 改动 | 所有失败条件必须在写之前查得全（危险命令检查最多） |
| 错误消息集中成一份清单（本地+远程），命令卡只引用 | 消息散落各命令 | 逐字消息有唯一出处，便于与 spec 对账 | 加消息要回清单登记，多一步 |

**命令实现精选**

| 决策 | 备选（被否） | 理由 | 代价 |
|---|---|---|---|
| `add` 当场写 blob（读字节 → 算 ID → 写 objects → ID 入 stage） | 只暂存内容、commit 时才写 blob | addition 本就存 blob ID（02 定稿）；当场写符合内容寻址，blob 只增幂等 | 可能产生尚无 commit 引用的孤儿 blob（无 GC、spec 不罚） |
| `add` 用 `stageChanged` 标记位收敛写盘，stage 仅在变化时写一次 | 各分支各自 writeObject / 无条件写回 | 多分支共享一个“要不要落盘”判定；无变化不写盘正常结束 | 需正确初始化标记，否则漏写 |
| commit 快照 = 复制父 map 后批量 `removeAll`+`putAll`；喂哈希用 `TreeMap` | 就地改父 map / 逐条循环+手动排序 | Commit immutable 不就地改；批量写法一句到位、TreeMap 一行得有序 | 每次提交复制整张当前映射（本就线性、达标） |
| 清 stage 用 `new StagingArea()` 覆盖写，不清内存旧对象 | 对读出对象 clear() 再写回 | 空对象即“已清空”的规范形态，少一步、不碰读出对象 | 读出的旧对象内存中仍非空（命令随即结束，无影响） |
| `rm` 落盘序：先删工作文件、后写 stage | 先写 stage 后删文件 | 崩溃窗口里文件仍可凭 current commit 用 checkout 恢复；反向会在下次 commit 静默丢失追踪 | 极端崩溃下文件已删但 stage 未变（可恢复，非零代价） |
| `find` 无匹配走 throw 汇报链，不在命令体内直接打印 | 按 spec:327 措辞直接打印 | 消息在 04 清单内；全程序只有一个错误出口 | 检查只能在遍历后进行（后置检查行） |
| `status` 判定树第一层按 inW（文件在工作目录与否）分叉 | 第一层按 inR（是否暂删）分叉 | 按“操作性质”切：W 在才取内容比较，取 blob 只在安全分支出现一次 | R 复活情形降级为 `!inR` 否决项，直观性略逊 |
| `checkout` 用法三删除条件不加 `&& !inR`（R 复活副本照删） | 加 `!inR` 保护暂删后复活的工作文件 | spec:397 删除义务无 R 例外、411 挡路只护 overwrite、413 不护 staged removal | 用户 `rm` 后捡回的未提交副本随切分支被删（spec 有意） |
| `checkout` 边读 blob 边写工作目录，不预读整个快照 | 预读全部 blob 再统一写 | 04 已保证落盘后无业务失败点；预读不增真原子、徒增内存 | 极端系统级崩溃下工作目录可能半切换（靠落盘序+重试恢复） |
| 缩写解析 helper `resolveCommitID` 返完整 ID String | 返 Commit / 返 (ID,Commit) 配对新类型 | reset 既要 readObject 又要 ID 写指针；Commit 不含自身 ID | 调用方各自再 readObject 一次（常数级、豁免） |
| 工作目录切换提取宽 helper `replaceWorkingDir`（挡路检查封内） | 窄 helper 挡路留调用方 / 各自内联 | checkout 用法三与 reset 挡路规则逐字同构，封内才不漏检错序 | helper 职责较宽（读 stage+列目录+检查+写删） |

**图算法专题（merge）**

| 决策 | 备选（被否） | 理由 | 代价 |
|---|---|---|---|
| split point = 公共祖先中 depth 最大者（`depth(v)=1+max(两 parent)`；引理：u 是 w 祖先 ⟹ depth(u)<depth(w)） | BFS 近到远取交集第一个 / “到 head 最短距离” / 经典 LCA 算法 | 只有“最长”把祖孙辈分层级数全、不被 merge 第二 parent 的捷径骗；最坏 Ω(N) 必读满，记忆化 O(N) 已最优 | 需额外建立并证明单调引理；depth 是外挂表（Commit 不含自身 ID） |
| 边界处理先统一算 split point、再与两 head ID 逐串比较三路分发 | 遍历中内联早退判边界 | max-depth 循环在边界下本就输出正确 s；逻辑平铺、无循环内特判、易终审 | 边界情形仍跑满第二遍（常数级） |
| 正常合并用两布尔主开关（`cChanged`/`gChanged`）坍缩 4 分支决策表 | spec 七条逐字展开成 13+ 行 if 链 | 比 blob ID 即比内容、除冲突外不读文件内容；`Objects.equals` 统一有/无与同/异 | 决策表（展示穷尽）与实现（4 分支）两层，终审须互证覆盖率 |
| merge commit 提取共用 helper `makeCommit(message, secondParentID)`，commit 委托传 `null` | merge 内联复制 commit 的落盘逻辑 | 同构逻辑复制即漂移；helper 内空 stage 检查天然满足 spec:552“让普通 commit 报错放行” | 回修定稿的 commit 卡；merge 先写 stage、helper 重读的常数级磁盘往返 |
| split point 不加读缓存、深度并列不加 tie-breaker | 加 HashMap 缓存 / 加 id 字典序 tie-break | 每 commit 至多两读仍线性；spec:512 的 split point 是“a latest common ancestor”（任选合法） | 共同祖先多读一次；并列时结果依赖遍历顺序（合法） |
| 冲突内容 String 五段直拼、不补换行 | 逐字节拼接 / 修补换行 | spec:534 straight concatenation、539 明示病态文件不补 | 非 UTF-8 文件经 String 往返可能失真（spec 不测） |
| merge 的 stage 条件写回（非空才 writeObject） | 无条件写回（零动作时同内容覆盖无害） | 零动作路径由 makeCommit 空 stage 检查抛错；条件写保证“throw 先于一切写盘”逐字成立 | 多一个条件分支 |

**远程命令**

| 决策 | 备选（被否） | 理由 | 代价 |
|---|---|---|---|
| remotes 访问只提窄 helper `readRemotes()`（读+判空），写回留调用方 | 宽 helper 连写回封入 / 五卡各内联 | 共享逻辑只在“读+判空”；写回是无逻辑单行，封了即薄包装 | 写回一行在各远程卡重复（可接受） |
| `fetch`/`push` 复制用存在即剪枝遍历（遇 dst 已有整支停走） | 全历史遍历复制 | 需求要求“尚不存在”的子集；正确性靠不变量“有 commit ⟹ 其祖先全在”（init/commit/merge/fetch 结构性保证） | 不变量无运行时守护，靠命令集结构维持 |
| 复制提取双向泛化 helper `copyMissingObjects(src, dst, headID)` | fetch/push 各写一份方向写死的遍历 | 两段遍历除 src/dst 外逐行同构；一处维护 | 调用处须想清楚哪个是 src 哪个是 dst |
| `pull` = fetch + merge 命令级拼装；merge 分支解析扩为双目录（先 heads 后 remotes） | 提取 merge 核心持 ID 的 helper / fetch 写双份分支 | 双目录只动 merge 一步；pull 零新逻辑、失败合集天然成立 | `merge [remote]/[branch]` 手输也可用（超集行为，spec 不测） |

## 4. 已知风险

- **缩写 ID 解析为线性扫目录**：`checkout`/`reset` 的缩写解析列出 `objects/commits/` 全部文件名做前缀匹配，O(对象数)。spec 已明示对此豁免计时，且与平铺不分片的布局决策同源（见 §3 哈希与持久化组）。
- **图遍历递归深度**：`merge` 的 split point 求解用递归 DFS，递归深度随历史长度增长，极端长历史下有栈溢出的理论风险（课程规模无虞）。`fetch`/`push` 的复制遍历已用显式栈（`ArrayDeque`）迭代，无此风险。
- **merge 写盘途中崩溃的残留**：只增对象无害，但已写入工作目录的“current 未追踪”文件会在重跑时被挡路检查拦下，需手动删除或先 `reset`（merge 卡 §5 已逐窗口记账）。系统级崩溃不在 spec 的失败原子性要求内（其要求针对业务失败）。
- **跨仓库复制的完整性依赖结构性不变量**：“仓库中有 commit X ⟹ X 的祖先都在”由 init/commit/merge/fetch/push 的复制方式共同维持，无运行时守护；若未来某命令残缺地引入 commit，剪枝遍历会漏复制（§3 远程组有行）。
- **病态文件与病态输入不防**：无末尾换行文件的冲突拼接会粘连（spec 明示不管）；非 UTF-8 字节文件的冲突内容经 String 往返可能失真（spec 不测）；同名目录挡路、`restrictedDelete` 静默失败等靠 Utils 自保。
- **孤儿对象白占磁盘**：只增不删、不做 GC —— `add` 暂存后未 commit 的 blob、崩溃残留的 commit 都永久占盘；spec 不检查不惩罚。
- **远程路径不校验**：`add-remote` 存的路径可能是坏路径，直到 `fetch`/`push`/`pull` 时才报 `Remote directory not found.`（spec 明言 add-remote 不校验）。
