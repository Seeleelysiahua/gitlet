# 04 错误处理

## 1. 错误消息清单

### 本地

破折号后为触发条件；机制不复述，缩写判定见 [03 持久化与文件布局](03-persistence-and-layout.md)

**通用（入口检查）**

- `Please enter a command.` —— 没有输入任何命令
- `No command with that name exists.` —— 命令名不存在
- `Incorrect operands.` —— 操作数的数量或格式不对
- `Not in an initialized Gitlet directory.` —— 当前目录不存在 `.gitlet`

**init**

- `A Gitlet version-control system already exists in the current directory.` —— 当前目录已存在 `.gitlet` 目录

**add**

- `File does not exist.` —— 要添加的文件不存在

**commit**

- `No changes added to the commit.` —— 暂存区为空，没有已暂存的改动
- `Please enter a commit message.` —— 提交消息为空或只含空白字符

**rm**

- `No reason to remove the file.` —— 该文件既不在暂存区、也不被当前提交追踪

**find**

- `Found no commit with that message.` —— 没有任何提交的消息等于给定消息

**checkout**

- `File does not exist in that commit.` —— 该文件不存在于指定提交中
- `No commit with that id exists.` —— 解析不到对应 ID 的提交
- `No such branch exists.` —— 该分支不存在
- `No need to checkout the current branch.` —— 要检出的分支是当前分支
- `There is an untracked file in the way; delete it, or add and commit it first.` —— 存在一个未追踪文件会被本次操作覆盖或删除

**branch**

- `A branch with that name already exists.` —— 同名分支已经存在

**rm-branch**

- `A branch with that name does not exist.` —— 要删除的分支不存在
- `Cannot remove the current branch.` —— 不能删除当前所在的分支

**reset**

- `No commit with that id exists.` —— 解析不到对应 ID 的提交
- `There is an untracked file in the way; delete it, or add and commit it first.` —— 存在一个未追踪文件会被本次操作覆盖或删除

**merge**

- `You have uncommitted changes.` —— 暂存区仍有未提交的改动
- `A branch with that name does not exist.` —— 要合并的分支不存在
- `Cannot merge a branch with itself.` —— 要合并的分支是当前分支
- `There is an untracked file in the way; delete it, or add and commit it first.` —— 存在一个未追踪文件会被本次操作覆盖或删除
- `Given branch is an ancestor of the current branch.` —— 给定分支已是当前分支的祖先，无需合并（正常提示）
- `Current branch fast-forwarded.` —— 当前分支可直接快进到给定分支（正常提示）
- `Encountered a merge conflict.` —— 合并遇到冲突；merge commit 照常提交，提交后打印提醒（正常提示）

注意：`merge` 在自动提交时也可能触发 `No changes added to the commit.`。

`log`、`global-log`、`status` 没有失败情况。

### 远程

<!-- pull 没有专属消息，失败情况等于 fetch 与 merge 的合集（见 spec:1073） -->

**add-remote**

- `A remote with that name already exists.` —— 同名远程已存在

**rm-remote**

- `A remote with that name does not exist.` —— 要删除的远程不存在

**push**

- `Please pull down remote changes before pushing.` —— 远程分支当前指向的提交不在本地当前提交的历史中（无法快进）
- `Remote directory not found.` —— 远程 `.gitlet` 目录不存在

**fetch**

- `That remote does not have that branch.` —— 远程仓库中不存在给定分支
- `Remote directory not found.` —— 远程 `.gitlet` 目录不存在

**pull**

- 无专属消息：可能触发 `fetch` 的全部失败消息，以及 `merge` 的全部失败消息。

## 2. 检查与抛出机制

**入口检查管线**

管线：在分发执行命令之前，按固定的顺序检查，任一失败就抛出异常，不再进入后续步骤。

1. 空输入 → `Please enter a command.`
2. 未知命令 → `No command with that name exists.`
3. 操作数数量/格式 → `Incorrect operands.`
4. 未初始化 → `Not in an initialized Gitlet directory.`

