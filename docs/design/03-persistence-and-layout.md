# 03 持久化与文件布局

> 【共享底座】本文件是磁盘布局的单一事实来源。

## 1. `.gitlet` 目录树

```
.gitlet/
├── objects/
│   ├── commits/
│   └── blobs/
├── refs/
│   ├── heads/
│   └── remotes/
│       └── <remote name>/
├── HEAD
├── remotes
└── stage
```

| 数据          | 位置                            | 文件名       |
| ----------- | ----------------------------- | --------- |
| commit      | `objects/commits/`            | 裸 40 位 ID |
| blob        | `objects/blobs/`              | 裸 40 位 ID |
| 本地分支指针      | `refs/heads/`                 | 分支名       |
| 远程跟踪分支      | `refs/remotes/<remote name>/` | 远程分支名     |
| HEAD        | `.gitlet/`                    | HEAD      |
| StagingArea | `.gitlet/`                    | stage     |
| remote 映射   | `.gitlet/`                    | remotes   |

- immutable 对象（commit/blob）：只增不改不删，按 ID 取。
- 按名引用（本地/远程分支）：本地分支可增可删、远程跟踪分支由 `fetch` 增加或更新、不单独删除，内容是 ID、可重写。
- 单一状态文件（HEAD/stage/remotes 映射）：HEAD 启动时读取分支名。stage/remotes 启动时整体反序列化加载。

## 2. 对象文件存放

真实 Git 不区分对象类型，所有对象统一用 `objects/<前 2 位>/<后 38 位>` 存放，类型信息靠对象文件内容开头的类型头（commit/blob/tree…）标识。Gitlet 不采用：spec 已豁免缩写查找的计时要求（原文 “we won't worry about timing”），完整取舍见 [05 测试策略与设计取舍](05-testing-and-tradeoffs.md)。

对象存放规则：

- 每个 commit 对象一个文件，位于 `objects/commits/<40 位 ID>`。
- 每个 blob 对象一个文件，位于 `objects/blobs/<40 位 ID>`。
- 对象文件写入后不修改、不删除。
- 写入时若同 ID 文件已存在，说明内容相同；覆盖写与跳过等价（`copyMissingObjects` 会先判存在跳过）。

## 3. 缩写 ID 解析

缩写 ID 适用范围：

- 只有接受 commit ID 参数的命令适用：`checkout`、`reset`；
- 明确 blob 不适用：没有任何命令让用户手输 blob ID。

缩写 ID 解析规则：

- 列出 `objects/commits/` 下全部文件名；
- 筛出以用户输入为前缀的 ID（比较前统一转小写）；
- 按匹配数分三种结果：0 个 → 失败；恰好 1 个 → 用该完整 ID；多个 → 歧义失败；
- 失败的错误消息文案不在本节写，见 [04 错误处理](04-error-handling.md)。

缩写 ID 输入边界说明：

- 前缀长度 1–40 位；输入恰好 40 位时是前缀匹配的特例（匹配自己，或 0 个）；实现未单独校验长度，直接按 `startsWith` 匹配；
- ID 一律以小写存储与比较；用户输入大写前缀时先转为小写再匹配。

## 4. 单一状态文件：stage 与 remotes

stage：
- stage 存储在 `.gitlet/stage`，通过序列化写入磁盘，反序列化读回。
- 执行 `init` 时新建空 stage 文件。
- 暂存区状态改变，重写 stage。

remotes：
- remote 存储在 `.gitlet/remotes`，通过序列化写入磁盘，反序列化读回。
- `init` 不建 remotes 文件，读不到当空映射，首次 `add-remote` 才创建。
- 读取 remotes 时，通过反序列化得到 HashMap，在这个映射中查找条目。

stage 和 remotes 都要满足：修改前先校验、通过后改内存、再整条覆盖写；校验失败则不触碰文件。
stage 是多数命令高频读写的核心状态，`init` 即建立，读取时不必处理缺失；remotes 属加分的可选功能，可能永远用不到，故 `init` 不建、读不到时按空映射处理。

## 5. 序列化方案

可序列化的类：Commit，StagingArea，需声明 `private static final long serialVersionUID = 1L`，防止字段改变导致的旧数据反序列化失败。Main、Repository、Utils 用于接受参数、执行命令逻辑和辅助读写文件，不需要写入磁盘，因此无需实现序列化。

`.gitlet` 中的数据按形态分三种读写方式：

- **对象序列化**：Commit、StagingArea、remotes 通过 Java 序列化整体写入，读时整体反序列化还原；
- **原始字节**：blob 不含对象结构，以原始字节直接读写；
- **单行文本**：HEAD 存分支名、refs 下分支文件存 commit ID，都以普通文本字符串读写，无需序列化。

Java 在序列化时会把引用对象一并写入，因此对象之间只存 SHA-1 ID 字符串，String 是基本可序列化数据、不会牵出别的对象，所以每个 commit 独立成文件。

## 6. 读写辅助方法约定

Utils 类提供的方法：

