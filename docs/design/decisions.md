# 关键决策登记

> 本文件是设计决策的索引：只给“决策 / 理由 / 出处行号”，**不是设计正文**。
> 设计内容一律以 00–05 各篇原文为单一事实来源，凭行号回原文核对。
> 行号随正文编辑会漂移；若行号对不上，以该篇正文搜索决策关键词为准，并顺手回修本表。

## 01 总体架构

| 决策 | 理由 | 出处 |
|---|---|---|
| 类清单为 Main / Repository / Commit / StagingArea，Utils 为边缘工具类 | 命令逻辑集中在 Repository，数据层只放对象结构 | 01:6-22 |
| Blob 不成类 | blob 只是磁盘字节文件，文件名 → blob ID 由 Commit 维护（见 02） | 01:30（职责表）；02:7-9 |
| StagingArea 成类但仅为数据类：保管 addition/removal 两组条目（包级私有 final 字段，Repository 直接操作），暂存规则归 Repository | 与全部定稿命令卡（Repository 直接操作两集合）一致；2026-10-26 回改 01:29 职责表，原表述“执行暂存规则”移至 Repository | 01:29（职责表，已回改） |
| Main 极薄，命令逻辑/入口检查/分发全在 Repository；总入口 `Repository.run(args)` | 入口层只转交，协调层统一处理 | 01:28,54-60 |
| 命令方法只传操作数、与命令同名，连字符转驼峰；init 无参 | 方法命名统一，不含命令名 | 01:71-73 |
| 依赖严格自上而下 Main → Repository → 数据层，禁止反向/环形 | 分层清晰，Utils 不依赖业务类 | 01:35-50 |

## 02 对象模型

| 决策 | 理由 | 出处 |
|---|---|---|
| Blob 无 Java 类，内存中仅 `byte[]`，不存文件名/路径 | 文件名与 blob 的对应由 Commit 映射维护 | 02:7-8 |
| Blob ID = SHA-1(原始字节)，相同内容共享一个 blob | 内容寻址、不重复存储 | 02:9,100-104 |
| Commit 五字段：message/timestamp/firstParent/secondParent/filenameToBlob | 完整快照；parent 分三类型（initial/normal/merge） | 02:17-31 |
| Commit 保存完整快照而非 diff，构造后 immutable | 快照模型，字段不再改变 | 02:34-35 |
| StagingArea 两组：addition Map<名,blobID>、removal Set<名>；commit 成功后清空 | 记录相对 current commit 的更新 | 02:43-51 |
| Remote 映射 = remote name → 远程 `.gitlet` 路径；只能整条增删、不能改值 | 逻辑数据形态，mutable | 02:58-70 |
| fetch 产生 `[remote]/[branch]`，复用 branch 模型，名含 `/`、目录不同 | 远程跟踪分支不单建模型 | 02:67 |
| Branch = `refs/heads/` 下一文件一指针；HEAD 为分支名字符串，只引用分支永不指 commit | current commit 经 HEAD → 分支文件 → ID 查出 | 02:76-80 |
| 集合选型 HashMap（Commit/Remote 映射）、TreeMap/TreeSet（status 需有序列出的场景） | 有序集合自带序，输出不手动排序 | 02:88-96 |
| SHA-1：类型标签首参 + 全字段固定顺序；映射排序后逐参数、null → “”；时间用毫秒数 | 任一字段改变都改变 ID；参数化不裸拼字符串 | 02:100-123 |

## 命令卡（init/add/commit/rm/log/global-log/find/status/checkout/branch/rm-branch/reset/merge；反向给对象类定接口）

