# `global-log`

> 对象结构见 [02 对象模型](../02-object-model.md)，磁盘读写见 [03 持久化与文件布局](../03-persistence-and-layout.md)，错误消息与抛出机制见 [04 错误处理](../04-error-handling.md)。

## 1. 方法签名

`static void globalLog()`

## 2. 依赖数据

- `objects/commits/` 目录：列出其中全部文件名（每个文件名即一个 commit ID）
- 每个 commit：按文件名从 `objects/commits/<ID>` 反序列化出 Commit，读 `message`、`timestamp`、`firstParent`、`secondParent`

## 3. 执行步骤

1. 读取 `objects/commits/` 目录中所有文件名 → `List<String> allCommitIDs = Utils.plainFilenamesIn(Utils.join(".gitlet", "objects", "commits"))`
2. 循环打印每一个 commit：

    ```java
    for (String id : allCommitIDs) {
        Commit commit = Utils.readObject(
            Utils.join(".gitlet", "objects", "commits", id),
            Commit.class);
        printCommit(commit, id);
    }
    ```

## 4. 复用与被复用

- **调用的 Utils 方法**：`plainFilenamesIn`、`join`、`readObject`
- **调用的对象接口**：
    - Commit：经 `readObject` 按 ID 反序列化读出；字段读取封在 `printCommit` 内，本卡不直接访问
- **被谁调用**：仅由 `Repository.run` 分发，当前无其他命令复用
- **新提取的辅助方法**：无。调用 log 卡提取的 `printCommit(Commit, String)`（见 03 §6），本卡为其第二个调用方

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

- **spec 要求**：runtime 相对于所有曾经创建的 commit 数量为线性；未单列 memory 要求
- **达标分析**：
    - runtime：`plainFilenamesIn` 列一次 `objects/commits/`（对文件数线性），再对每个 commit 按 ID `readObject` 一次、`printCommit` 打印固定行数；合计对全部 commit 数线性，满足 spec
    - memory：未单列；峰值为全部 commit ID 字符串的列表（对 commit 数线性）外加循环内单个 Commit 对象
