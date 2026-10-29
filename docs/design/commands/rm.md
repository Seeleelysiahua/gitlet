# `rm`

> 对象结构见 [02 对象模型](../02-object-model.md)，磁盘读写见 [03 持久化与文件布局](../03-persistence-and-layout.md)，错误消息与抛出机制见 [04 错误处理](../04-error-handling.md)。

## 1. 方法签名

`static void rm(String fileName)`

## 2. 依赖数据

- 参数 fileName
- stage：其中的 addition 映射与 removal 集合
- current commit：文件名 → blob ID 的映射

## 3. 执行步骤

1. 读取 stage → `StagingArea stage = Utils.readObject(Utils.join(".gitlet", "stage"), StagingArea.class)`
2. 读取当前 commit 对象 → `Commit currentCommit = getCurrentCommit()`
3. 检查目标文件既不在 addition 中，也不在 current commit 的映射中 `!stage.addition.containsKey(fileName) && !currentCommit.filenameToBlob.containsKey(fileName)` → `throw Utils.error("No reason to remove the file.")`
4. 把该文件移出 addition → `stage.addition.remove(fileName)`
5. 目标文件在 current commit 的映射中 `currentCommit.filenameToBlob.containsKey(fileName)`：

    - 把文件名加进 removal → `stage.removal.add(fileName)`
    - 删除目标文件 → `Utils.restrictedDelete(fileName)`

6. 写回 stage → `Utils.writeObject(Utils.join(".gitlet", "stage"), stage)`

## 4. 复用与被复用

- **调用的 Utils 方法**：`readObject`、`join`、`error`、`restrictedDelete`、`writeObject`
- **调用的对象接口**：
    - Commit：读字段 `filenameToBlob`（调用 `containsKey`）
    - StagingArea：读改字段 `addition`（`containsKey`/`remove`）、`removal`（`add`）
- **被谁调用**：仅由 `Repository.run` 分发，当前无其他命令复用
- **新提取的辅助方法**：无。调用已提取的 `getCurrentCommit()`（commit 卡提取，见 03 §6）

## 5. 状态修改与失败保护

- **分界线**：唯一的检查在第 3 步完成；第 4 步只改内存对象，不写盘；从第 5 步起首次写磁盘（先删工作文件，第 6 步再写 stage）
- **改动项**：
  - working directory：改（文件被 current commit 追踪：删除该工作文件）
  - staging area：改（addition 移除、removal 加入，整条覆盖写回）
  - `.gitlet`：改（仅 stage 文件覆盖写，无新增对象）
  - branch pointer：不改
  - HEAD：不改
- **失败保护**：落盘分两步，先删目标文件、再覆盖写 stage。先删后写，中途失败导致文件已删而 removal 未标记，该文件仍被 current commit 追踪，可恢复；反向顺序若删除失败，会在无报错的情况下于下次 commit 丢失目标文件

## 6. 性能核对

- **spec 要求**：runtime 相对于任何 significant measure 为常数；未单列 memory、`.gitlet` 增长要求
- **达标分析**：
    - runtime：对 addition、removal、current commit 的操作皆常数；stage 读写不随历史总量增长
    - memory：未单列；峰值为单个 stage 对象，仅随当前暂存条目数变化

