# `reset`

> 对象结构见 [02 对象模型](../02-object-model.md)，磁盘读写见 [03 持久化与文件布局](../03-persistence-and-layout.md)，错误消息与抛出机制见 [04 错误处理](../04-error-handling.md)。

## 1. 方法签名

`static void reset(String givenID)`

## 2. 依赖数据

- 参数 givenID 对应的 commit：文件名 → blob ID 的映射
- current commit 的文件名 → blob ID 的映射
- 目标 commit 所含各 blob 的字节内容
- objects/commits/ 目录列表（缩写解析）
- 工作目录全部普通文件名
- stage
- HEAD：当前分支名
- refs/heads 下当前分支的指针文件

## 3. 执行步骤

1. 解析缩写 ID 得完整 commit ID → `String commitID = resolveCommitID(givenID)`
2. 取目标 commit 对象 → `Commit targetedCommit = Utils.readObject(Utils.join(".gitlet", "objects", "commits", commitID), Commit.class)`
3. 获取当前 commit 对象 → `Commit currentCommit = getCurrentCommit()`
4. 获取当前分支名 → `String branchName = getCurrentBranchName()`
5. 将工作目录整体替换为目标 commit 的追踪文件 → `replaceWorkingDir(currentCommit, targetedCommit)`
6. 移动当前分支的指针 → `Utils.writeContents(Utils.join(".gitlet", "refs", "heads", branchName), commitID)`
7. 清空 stage → `Utils.writeObject(Utils.join(".gitlet", "stage"), new StagingArea())`

## 4. 复用与被复用

- **调用的 Utils 方法**：`join`、`readObject`、`writeContents`、`writeObject`
- **调用的对象接口**：`new StagingArea()` 无参构造造空 stage 覆盖写入
- **被谁调用**：仅由 `Repository.run` 分发，当前无其他命令复用
- **新提取的辅助方法**：无。调用已提取的 `resolveCommitID(String)`（缩写 ID 解析，checkout 卡提取，见 03 §6）、`replaceWorkingDir(Commit, Commit)`（工作目录整体替换，checkout 卡提取，见 03 §6）、`getCurrentCommit()`（commit 卡提取，见 03 §6）、`getCurrentBranchName()`（commit 卡提取，见 03 §6）

## 5. 状态修改与失败保护

- **分界线**：所有检查在步骤 1 与步骤 5 内部完成（缩写解析与未追踪文件检查），从步骤 5 的工作目录写入起首次写磁盘
- **改动项**：
    - working directory：改（按目标 commit 覆盖或新建文件、删除 current 追踪而目标不追踪的文件）
    - staging area：改（清空）
    - `.gitlet`：不改
    - branch pointer：改（当前分支指针移动到目标 commit ID）
    - HEAD：不改
- **失败保护**：业务错误全部先于写盘抛出，失败时零改动。落盘按工作目录写删、覆盖分支指针、清 stage 顺序推进，系统级崩溃可能停在半切换：
    - 工作目录已写删、指针未移动：分支仍指向旧 commit，重试 `reset` 同一 ID 即可完成
    - 指针已移动、stage 未清空：分支已指向目标 commit，重试同一 ID 的 `reset` 不会被检查拦下（残留 stage 条目在检查中放行），可完成清空、恢复一致

## 6. 性能核对

- **spec 要求**：runtime 相对于目标 commit 快照中文件的总大小为线性，对任何涉及 commit 数量的度量为常数；使用缩写 ID 时豁免计时要求；未单列 memory 要求
- **达标分析**：
    - runtime：完整 ID 直接读出目标 commit，对 commit 数为常数；工作目录写删各 blob 的总量对快照总大小为线性；读单个分支指针、覆盖写指针与清空 stage 均为常数；缩写前缀匹配需列 `objects/commits/` 并扫描，对 commit 数为线性，按规豁免
    - memory：未单列；峰值为单个 blob 的字节内容（边读边写不预读），外加目录文件名列表、两个 commit 与一个 stage 对象，规模只随当前快照与工作目录增长
