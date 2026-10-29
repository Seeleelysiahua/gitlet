# `find`

> 对象结构见 [02 对象模型](../02-object-model.md)，磁盘读写见 [03 持久化与文件布局](../03-persistence-and-layout.md)，错误消息与抛出机制见 [04 错误处理](../04-error-handling.md)。

## 1. 方法签名

`static void find(String message)`

## 2. 依赖数据

- `objects/commits/` 目录：列出其中全部文件名（每个文件名即一个 commit ID）
- 每个 commit：按文件名从 `objects/commits/<ID>` 反序列化出 Commit，读 `message`

## 3. 执行步骤

1. 读取 `objects/commits/` 目录中所有文件名 → `List<String> allCommitIDs = Utils.plainFilenamesIn(Utils.join(".gitlet", "objects", "commits"))`
2. 构造判断 `matched` 变量 → `boolean matched = false`
3. 遍历每个 commit：

    ```java
    for (String id : allCommitIDs) {
        Commit commit = Utils.readObject(
            Utils.join(".gitlet", "objects", "commits", id),
            Commit.class);
        if (message.equals(commit.message)) {
            matched = true;
            Utils.message(id);
        }
    }
    ```

4. 无匹配的 commit `!matched` → `throw Utils.error("Found no commit with that message.")`

## 4. 复用与被复用

- **调用的 Utils 方法**：`plainFilenamesIn`、`join`、`readObject`、`message`、`error`
- **调用的对象接口**：
    - Commit：读 `message` 字段
- **被谁调用**：仅由 `Repository.run` 分发，当前无其他命令复用
- **新提取的辅助方法**：无

## 5. 状态修改与失败保护

- **分界线**：本命令全程只读，无写盘步骤；唯一的失败在遍历匹配完成后按 `matched` 判定，不设写盘分界线
- **改动项**：
    - working directory：不改
    - staging area：不改
    - `.gitlet`：不改
    - branch pointer：不改
    - HEAD：不改
- **失败保护**：无任何写盘，五项天然不变

## 6. 性能核对

- **spec 要求**：runtime 相对于所有曾经创建的 commit 数量为线性；未单列 memory 要求
- **达标分析**：
    - runtime：`plainFilenamesIn` 列一次 `objects/commits/`（对 commit 数线性），再对每个 commit 按 ID `readObject` 一次、做一次定长字符串 `equals` 匹配，命中则打印一行
    - memory：未单列；峰值为全部 commit ID 的列表（对 commit 数线性）外加循环内单个 Commit 对象与一个 `matched` 标记
