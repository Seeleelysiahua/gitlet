# `checkout`

> 对象结构见 [02 对象模型](../02-object-model.md)，磁盘读写见 [03 持久化与文件布局](../03-persistence-and-layout.md)，错误消息与抛出机制见 [04 错误处理](../04-error-handling.md)。

## 1. 方法签名

`static void checkout(String[] operands)`

- 用法一：`operands = -- [file name]`
- 用法二：`operands = [commit id] -- [file name]`
- 用法三：`operands = [branch name]`

三个私有分派 helper：

用法一：`private static void checkoutFile(String filename)`
用法二：`private static void checkoutCommit(String givenID, String filename)`
用法三：`private static void checkoutBranch(String branchName)`

用法一/二共用私有 helper：`private static void checkoutFileFromCommit(Commit commit, String filename)`

## 2. 依赖数据

### 用法一

- current commit：文件名 → blob ID 的映射
- 目标 blob：文件字节内容

### 用法二

- 参数 givenID 对应的 commit：文件名 → blob ID 的映射
- 目标 blob：文件字节内容
- objects/commits/ 目录列表（缩写解析）

### 用法三

- HEAD：当前分支名
- refs/heads：目标分支指针所指 commit
- 目标 commit 的文件名 → blob ID 映射
- current commit 的文件名 → blob ID 映射
- 目标 commit 所含各 blob 的字节内容
- 工作目录全部普通文件名
- stage

## 3. 执行步骤

1. 根据传入的 `operands` 中的元素个数与 `--` 的位置调用辅助函数：

    ```java
    if (operands.length == 2){
        if (!operands[0].equals("--")){
            throw Utils.error("Incorrect operands.");
        }
        else{
            checkoutFile(operands[1]);
        }
    }
    else if (operands.length == 3){
        if (!operands[1].equals("--")){
            throw Utils.error("Incorrect operands.");
        }
        else{
            checkoutCommit(operands[0], operands[2]);
        }
    }
    else if (operands.length == 1){
        checkoutBranch(operands[0]);
    }
    else{
        throw Utils.error("Incorrect operands.");
    }
    ```

### 用法一

1. 取当前 commit 对象 → `Commit currentCommit = getCurrentCommit()`
2. 调共用 helper 恢复文件 → `checkoutFileFromCommit(currentCommit, filename)`

`checkoutFileFromCommit` 为用法一/二共用的私有 helper：

1. 取映射 → `Map<String, String> filenameToBlob = commit.getFilenameToBlob()`
2. 取 blob ID → `String blobID = filenameToBlob.get(filename)`
3. 检查目标文件名不存在于该 commit 中 `blobID == null` → `throw Utils.error("File does not exist in that commit.")`
4. 读取 blob 内容 → `byte[] fileContent = Utils.readContents(Utils.join(".gitlet", "objects", "blobs", blobID))`
5. 写入工作目录（存在则覆盖） → `Utils.writeContents(new File(filename), fileContent)`

### 用法二

1. 解析缩写 ID 得完整 commit ID → `String commitID = resolveCommitID(givenID)`

    `resolveCommitID` 为跨命令辅助方法（checkout 与 reset 共用，见 03 §6），定义如下：

    ```java
    static String resolveCommitID(String givenID) {
        List<String> allCommitID = Utils.plainFilenamesIn(Utils.join(".gitlet", "objects", "commits"));
        String commitID = null;
        for (String id : allCommitID) {
            if (id.startsWith(givenID.toLowerCase())) {
                if (commitID != null) {
                    throw Utils.error("No commit with that id exists.");
                }
                commitID = id;
            }
        }
        if (commitID == null) {
            throw Utils.error("No commit with that id exists.");
        }
        return commitID;
    }
    ```

2. 取目标 commit 对象 → `Commit targetedCommit = Utils.readObject(Utils.join(".gitlet", "objects", "commits", commitID), Commit.class)`
3. 调共用 helper 恢复文件 → `checkoutFileFromCommit(targetedCommit, filename)`

### 用法三

1. 获取所有分支名 → `List<String> allBranchName = Utils.plainFilenamesIn(Utils.join(".gitlet", "refs", "heads"))`
2. 检查目标分支不存在 `!allBranchName.contains(branchName)` → `throw Utils.error("No such branch exists.")`
3. 获取当前分支名 → `String currentBranchName = getCurrentBranchName()`
4. 检查目标分支是当前分支 `currentBranchName.equals(branchName)` → `throw Utils.error("No need to checkout the current branch.")`
5. 获取目标分支的 current commit ID → `String commitID = Utils.readContentsAsString(Utils.join(".gitlet", "refs", "heads", branchName))`
6. 获取当前 commit 对象 → `Commit currentCommit = getCurrentCommit()`
7. 获取目标分支的 current commit 对象 → `Commit targetedCommit = Utils.readObject(Utils.join(".gitlet", "objects", "commits", commitID), Commit.class)`
8. 将工作目录整体替换为目标 commit 的追踪文件 → `replaceWorkingDir(currentCommit, targetedCommit)`

    `replaceWorkingDir` 为跨命令辅助方法（checkout 与 reset 共用，见 03 §6），定义如下：

    ```java
    static void replaceWorkingDir(Commit currentCommit, Commit targetedCommit) {
        StagingArea stage = Utils.readObject(Utils.join(".gitlet", "stage"), StagingArea.class);
        List<String> allFileName = Utils.plainFilenamesIn(".");
        for (String name : allFileName){
            if (stage.addition.containsKey(name)){
                continue;
            }
            if (currentCommit.filenameToBlob.containsKey(name) && !stage.removal.contains(name)){
                continue;
            }
            if (targetedCommit.filenameToBlob.containsKey(name)){
                throw Utils.error("There is an untracked file in the way; delete it, or add and commit it first.");
            }
        }
        for (Map.Entry<String, String> e : targetedCommit.filenameToBlob.entrySet()){
            String afileName = e.getKey();
            String aBlobID = e.getValue();
            byte[] afileContent = Utils.readContents(Utils.join(".gitlet", "objects", "blobs", aBlobID));
            Utils.writeContents(new File(afileName), afileContent);
        }
        for (String name : allFileName){
            if (!targetedCommit.filenameToBlob.containsKey(name) && currentCommit.filenameToBlob.containsKey(name)) {
                Utils.restrictedDelete(name);
            }
        }
    }
    ```

