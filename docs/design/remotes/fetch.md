# `fetch`（加分项）

> 对象结构见 [02 对象模型](../02-object-model.md)，磁盘读写见 [03 持久化与文件布局](../03-persistence-and-layout.md)，错误消息与抛出机制见 [04 错误处理](../04-error-handling.md)。

## 1. 方法签名

`static void fetch(String remoteName, String remoteBranchName)`

## 2. 依赖数据

- `.gitlet/remotes`：remote name → 远程 `.gitlet` 目录路径的映射
- 远程 `.gitlet` 目录：存在性、`refs/heads/<remoteBranchName>` 指针文件、沿 parent 回溯沿途各 commit 对象（`firstParent`/`secondParent`/`filenameToBlob`）与各 blob 字节
- 本地 `.gitlet`：objects/commits 与 objects/blobs 下各对象文件的存在性

## 3. 执行步骤

1. 读 remote 映射 → `HashMap<String, String> remotes = readRemotes()`
2. 取远程 `.gitlet` 目录路径 → `String remoteGitlet = remotes.get(remoteName)`
3. 检查远程目录不存在 `remoteGitlet == null || !Utils.join(remoteGitlet).exists()` → `throw Utils.error("Remote directory not found.")`
4. 构造远程分支指针文件对象 → `File remoteBranchFile = Utils.join(remoteGitlet, "refs", "heads", remoteBranchName)`
5. 检查远程分支不存在 `!remoteBranchFile.exists()` → `throw Utils.error("That remote does not have that branch.")`
6. 读远程分支 head 的 commit ID → `String remoteHeadID = Utils.readContentsAsString(remoteBranchFile)`
7. 复制本地尚不存在的 commit 及其引用的 blob → `copyMissingObjects(remoteGitlet, GITLET_DIR.getPath(), remoteHeadID)`
8. 建远程跟踪分支目录 → `Utils.join(".gitlet", "refs", "remotes", remoteName).mkdirs()`
9. 写远程跟踪分支指针 → `Utils.writeContents(Utils.join(".gitlet", "refs", "remotes", remoteName, remoteBranchName), remoteHeadID)`

### copyMissingObjects

`static void copyMissingObjects(String srcGitlet, String dstGitlet, String headID)`：从源仓库向目标仓库复制从 headID 沿 parent 可达且目标尚不存在的 commit 及其引用的 blob，遇目标已有 commit 即整支剪枝。src/dst 均为 `.gitlet` 目录路径串：运行时读表得到的远程路径直接用，本地侧传 `GITLET_DIR.getPath()`（fetch 远程 → 本地，push 本地 → 远程，方向相反共用同一方法）：

```java
static void copyMissingObjects(String srcGitlet, String dstGitlet, String headID) {
    ArrayDeque<String> stack = new ArrayDeque<>();
    stack.push(headID);

    while (!stack.isEmpty()) {
    
        String id = stack.pop();

        File dstCommit = Utils.join(dstGitlet, "objects", "commits", id);

        if (dstCommit.exists()) {
            continue;
        }
        Commit commit = Utils.readObject(Utils.join(srcGitlet, "objects", "commits", id), Commit.class);
        Utils.writeObject(dstCommit, commit);
        for (String blobID : commit.filenameToBlob.values()) {
            File dstBlob = Utils.join(dstGitlet, "objects", "blobs", blobID);
            if (!dstBlob.exists()) {
                Utils.writeContents(dstBlob,
                    Utils.readContents(Utils.join(srcGitlet, "objects", "blobs", blobID)));
            }
        }
        if (commit.firstParent != null) {
            stack.push(commit.firstParent);
        }
        if (commit.secondParent != null) {
            stack.push(commit.secondParent);
        }
    }
}
```

## 4. 复用与被复用

- **调用的 Utils 方法**：`join`、`error`、`readContentsAsString`、`writeContents`
- **调用的对象接口**：无
- **被谁调用**：由 `Repository.run` 分发，并被 `pull` 复用
- **新提取的辅助方法**：`static void copyMissingObjects(String srcGitlet, String dstGitlet, String headID)`

## 5. 状态修改与失败保护

- **分界线**：两个业务检查在步骤 3/5，全程只读；首次写盘在步骤 7（只增复制）
- **改动项**：
    - working directory：不改
    - staging area：不改
    - `.gitlet`：改（新增本地缺失的 commit 与 blob 对象）
    - branch pointer：改（新增或更新 `refs/remotes/<remote>/<branch>` 指针）
    - HEAD：不改
- **失败保护**：两个业务失败（远程目录/远程分支不存在）均先于首次写盘；落盘序为先只增复制对象、再建目录、最后写指针 —— 复制中途崩溃只留无引用的只增孤儿对象、指针未动，远程仓库全程不被写；重跑幂等