| 决策 | 理由 | 出处 |
|---|---|---|
| Commit 构造方法五参数：(String message, Date timestamp, String firstParent, String secondParent, Map<String,String> filenameToBlob) | 命令卡先于类编码，需规定构造接口；对应 02 五字段 | init 卡:17（第 3 节步骤 2） |
| commit ID 由 Repository 在外部用 Utils.sha1 计算（流派 A，非 Commit 内部算） | 02 哈希规则只在调用处出现，Commit 保持纯数据类 | init 卡:18（第 3 节步骤 4） |
| StagingArea 提供无参构造方法，造出来 addition/removal 皆空 | init 需要皆空暂存区，空状态无字段可传 | init 卡:19（第 3 节步骤 3） |
| current commit 三跳提取为嵌套三方法（无参 static）：getCurrentBranchName()←getCurrentCommitID()←getCurrentCommit()；commit 第 4 步直接调用 | 三种粒度各取所需、底层读盘只一份 | commit 卡第 3 节步骤 4、第 4 节；03 §6 |
| commit 快照：复制父 map 后用 keySet 视图 removeAll(removal)、putAll(addition) 批量更新；喂哈希的有序映射用 TreeMap | immutable 不就地改父 map；批量比逐条循环短；TreeMap 一行得有序映射（与 keySet+sort 等价，同为 O(n log n)） | commit 卡第 3 节步骤 5–7、11 |
| commit 落盘序：写 commit 对象 → 覆盖分支指针 → 用 new StagingArea() 覆盖 stage（不清空读出的对象）；HEAD/工作目录不碰、不写 blob | 守 04 通用安全落盘序；清空=整条覆盖写空对象 | commit 卡第 3 节步骤 14–16 |
| rm 两个义务独立执行（非互斥）：无条件移出 addition；被追踪才加 removal + Utils.restrictedDelete 删工作文件；删文件先于写 stage | spec:247 两句并列；restrictedDelete 对不存在/目录返回 false 天然覆盖“用户已删”；先删后写的崩溃可经 checkout 恢复 | rm 卡第 3 节步骤 4–6 |
| rm 第 4 步无条件 Map.remove（幂等不先 containsKey）、不设 stageChanged 标记位 | 检查行保证两集合至少居其一，写回必有变化或无害覆盖 | rm 卡第 3 节步骤 3–4、6 |
| log 回溯：起点成对握 getCurrentCommitID()+getCurrentCommit()，链上按 firstParent 字段的 ID 直接 readObject（不走三跳）；while 先打印后推进，firstParent==null 打印 initial 后退出 | Commit 不存自身 ID；先打印防漏起点与 initial；判空才读盘防 null 路径 | log 卡第 3 节步骤 2–4；spec:260,293 |
| log 单块打印提取为输出型 helper `static void printCommit(Commit,String)`，归属卡 log、global-log 复用；方法体唯一定义在 log 卡第 3 节 | 同一逻辑第二张卡出现即提取；逐字格式串须有单一可照查处，调用卡只调用不复制 | log 卡第 3 节步骤 1/第 4 节；03 §6:115；接口清单:37 |
| global-log 枚举全库：plainFilenamesIn(objects/commits) 列目录（文件名即裸 ID），for 逐个 readObject+printCommit；不依赖 current/HEAD | 无指针可沿、spec:310 授权目录遍历；03:124 禁扫目录对此卡不适用；顺序 spec 不限取字典序 | global-log 卡第 3 节；spec:309-312 |
| find 枚举全库按 message 内容匹配：plainFilenamesIn 列目录 → 逐个 readObject 读 `message` 做 `message.equals(commit.message)`，命中 Utils.message(id) 全打（无 break）；for 外置 `boolean matched`，遍历后 `!matched` 才 throw；不调 printCommit（只打裸 ID 一行，非整块） | message 不参与寻址、无指针可沿，按内容找对象只能整库枚举；检查时机是“完成匹配后”故为后置检查行（区别 init/add 前置断言）；错误仍走 throw 统一汇报链、不在命令体直接打印错误 | find 卡第 3 节步骤 1–4、第 4 节；spec:320-331；04:35 |
| status 判定先算 inW/inA/inR/inC 四存在位与 `expectedExists=inA‖(inC&&!inR)`，决策树**第一层按 inW 分叉**（W 在比期待版本 blob、W 无只判删除）；六条件 MECE，R 复活（R 有 W 有）由 `!inR` 否决为 untracked；遍历范围=四位置文件名并集 HashSet，保证已删文件不漏 | inW 按“比内容 vs 判删除”操作性质切，取工作内容只在 W 在分支一次、不对已删文件取 blob；被否的 R 打头树见取舍素材；并集防漏条件 3/4 的 W 无名文件 | status 卡第 3 节步骤 10；spec:357-374 |
| status 结果容器：modified 用 `TreeMap<String,Boolean>`（值 `true`=modified/`false`=deleted，TreeMap 按键排序、后缀打印时拼，spec:354 后缀不参比）、untracked 用 `TreeSet<String>`；工作文件 blob 收 HashMap、遍历并集收 HashSet（只查不序）；Staged/Removed/Branches 直接遍历 TreeMap/TreeSet/已排序 List 不另建结构 | 02 选型（addition=TreeMap、removal=TreeSet；branch 用 refs/heads 文件）正为 status 有序列出；modified 须同时带名与后缀故选 Map，untracked 无名外信息故选 Set | status 卡第 3 节步骤 6–11；02:92-96 |
| status 不提新 helper：六条件分类、五节打印均仅本卡使用；步骤 6 读字节+`sha1("blob",…)` 虽与 add 形式重合但仅两行 Utils 原语、后续语义相反（add 写 blob 改 stage、status 只读），不构成可复用逻辑片段；只调用已提取的 getCurrentBranchName/getCurrentCommit | 提取规矩是“同一逻辑片段第二次出现”而非“同一两个原语出现”；薄包装只增间接；三跳复用已在 03§6/接口清单 | status 卡第 4 节；取舍素材两行 |
| checkout 一命令三用法：唯一公共入口 `checkout(String[] operands)`（run 分派点 `Arrays.copyOfRange(args,1,n)` 切去命令名，方法内从下标 0 数起；定长命令仍收定长 String、不搞统一 List），入口只校验形状（个数+`--`位置，非法 → `Incorrect operands.`），分派到三个**私有** helper `checkoutFile(String)`/`checkoutCommit(String givenID,String filename)`/`checkoutBranch(String)`（另有共用私有 helper `checkoutFileFromCommit(Commit,String)`）；私有分派 helper 仅服务本卡，**不回填 03§6/接口清单**（区别于三跳/printCommit 跨命令 helper） | 三公共方法（违 01“一命令一同名方法”）；统一收 List<String>（要为一家在 run 加转换、回头改 7 张定稿卡）；helper 传数组/传完整 ID（耦合下标布局；缩写解析属读仓库语义不能在入口做） | checkout 卡第 1 节；01:58,64,69-71；04:106-117 |
| checkout 用法二缩写解析**本卡内联、不提前提取**（列 commits 目录 → `toLowerCase` 前缀匹配 → 0 个/多个都 throw `No commit with that id exists.`、唯一才用；40 位是前缀特例；不靠 null 以外的失败通道），等 reset 卡第二次出现再提取为 Repository helper（守 printCommit 先例）；空前缀/非 hex 病态输入本卡不防，随该 helper/03§3 处理 | 现在就提 helper（reset 复用虽被 03:50 预定，但签名未经两调用方检验）；返回 Commit vs 完整 ID String 留待 reset 定（reset 需 ID 写指针、Commit 不含自身 ID） | checkout 卡第 3 节用法二步骤 1–3；03 §3（46-63）；spec:409,415-421 |
| checkout 用法三挡路检查独立成遍、置于一切写盘前（步骤 10），对每个工作文件三出口 MECE：in A 放行 /（C 有且非 R）放行 / 其余 untracked 若 T 有同名则 throw in-the-way；写集=T 键、删集=`!T有&&C有`、两集不相交；落盘序写工作文件 → 删多余 tracked → 写 HEAD → 清 stage（HEAD/stage 最后动，崩溃可重试）；用法一/二只覆盖写单文件、不动 stage，且只认 commit 版无视 staged 版（spec:393/423） | 删除加 `!inR` 保护 R 复活副本（违 spec:397 删除义务，见取舍素材）；预读全部 blob 再写（不增真原子、徒增内存，见取舍素材） | checkout 卡第 3 节用法三；spec:397,407-413,423；04:158,172 |
| reset 卡触发两跨卡 helper 提取（归属 checkout 卡，包级私有、跨命令复用）：①`resolveCommitID(String givenID)→完整 ID String`（缩写解析，内查唯一性+存在性，0/多个匹配均 throw）；②`replaceWorkingDir(Commit current, Commit target)`（读 stage+列工作名单 → 挡路检查 → 写目标全部 blob → 删 C 有 T 无；不负责指针与 stage）。reset 7 步=resolve → readObject 目标 → getCurrentCommit → getCurrentBranchName → replaceWorkingDir → 覆盖当前分支指针为完整 ID → new StagingArea() 清 stage；HEAD 不写 | ①返 Commit/配对类型（reset 还需 ID 写指针、Commit 不含自身 ID）；②窄 helper 只包写删、挡路留外（两调用方挡路同构，封内才保证“before anything else”）；复制两套循环（违第二张卡即提取约定）。返回类型与宽 helper 被否备选见取舍素材 | reset 卡第 3 节步骤 1/5、第 4 节；checkout 卡用法二/三；03 §6:115-118；接口清单:38-39；spec:495-500 |
| reset 崩溃窗口二（指针已移、stage 未清）可经重试同一 ID 的 reset 自救：reset 无“当前分支”检查（区别 checkout 用法三），残留 stage 条目在挡路检查中放行（in A 放行；removal 名字在新 current=目标 commit 下不构成 untracked）；checkout 卡窗口二保守措辞已据此回翻为确定结论 | 无 | reset 卡第 5 节；checkout 卡:176；spec:499-500 |
| commit/merge 共用提交提取为 `makeCommit(String message, String secondParentID)`（归属 commit 卡，方法体 = commit 卡 §3 原 16 步，仅步骤 9 的 `null` 与步骤 12 的 `""` 两处参数化；`commit` 传 `null`、`merge` 传被合并分支 head ID） | 同一提交逻辑在第二张卡（merge）出现即提取，复制即漂移；空 stage 检查封在 helper 内，天然实现 spec:552“让普通 commit 报错放行”（stage 空 ⟺ merge 无任何文件动作，throw 仍先于一切写盘） | commit 卡 §3/§4；03 §6；取舍素材（方案甲行）；merge 卡 §3 步骤 23 |
| merge split point = depth 最大公共祖先：三私有方法（findSplitPoint/collectAncestors/deepestCommonAncestor），先收 current 祖先并算到 initial 最长距离、再沿 given 取 depth 最大者；仅服务本卡不登记 03§6（同 checkout 私有 helper 口径） | 单调引理 u 是 w 祖先 ⟹ depth(u)<depth(w)，max-depth 公共祖先必为 LCA；被否 BFS 等备选见取舍素材 77–82 行 | merge 卡 §3“得到 split point”；spec:512 |
| merge 边界三分支：先统一算 splitPointID 再逐串比较（先比 given、再比 current） —— 边界一打印祖先消息不动状态、边界二 fast-forward（replaceWorkingDir、移当前分支指针、清 stage、打印）、其余走正常 merge | spec:512 描述顺序即先 given 后 current；先算 s 再比较，循环内无边界特判（取舍素材有行） | merge 卡 §3 步骤 12–17；spec:512 |
| merge 正常合并 = 两布尔主开关（`cChanged`/`gChanged`，`Objects.equals` 比 blob ID、null=缺席）坍缩 5 行决策表；步骤序：分析只动内存 → 挡路检查 → 写删（先新 blob、再工作文件、再删） → 条件写回 stage → makeCommit → 最后打冲突消息 | 比 ID 即比内容、除冲突分支不读文件内容；挡路检查需完整写/删集故分析先行；spec:541 的“Then”定提交先于冲突消息 | merge 卡 §3 决策表与步骤 18–24；spec:513–541,552 |
| merge stage 条件写回（非空才 writeObject）；零动作由 makeCommit 空 stage 检查抛 `No changes added to the commit.`（04:68 已预告此触发） | 零动作 ⟺ stage 未动；条件写保“throw 先于一切写盘”逐字成立 | merge 卡 §3 步骤 22、§5；04:158,68 |