入口检查的前三步不依赖 `.gitlet`；第四步只检查 `.gitlet` 目录是否存在，命令内部的读写才访问 `.gitlet` 内容。
第三步规定检查的时机，具体规则按命令分派，只核对参数本身的数量和格式。
`init` 可以不执行第 4 步检查。

**入口层与语义层的分工**

- 四步管线只拦入口形态问题。
- 参数形式正确但内容不满足业务要求（空提交消息、文件/分支不存在、暂存区为空、缩写 ID 不匹配）由各命令方法在执行中抛出。
- 需读仓库状态的、或对应命令专属消息的属于语义层。

**统一汇报链与归属**

无论是入口管线还是命令方法，所有失败都走同一条汇报链，区别只在异常从何处抛出：

```
命令方法 / 入口检查中，一旦判定失败
│  throw Utils.error("第 1 节清单里的某条消息")
│    · Utils.error 造出带消息的 GitletException（非受检异常）
│    · throw 立即中断当前流程
▼
异常沿调用链向外传播
│  · 穿透任意多层辅助方法，无需在方法签名上声明
│  · 跳过抛出点之后的全部语句（因此后面的写文件步骤不会执行）
▼
Repository.run 中的 catch (GitletException e) 接住
│  · 这是全程序唯一的错误打印出口
│  · Utils.message(e.getMessage())   逐字打印消息（带换行）
│  · System.exit(0)                  立即结束，参数固定为 0
▼
程序终止
```

- 全程序只有 `catch` 块这一处打印错误消息，消息全部取自第 1 节清单，不在其他地方零散打印，保证逐字一致、且不打印多余内容。
- 入口管线与 `try/catch` 都位于 Repository 的 `run` 方法；Main 极薄，只负责把 `args` 转交给 `run`，命令的检查与分发一律在 Repository 完成（见 [01 总体架构](01-architecture.md)）。

## 3. 失败原子性

失败原子性要求一条命令只有两种结果：要么完整成功，要么如同从未运行，不存在做了一半的中间状态。失败后，受保护的状态必须与命令运行前逐字节相同。

**保护范围**

需要保护的状态共五项：working directory、staging area、`.gitlet`、branch pointer、HEAD。命令失败时，这五项都不得发生变化。只读命令（如 `log`、`global-log`、`status`）不写入任何一项，天然满足原子性。

**统一执行顺序：检查 → 内存计算 → 落盘**

- 检查阶段只读取状态，把所有可能抛出 GitletException 的业务条件全部验证完。
- 计算阶段只在内存中（对象、Map、临时变量）算出成功后应有的结果。
- 落盘阶段放在最后，确认无误后才写入。

关键纪律：任何会抛出 GitletException 的检查，都必须排在第一次写操作之前。于是一旦开始落盘，后面就不再有业务失败点，中途异常在结构上不可能发生。

**多文件写入的顺序**

当一条命令要写多个文件时，并不要求它们同时出现，而是按“是否影响可见状态”排次序（对象只增不改、状态文件整条覆盖写，见 [03 持久化与文件布局](03-persistence-and-layout.md)）：

1. 先写只增不改的新对象（blob、commit）。它们使用全新 ID、没有任何指针引用，即使提前写出，也不改变仓库的可见状态。
2. 再移动代表版本切换的分支指针。指针未动之前，仓库的当前提交仍是旧的；指针一改，版本才在这一步整体切换。
3. 最后做依附于新状态的清理，例如清空 stage。

其中第 3 步不得提前到第 2 步之前。若先清空 stage 再移动指针，两步之间会出现“stage 已空、分支指针仍指旧提交”的状态：`add` 的改动既没有进入新提交，也已从暂存区消失，无法找回。

**危险命令**

`rm`、`checkout`、`reset`、`merge` 会覆写或删除 working directory 中的文件，最容易留下半完成状态，因此它们的前置检查必须最完整。
