# `commit`

> 对象结构见 [02 对象模型](../02-object-model.md)，磁盘读写见 [03 持久化与文件布局](../03-persistence-and-layout.md)，错误消息与抛出机制见 [04 错误处理](../04-error-handling.md)。

## 1. 方法签名

`static void commit(String message)`

## 2. 依赖数据

- stage：其中的 addition 映射与 removal 集合。
- HEAD：当前分支的名字。
- 当前分支指针：所指 commit 的 ID。
- current commit：文件名 → blob ID 的映射，current commit 自身的 ID。

## 3. 执行步骤

`commit` 只有一步：委托给共用提交 helper → `makeCommit(message, null)`。

### makeCommit

`makeCommit(message, secondParentID)`：提交当前 stage 的共用实现。`commit` 传 `null`（普通单 parent 提交）；`merge` 传被合并分支 head 的 commit ID（merge commit 双 parent，见 [02 对象模型](../02-object-model.md)）。以下 16 步即该 helper 的方法体：

1. 检查 message 为空或仅包含空白字符 `message.isBlank()` → `throw Utils.error("Please enter a commit message.")`
2. 读取 stage → `StagingArea stage = Utils.readObject(Utils.join(".gitlet", "stage"), StagingArea.class)`
3. 检查 stage 为空 `stage.addition.isEmpty() && stage.removal.isEmpty()` → `throw Utils.error("No changes added to the commit.")`
4. 定位 current commit：

    - 取当前分支名 → `String branchName = getCurrentBranchName()`
    - 取当前 commit ID → `String commitID = getCurrentCommitID()`
    - 取当前 commit 对象 → `Commit currentCommit = getCurrentCommit()`

5. 构造新 Map 对象 → `Map<String, String> newMap = new HashMap<>(currentCommit.filenameToBlob)`
6. 根据 stage 的 removal 更新 `newMap` → `newMap.keySet().removeAll(stage.removal)`
7. 根据 stage 的 addition 更新 `newMap` → `newMap.putAll(stage.addition)`
8. 计算 timestamp → `Date timestamp = new Date()`
9. 构造新 commit 对象 → `Commit newCommit = new Commit(message, timestamp, commitID, secondParentID, newMap)`
10. 构造计算 commit ID 所需的列表 → `List<Object> sha1Vals = new ArrayList<>()`
11. 获取排序后的 `newMap` → `TreeMap<String, String> sortedMap = new TreeMap<>(newMap)`
12. 填充 `sha1Vals` →

    ```java
    sha1Vals.add("commit");
    sha1Vals.add(message);
    sha1Vals.add(String.valueOf(timestamp.getTime()));
    sha1Vals.add(commitID);
    sha1Vals.add(secondParentID == null ? "" : secondParentID);
    for (Map.Entry<String, String> entry : sortedMap.entrySet()) {
        sha1Vals.add(entry.getKey());
        sha1Vals.add(entry.getValue());
    }
    ```

13. 计算新 commit ID → `String newCommitID = Utils.sha1(sha1Vals)`
14. 写 commit → `Utils.writeObject(Utils.join(".gitlet","objects","commits",newCommitID), newCommit)`
15. 写 `branchName` 指针 → `Utils.writeContents(Utils.join(".gitlet", "refs", "heads", branchName), newCommitID)`
16. 写 stage → `Utils.writeObject(Utils.join(".gitlet", "stage"), new StagingArea())`

## 4. 复用与被复用

- **调用的 Utils 方法**：`error`、`readObject`、`sha1`、`writeContents`、`writeObject`、`join`
- **调用的对象接口**：
    - Commit：读字段 `filenameToBlob`、五参构造
    - StagingArea：无参构造；读字段 `addition`、`removal`，不修改读出的对象（清空靠写入新建的空对象）
- **被谁调用**：`commit` 仅由 `Repository.run` 分发；`makeCommit` 由 `commit` 与 `merge` 调用
- **新提取的辅助方法**：
    - `static String getCurrentBranchName()` —— 读 HEAD，得当前分支名
    - `static String getCurrentCommitID()` —— 调 `getCurrentBranchName()`，读 refs/heads 该分支指针，得 commit ID
    - `static Commit getCurrentCommit()` —— 调 `getCurrentCommitID()`，按 ID 从 objects/commits 反序列化出 current commit
    - `static void makeCommit(String message, String secondParentID)` —— 提交当前 stage 的共用实现（本卡 §3 的 16 步即其方法体），merge 卡触发提取（同一逻辑片段第二张卡）；`secondParentID` 为 `null` 时按 02 折 `""` 参与哈希（见 03 §6）

## 5. 状态修改与失败保护

- **分界线**：所有检查在 makeCommit 第 3 步完成；其后只在内存构造与计算，从第 14 步起首次写磁盘
- **改动项**：
  - working directory：不改
  - staging area：改（覆盖写回 stage）
  - `.gitlet`：改（新增一个 commit 对象）
  - branch pointer：改（当前分支指针指向新 commit）
  - HEAD：不改
- **失败保护**：落盘阶段先写 commit、再移动指针、最后覆盖写回 stage。按失败位置分两种残留：
    - commit 已写完、指针移动前（含移动中）失败：仓库残留一个无引用的 commit
    - 指针已前移、stage 清空前（含清空中）失败：指针已指向新 commit，stage 里还留着这批条目。重跑 `commit` 只可能产生一个映射相同的冗余 commit，不损坏仓库、不丢数据

## 6. 性能核对

- **spec 要求**：
    1. runtime 相对任何以 commit 数量计的度量均为常数
    2. runtime 相对本次提交所追踪文件的总大小不劣于线性
    3. memory：提交后 `.gitlet` 的增量不超过提交当时 staged for addition 文件的总大小，不计额外元数据
- **达标分析**：
    1. 通过 `getCurrentCommit()` 得到 current commit，全程不读第二个历史 commit、不遍历提交历史，故 runtime 与 commit 数量无关（为常数）
    2. 复制/更新映射和排序都只随本次快照的文件数及文件名、blob ID 长度线性增长，因此相对本次提交所追踪文件的总大小不劣于线性
    3. 未改文件 ID 不变，新映射指向已有 blob，不重复复制内容，因此新增内容字节仅来自 staged for addition 文件；且其 blob 已在 `add` 时写入，本命令不写 blob；新 commit 多出的仅是文件名 → blob ID 映射条目，属 spec 明确不计入增量上限的额外元数据。故 `.gitlet` 内容增量不超过 staged 文件总大小。