## 远程命令卡（add-remote/rm-remote/fetch/push/pull）

| 决策 | 理由 | 出处 |
|---|---|---|
| add-remote 首张远程卡确立 remotes 访问模式：读（不存在当空映射，`exists() ? readObject : new HashMap<>()`） → 检查重名 → `replace("/", File.separator)` 转换后 `put` → 整条覆盖写回；“读-改-写”片段标候选、待第二张远程卡提取回填 03§6/接口清单 | 03 §4 定 init 不建 remotes、读不到当空映射；spec:1030 要求分隔符转换；“第二张卡出现即提取”约定 | add-remote 卡 §3 步骤 2–4、§4；03:73-78；spec:1030,1032 |
| rm-remote 触发 `readRemotes()` 提取（窄 helper：读 `.gitlet/remotes`、判空返回空映射），归属 add-remote 卡，03§6/接口清单已登记；add-remote 卡 §3 改调用+补 helper 小节、§4 修订、§5 分界线步骤号回修 | “同一逻辑片段第二张卡出现即提取”约定；窄/宽取舍见取舍素材 | rm-remote 卡 §3/§4；add-remote 卡 §3/§4；03 §6 |

## 03 持久化与文件布局

| 决策 | 理由 | 出处 |
|---|---|---|
| objects 下分 commits/、blobs/ 子目录，文件名用裸 40 位 ID（路线 A，不用文件名前缀） | 靠所在目录区分类型；与 02 类型标签互证 | 03:9-11,23-24 |
| 对象平铺、不再按前 2 位 fan-out 分片 | spec 已豁免缩写查找计时（原文 “we won't worry about timing”）；规模小无压力；分片改动已定稿目录树且增加解析复杂度。完整论证见 05 | 03:37 |
| 对象文件写入后不修改、不删除；同 ID 已存在时内容相同，覆盖写与跳过等价（`copyMissingObjects` 先判存在跳过） | 内容哈希决定 ID，内容同则 ID 同 | 03:43-44 |
| 缩写机制留在 03，错误文案归 04 | 机制是按布局定位文件，04 不应复述磁盘布局（单一事实来源） | 03:46-58 |
| 缩写解析只对 commit（checkout v1/v2、reset），blob 不支持 | 无任何命令让用户手输 blob ID | 03:50-51 |
| 解析三步：列 commits 目录 → 前缀筛选（先转小写） → 按 0/1/多分流 | 平铺目录下唯一可行的定位方式；0 与歧义为失败 | 03:55-58 |
| ID 小写存储与比较；大写输入先转小写；前缀长 1–40，40 位为特例 | 大小写边界统一处理 | 03:62-63 |
| 除缩写解析、global-log、find 外禁止扫描 objects 目录；log 等沿 parent/branch 引用直接读取 | 防止用目录扫描代替指针追踪而违反 log runtime；global-log/find 需全库枚举，spec 授权 | 03:124 |
| HEAD 存分支名；stage/remotes 启动整体反序列化；分支一文件、内容为 ID | 三类读写规律 | 03:31-33 |
| stage 与 remotes 合并为第 4 节“单一状态文件”，共用整体序列化与原子性规律 | 同机制不重复写，单一事实来源 | 03:65-77 |
| init 建空 stage 文件；init 不建 remotes、读不到当空映射 | stage=高频核心状态读取免判空；remotes=可选加分功能可能永不用到 | 03:69,74,78 |
| 修改前先校验、通过后改内存、再整条覆盖写；校验失败不触碰文件 | 失败不留半完成状态 | 03:77 |
| 仅 Commit、StagingArea implements Serializable，并声明 serialVersionUID=1L | 其余类不落盘；锁定版本防改字段致旧数据反序列化即崩 | 03:82,86 |
| 三种读写形态：对象序列化（Commit/StagingArea/remotes）、原始字节（blob）、单行文本（HEAD/refs） | 按数据形态区分；blob 无对象结构、引用只是一行文本 | 03:84-88 |
| 对象间只存 SHA-1 ID 字符串，保证序列化不连锁、每个 commit 独立成文件；不用 transient | 对应 spec 653 行“顺藤摸瓜”警告 | 03:90 |
| Utils 原语表定稿，Repository 辅助方法刻意留白、随命令卡实现按复用提取 | 方法签名依据来自命令；提前列会漏组合或造无用方法 | 03:93-108 |
| §6 原语表补全为 8 行：新增 restrictedDelete、error/message，已有行内点出 serialize 与 sha1 两形态 | 原表不全，与真实 Utils.java、模板速查表对齐，贯彻单一事实来源 | 03:99-106 |
| 除缩写解析、global-log、find 外禁扫 objects 目录，log 沿 firstParent 指针直接读取 | 防目录扫描代替指针追踪违反 log runtime；global-log/find 属 spec 授权的全库枚举 | 03:124 |

