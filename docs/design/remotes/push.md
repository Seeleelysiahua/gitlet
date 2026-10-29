# `push`（加分项）

> 对象结构见 [02 对象模型](../02-object-model.md)，磁盘读写见 [03 持久化与文件布局](../03-persistence-and-layout.md)，错误消息与抛出机制见 [04 错误处理](../04-error-handling.md)。

## 1. 方法签名

`static void push(String remoteName, String remoteBranchName)`

## 2. 依赖数据

- `.gitlet/remotes`：remote name → 远程 `.gitlet` 目录路径的映射
- 远程 `.gitlet` 目录：存在性、`refs/heads/<remoteBranchName>` 指针文件（存在性 + 内容即远程分支 head 的 commit ID）、objects 下各对象文件的存在性
- 本地 `.gitlet`：HEAD 存的当前分支名、当前分支指针所指 commit ID、沿 parent 回溯沿途各 commit 对象（`firstParent`/`secondParent`/`filenameToBlob`）与各 blob 字节

## 3. 执行步骤

1. 读 remote 映射 → `HashMap<String, String> remotes = readRemotes()`
2. 取远程 `.gitlet` 目录路径 → `String remoteGitlet = remotes.get(remoteName)`
3. 检查远程目录不存在 `remoteGitlet == null || !Utils.join(remoteGitlet).exists()` → `throw Utils.error("Remote directory not found.")`
4. 取当前分支 head 的 commit ID → `String localHeadID = getCurrentCommitID()`
5. 构造远程分支指针文件对象 → `File remoteBranchFile = Utils.join(remoteGitlet, "refs", "heads", remoteBranchName)`
6. 若远程分支已存在，读其 head 并做快进检查（远程 head 须是本地 head 的祖先，含相等；遍历只沿本地 `COMMITS_DIR` 读双 parent，与对象是否已复制无关，不做剪枝）；不存在则跳过本检查，由步骤 8 新建分支：

    ```java
    if (remoteBranchFile.exists()) {
        String remoteHeadID = Utils.readContentsAsString(remoteBranchFile);
        if (!isAncestor(remoteHeadID, localHeadID)) {
            throw Utils.error("Please pull down remote changes before pushing.");
        }
    }
    ```

7. 复制远程尚不存在的 commit 及其引用的 blob → `copyMissingObjects(GITLET_DIR.getPath(), remoteGitlet, localHeadID)`
8. 写远程分支指针指向本地 head → `Utils.writeContents(remoteBranchFile, localHeadID)`

### isAncestor

`private static boolean isAncestor(String ancestorID, String descendantID)`：判断 `ancestorID` 是否等于 `descendantID` 或在其祖先集合中。显式栈 + `HashSet<String> visited` 去重，从 descendantID 出发沿 `getFirstParent()`/`getSecondParent()` 双 parent 遍历（commit 文件经 `Utils.join(COMMITS_DIR, id)` 读），命中 ancestorID 返回 true，栈空返回 false。登记于本卡，方法体位于单卡私有 helper 区。

## 4. 复用与被复用

- **调用的 Utils 方法**：`join`、`error`、`readContentsAsString`、`writeContents`
- **调用的对象接口**：无（parent 遍历封在 isAncestor 内，经 getter 访问）
- **被谁调用**：仅由 `Repository.run` 分发，当前无其他命令复用
- **新提取的辅助方法**：`private static boolean isAncestor(String ancestorID, String descendantID)`（单卡私有 helper）。调用已提取的 `readRemotes()`、`copyMissingObjects(String, String, String)`

## 5. 状态修改与失败保护

- **分界线**：两个业务检查在步骤 3 与步骤 6；本地全程不写盘，对远程的首次写在步骤 7
- **改动项**：
    - working directory：不改
    - staging area：不改
    - `.gitlet`：不改（本命令只写远程仓库）
    - branch pointer：不改（本地分支不动；移动的是远程分支指针）
    - HEAD：不改
- **失败保护**：两个业务失败（远程目录不存在、远程 head 不在本地历史中）均先于对远程的首次写；对远程的落盘序为先只增复制对象、后写分支指针 —— 复制中途崩溃远程只留无引用的只增孤儿对象、指针未动；重跑幂等（远程已有的对象被 `exists` 跳过、指针重写到同一 ID）。本地状态全程零改动