| 方法                               | 作用                             |
| -------------------------------- | ------------------------------ |
| `readContents` / `writeContents` | 读/写原始字节或文本                     |
| `readContentsAsString`           | 读文件为字符串（用于 HEAD、refs）          |
| `readObject` / `writeObject`     | 反序列化读对象 / 序列化写对象（内部经 `serialize`） |
| `plainFilenamesIn`               | 列出目录下普通文件名（缩写解析、status 用），目录不存在返回 null |
| `restrictedDelete`               | 删除普通文件（`rm` 用），所在目录须含 `.gitlet`、不删目录 |
| `join`                           | 拼接路径                           |
| `sha1`                           | 计算 SHA-1，支持可变参数 / List 两种传参形态      |
| `error` / `message`              | `error` 只造异常须自己 `throw`；`message` 打印一行（汇报链见 04） |

Repository 辅助方法：随命令卡复用提取，已提取的登记于下（命令卡统一引用此处）。

| 签名                                                        | 作用                                                            | 首次登记     |
| --------------------------------------------------------- | ------------------------------------------------------------- | -------- |
| `static String getCurrentBranchName()`                    | 读 HEAD，得当前分支名                                                 | commit 卡 |
| `static String getCurrentCommitID()`                      | 调 `getCurrentBranchName()`，读 refs/heads 分支指针，得当前 commit ID    | commit 卡 |
| `static Commit getCurrentCommit()`                        | 调 `getCurrentCommitID()`，按 ID 从 objects/commits 反序列化当前 commit | commit 卡 |
| `static void printCommit(Commit commit, String commitID)` | 按 log 版式打印单个 commit                                           | log 卡    |
| `static String resolveCommitID(String givenID)`           | 缩写 ID 解析：列 objects/commits 按小写前缀匹配，0 个或多个匹配均抛 `No commit with that id exists.`，返回唯一完整 ID | checkout 卡 |
| `static void replaceWorkingDir(Commit currentCommit, Commit targetedCommit)` | 工作目录整体替换：内部读 stage、列工作目录普通文件，先做未追踪文件挡路检查（抛 `There is an untracked file in the way; delete it, or add and commit it first.`），再写目标 commit 全部追踪文件、删 current 追踪而目标不追踪的文件；不负责分支指针与 stage | checkout 卡 |
| `static void makeCommit(String message, String secondParentID)` | 提交当前 stage 的共用实现：空白消息/空 stage 两检查 → 复制 current 映射并应用 stage → 五参构造 → sha1（`secondParentID` 为 `null` 折 `""`） → 落盘对象 → 移当前分支指针 → 清 stage；`commit`（传 `null`）与 `merge`（传被合并分支 head ID）共用，方法体唯一定义在 commit 卡 §3 | commit 卡 |
| `static HashMap<String, String> readRemotes()` | 读 `.gitlet/remotes` 整体反序列化，文件不存在（`init` 不建）返回空映射；远程命令共用，方法体唯一定义在 add-remote 卡 §3 | add-remote 卡 |
| `static void copyMissingObjects(String srcGitlet, String dstGitlet, String headID)` | 跨仓库对象复制：从 src 的 headID 沿 parent 回溯，把 dst 缺失的 commit（readObject/writeObject）与 blob（字节拷贝）复制过去，遇 dst 已有即整支剪枝；src/dst 均为 `.gitlet` 目录路径串（远程路径运行时读表得到，本地侧传 `GITLET_DIR.getPath()`）；`fetch`（远程 → 本地）与 `push`（本地 → 远程，方向相反）共用，方法体唯一定义在 fetch 卡 §3 | fetch 卡 |

> 前三者无参 static、自下而上嵌套，底层读盘只写一份；命令按需停在某一层（要分支名、要 ID、要对象）。`printCommit` 为输出型 helper，持 Commit 对象与其自身 ID（Commit 不存自身 ID），`log`、`global-log` 共用。`resolveCommitID` 为缩写解析型 helper，持原始串（可能缩写/大写）返回完整 ID，`checkout` 用法二、`reset` 共用；调用方自行按返回的 ID readObject。`replaceWorkingDir` 为工作目录整体替换型 helper，持 current/目标两个 Commit 完成挡路检查与全量写删，`checkout` 用法三、`reset` 共用；分支指针移动与清空 stage 由调用方各自完成。`makeCommit` 为提交型 helper，持消息与第二 parent ID（`null` 按 02 折 `""` 参与哈希），`commit`、`merge` 共用，方法体唯一定义在 commit 卡 §3。`readRemotes` 为 remotes 读取型 helper，判空返回空映射（`init` 不建 remotes），远程命令共用，方法体唯一定义在 add-remote 卡 §3。`copyMissingObjects` 为跨仓库复制型 helper，持源/目标两个 `.gitlet` 目录路径串（均为 `String`：远程路径运行时读表得到，本地侧传 `GITLET_DIR.getPath()`）做存在即剪枝的遍历复制，`fetch`、`push` 方向互反而共用，方法体唯一定义在 fetch 卡 §3。后续命令卡出现新的可复用读写片段时，照“同一逻辑在第二张卡出现即提取”的约定继续追加本表。


除缩写 ID 解析、`global-log` 与 `find` 外，任何命令不得通过列举 `objects/` 目录来定位对象；凡能由 parent ID 或 branch pointer 到达的对象（如 `log` 沿 firstParent 遍历 head 历史），必须沿引用直接按 ID 读取。
