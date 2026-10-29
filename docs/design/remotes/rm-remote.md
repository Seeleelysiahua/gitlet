# `rm-remote`（加分项）

> 对象结构见 [02 对象模型](../02-object-model.md)，磁盘读写见 [03 持久化与文件布局](../03-persistence-and-layout.md)，错误消息与抛出机制见 [04 错误处理](../04-error-handling.md)。

## 1. 方法签名

`static void rmRemote(String remoteName)`

## 2. 依赖数据

- `.gitlet/remotes` 单一状态文件：remote name → 远程 `.gitlet` 目录路径的映射（读不到当空映射）
- 操作数 remote 名为纯字面值，不列为依赖

## 3. 执行步骤

1. 读 remote 映射 → `HashMap<String, String> remotes = readRemotes()`
2. 检查 remote 名不存在 `!remotes.containsKey(remoteName)` → `throw Utils.error("A remote with that name does not exist.")`
3. 删除该条映射 → `remotes.remove(remoteName)`
4. 整条覆盖写回 remotes → `Utils.writeObject(Utils.join(".gitlet", "remotes"), remotes)`

## 4. 复用与被复用

- **调用的 Utils 方法**：`error`、`join`、`writeObject`（helper 内 Utils 不重复列）
- **调用的对象接口**：无（remotes 是裸 `HashMap<String, String>` 整体序列化，无对象类）
- **被谁调用**：仅由 `Repository.run` 分发，当前无其他命令复用
- **新提取的辅助方法**：无。调用 add-remote 卡提取的 `readRemotes()`（见 03 §6）

## 5. 状态修改与失败保护

- **分界线**：唯一业务检查在步骤 2，首次写盘在步骤 4
- **改动项**：
    - working directory：不改
    - staging area：不改
    - `.gitlet`：改（remotes 删除一条映射）
    - branch pointer：不改
    - HEAD：不改
- **失败保护**：唯一业务失败（名不存在）先于唯一写盘步，失败零改动；单步整条覆盖写，无多步落盘、无崩溃窗口