9. 更新 HEAD → `Utils.writeContents(Utils.join(".gitlet", "HEAD"), branchName)`
10. 清空 stage → `Utils.writeObject(Utils.join(".gitlet", "stage"), new StagingArea())`

## 4. 复用与被复用

- **调用的 Utils 方法**：`plainFilenamesIn`、`join`、`readContents`、`writeContents`、`readContentsAsString`、`readObject`、`writeObject`、`restrictedDelete`、`error`
- **调用的对象接口**：
    - Commit：读 `filenameToBlob`（`containsKey`/`get`/`entrySet`）
    - StagingArea：读 `addition`、`removal`；`new StagingArea()` 无参构造造空 stage 覆盖写入
- **被谁调用**：仅由 `Repository.run` 按操作数形态分发到本卡三个私有分派分支（单文件恢复再经共用 `checkoutFileFromCommit`），当前无其他命令复用
- **新提取的辅助方法**：三个私有分派 helper `checkoutFile(String)`、`checkoutCommit(String, String)`、`checkoutBranch(String)` 与共用私有 helper `checkoutFileFromCommit(Commit, String)`，仅服务本卡；跨命令 helper `resolveCommitID(String)`（缩写 ID 解析，返回唯一完整 ID）与 `replaceWorkingDir(Commit, Commit)`（工作目录整体替换，挡路检查封在内部）为包级私有，checkout 与 reset 共用，登记于 03 §6/接口清单

## 5. 状态修改与失败保护

- **分界线**：用法一/二在文件存在性检查（用法二另有缩写解析）处完成检查，其后只读对象、最后一步首次写磁盘；用法三所有检查在步骤 2–8 完成（挡路检查在 `replaceWorkingDir` 内部先于写删），从步骤 8 的工作目录写入起首次写磁盘
- **改动项**：
    - working directory：改（用法一/二覆盖写单个文件；用法三按目标 commit 覆盖或新建文件、删除目标不追踪而 current commit 追踪的文件）
    - staging area：不改（用法一/二）；改（用法三清空）
    - `.gitlet`：不改
    - branch pointer：不改
    - HEAD：不改（用法一/二）；改（用法三指向目标分支名）
- **失败保护**：业务错误全部在首次写盘前抛出，失败时零改动。用法三落盘按写工作文件、删文件、写 HEAD、清 stage 顺序推进，系统级崩溃可能停在半切换：
    - 工作文件已写入和删除、HEAD 未移动：仓库仍属原分支，重试 `checkout` 即可完成切换分支
    - HEAD 已移动、stage 未清空：分支已切到目标分支，stage 残留旧分支条目，重试 `checkout` 会在当前分支检查处退出。条目所指 blob 在 `add` 时已写入 objects，数据不丢；仓库可经 `reset` 到同一 commit ID 修复（已由 reset 卡核实：reset 无“当前分支”检查，重试时残留 stage 条目在挡路检查中放行，可完成清空恢复一致）

## 6. 性能核对

- **spec 要求**：用法一 runtime 相对于被检出文件的大小为线性；用法二、用法三 runtime 相对于该 commit 快照中文件的总大小为线性，对任何涉及 commit 数量的度量为常数，对分支数量为常数；使用缩写 ID 的命令豁免计时要求；未单列 memory 要求
- **达标分析**：
    - runtime：
        - 用法一：读、写一个 blob，读写量等于该文件大小，对文件大小线性
        - 用法二：读、写一个 blob，对文件大小线性；直接通过 ID 读出 commit，对 commit 数常数；缩写前缀匹配需列 `objects/commits/` 并扫描，对 commit 数线性，按规豁免
        - 用法三：读、写目标 commit 的映射条目记录的所有 blob，对该 commit 快照中文件的总大小为线性；根据分支名读取 commit ID，对分支数量为常数；直接通过 ID 读出 commit，对 commit 数常数
    - memory：未单列；峰值为单个 blob 的字节内容，外加目录文件名列表、两个 commit 与一个 stage 对象，规模只随当前快照与工作目录增长
