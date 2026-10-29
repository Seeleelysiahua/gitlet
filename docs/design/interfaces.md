# 对象接口清单

> 本文件是写命令卡前查的接口一览表，**不是对象结构的定稿正文**。
> 对象字段的最终事实来源是 [02 对象模型](02-object-model.md)；“为什么这么定”见[关键决策登记](decisions.md)。
> 用途：写新卡前先查本表，防止重复定义已长出的构造方法/方法；命令卡反向定了新接口就追加到这里，并在“出处”标来源卡。
> 所有命令卡完成、方法回填 02 后，本清单的临时使命结束（并入 02 或留存）。

## Commit

| 种类  | 签名                                                                                                                   | 说明                           | 出处                |
| --- | -------------------------------------------------------------------------------------------------------------------- | ---------------------------- | ----------------- |
| 字段  | `String message`                                                                                                     | 提交信息文本，非空                    | 02:19             |
| 字段  | `Date timestamp`                                                                                                     | 提交时刻，非空                      | 02:20             |
| 字段  | `String firstParent`                                                                                                 | first parent 的 ID，可空         | 02:21             |
| 字段  | `String secondParent`                                                                                                | second parent 的 ID，可空        | 02:22             |
| 字段  | `Map<String,String> filenameToBlob`                                                                                  | 文件名 → blob ID，非空（initial 为空映射） | 02:23             |
| 构造  | `Commit(String message, Date timestamp, String firstParent, String secondParent, Map<String,String> filenameToBlob)` | 五参构造                         | init 卡（第 3 节步骤 2） |


## StagingArea

| 种类  | 签名                              | 说明                                                 | 出处                |
| --- | ------------------------------- | -------------------------------------------------- | ----------------- |
| 字段  | `Map<String,String> addition`   | staged for addition：文件名 → blob ID             | 02:47             |
| 字段  | `Set<String> removal`           | staged for removal：文件名                      | 02:48             |
| 构造  | `StagingArea()`                 | 无参构造，造出来 addition/removal 皆空                       | init 卡（第 3 节步骤 3） |

## Repository（辅助方法）

> 随命令卡复用提取；commit 卡（第三张卡）首次成卡。前三个（三跳）均包级私有 `static`、无参，自下而上嵌套调用，底层读盘只写一份。

| 签名 | 作用（读什么） | 出处 |
| --- | --- | --- |
| `String getCurrentBranchName()` | 读 `.gitlet/HEAD`，得当前分支名 | commit 卡第 4 节 |
| `String getCurrentCommitID()` | 调 `getCurrentBranchName()`，读 `refs/heads/<分支>` 单行指针，得当前 commit ID | commit 卡第 4 节 |
| `Commit getCurrentCommit()` | 调 `getCurrentCommitID()`，按 ID 从 `objects/commits/` 反序列化出当前 commit | commit 卡第 4 节 |
| `void printCommit(Commit commit, String commitID)` | 按 log 版式向标准输出打印单个 commit 一块（`===`/commit ID/[merge 的 `Merge: 4975af1 2c1ead1` 行]/Date/message/块后空行）；持对象与其自身 ID | log 卡第 3 节 |
| `String resolveCommitID(String givenID)` | 缩写 ID 解析：列 `objects/commits/`，按 `givenID.toLowerCase()` 前缀匹配，0 个或多个匹配均抛 `No commit with that id exists.`，返回唯一完整 ID；调用方自行 readObject | checkout 卡第 3 节用法二（`reset` 卡共用） |
| `void replaceWorkingDir(Commit currentCommit, Commit targetedCommit)` | 工作目录整体替换：内部读 stage、列工作目录普通文件，先做未追踪文件挡路检查（抛 `There is an untracked file in the way; delete it, or add and commit it first.`），再遍历目标 map 写全部追踪 blob、删 current 追踪而目标不追踪的文件；不负责分支指针与 stage | checkout 卡第 3 节用法三（`reset` 卡共用） |
| `void makeCommit(String message, String secondParentID)` | 提交当前 stage 的共用实现：空白消息/空 stage 两检查 → 复制 current 映射并应用 stage → 五参构造 → sha1（`secondParentID` 为 `null` 折 `""`） → 落盘对象 → 移当前分支指针 → 清 stage；方法体唯一定义在 commit 卡 §3 | commit 卡第 3/4 节（`merge` 卡共用） |
| `HashMap<String, String> readRemotes()` | 读 `.gitlet/remotes`，文件不存在返回空映射（`init` 不建） | add-remote 卡第 3/4 节（`rm-remote` 等远程卡共用） |
| `void copyMissingObjects(String srcGitlet, String dstGitlet, String headID)` | 从 src 仓库向 dst 仓库复制 dst 缺失的 commit 与 blob（存在即剪枝） | fetch 卡第 3/4 节（`push` 卡共用） |

