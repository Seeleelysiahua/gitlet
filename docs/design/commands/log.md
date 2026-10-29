# `log`

> 对象结构见 [02 对象模型](../02-object-model.md)，磁盘读写见 [03 持久化与文件布局](../03-persistence-and-layout.md)，错误消息与抛出机制见 [04 错误处理](../04-error-handling.md)。

## 1. 方法签名

`static void log()`

## 2. 依赖数据

- 起点：current commit（回溯链起点）及其 commit ID
- 回溯链：从 current commit 沿 `firstParent` 到 initial commit 的每个 commit（含两端）；每个 commit 读 `message`、`timestamp`、`firstParent`、`secondParent`
- merge commit：`secondParent` 非空时，取两个 parent ID 的前 7 位用于 `Merge:` 行

## 3. 执行步骤

1. 定义打印单个 commit 一块的辅助方法 `printCommit`（global-log 复用，见 03 §6）：

    ```java
    static void printCommit(Commit commit, String commitID) {
        Utils.message("===");
        Utils.message("commit %s", commitID);
        if (commit.secondParent != null) {
            Utils.message("Merge: %s %s",
                commit.firstParent.substring(0, 7),
                commit.secondParent.substring(0, 7));
        }
        String dateStr = new SimpleDateFormat(
            "EEE MMM d HH:mm:ss yyyy Z", Locale.US).format(commit.timestamp);
        Utils.message("Date: %s", dateStr);
        Utils.message(commit.message);
        Utils.message("");
    }
    ```

2. 取当前 commit ID → `String commitID = getCurrentCommitID()`
3. 取当前 commit 对象 → `Commit currentCommit = getCurrentCommit()`
4. 沿 `firstParent` 循环，commit ID 非 null 即进入；每轮先调 `printCommit` 打印当前节点，再推进到 `firstParent`，直到无 parent：

    ```java
    while (commitID != null) {
        printCommit(currentCommit, commitID);
        commitID = currentCommit.firstParent;
        if (commitID != null) {
            currentCommit = Utils.readObject(
                Utils.join(".gitlet", "objects", "commits", commitID),
                Commit.class);
        }
    }
    ```

## 4. 复用与被复用

- **调用的 Utils 方法**：`message`、`readObject`、`join`
- **调用的 JDK API**：`new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US).format(timestamp)` 格式化时间（`d` 不补零、英文缩写、本地时区）。注意日期模式必须交给 `SimpleDateFormat`，不能交给 `String.format`（后者只认 `%` 转换符，会把模式串原样输出）
- **调用的对象接口**：
    - Commit：读字段 `message`、`timestamp`、`firstParent`、`secondParent`
- **被谁调用**：`printCommit` 由本命令与 `global-log` 调用；`log` 本身仅由 `Repository.run` 分发
- **新提取的辅助方法**：`static void printCommit(Commit commit, String commitID)` —— 按 log 版式打印单个 commit 一块（含 merge commit 的 `Merge:` 行、块后空行），本卡首次提取（见 03 §6）；另调用 commit 卡已提取的 `getCurrentCommitID()`、`getCurrentCommit()`

## 5. 状态修改与失败保护

- **分界线**：本命令全程只读，无检查行、无写盘步骤，不设分界线
- **改动项**：
    - working directory：不改
    - staging area：不改
    - `.gitlet`：不改
    - branch pointer：不改
    - HEAD：不改
- **失败保护**：无命令专属失败情况，亦无写盘，无失败窗口与残留，无需失败保护

## 6. 性能核对

- **spec 要求**：runtime 相对于 current commit 沿 first parent 回溯所得的 commit 节点数为线性；未单列 memory 要求
- **达标分析**：
    - runtime：沿 `firstParent` 逐节点推进，每节点读一次对象、打印固定行数，皆与历史总量无关的常数
    - memory：循环只持当前一个 Commit 对象及其 ID，峰值为单个 commit 对象，不随历史总量增长