## 04 错误处理（已定稿）

| 决策 | 理由 | 出处 |
|---|---|---|
| 全部错误消息集中为清单，分本地/远程；正文列表呈现（不用宽表格） | 命令卡统一回来引用，逐字消息唯一出处；长消息列表不撑表 | 04:5-96 |
| 统一汇报链：`throw Utils.error(...)` → run 的 catch → `Utils.message` → `System.exit(0)` | Utils.error 只造异常不抛；唯一打印出口保逐字；非受检异常可穿透多层 | 04:124-143 |
| 入口四步管线顺序钉死：空输入 → 未知命令 → 操作数 → 未初始化；init 豁免第 4 步 | 前一步是后一步的前提（无 args[0] 不能判命令名等） | 04:102-112 |
| 管线统一“检查时机”，具体“检查规则”按命令分派 | 各命令参数格式不同，但时机一致，不破坏顺序 | 04:111 |
| 入口层只查参数形态（对应 Incorrect operands）；内容不合格（专属消息）归命令方法 | 形式问题不读仓库；业务内容问题需读状态或属专属错误 | 04:114-118 |
| log、global-log、status 无失败情况 | spec 中这三者无 Failure cases | 04（第 1 节末） |
| 远程 5 句：remote 已/不存在、pull down before pushing、remote directory not found、that remote does not have that branch；pull 无专属消息 | pull 失败=fetch+merge 合集（spec:1073） | 04:72-96 |
| 失败原子性：检查 → 内存计算 → 落盘；所有 throw 排在首次写之前；写盘先只增新对象、再动分支指针、最后清理 stage | 指针未动可见状态不变；先清 stage 再动指针会丢 `add` 改动 | 04:144-168 |
| 五项保护范围：working directory、staging area、`.gitlet`、branch pointer、HEAD；只读命令天然原子；危险命令 rm/checkout/reset/merge | spec 要求失败零改动、点名危险命令（spec:166-167,193） | 04:148-172 |
