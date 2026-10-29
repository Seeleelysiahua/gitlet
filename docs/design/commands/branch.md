# `branch`

> 对象结构见 [02 对象模型](../02-object-model.md)，磁盘读写见 [03 持久化与文件布局](../03-persistence-and-layout.md)，错误消息与抛出机制见 [04 错误处理](../04-error-handling.md)。

## 1. 方法签名

`static void branch(String name)`

## 2. 依赖数据

- `refs/heads/` 下的分支文件
- 当前分支指针所指的 commit ID

## 3. 执行步骤

1. 构造分支文件对象 → `File branchFile = Utils.join(".gitlet", "refs", "heads", name)`
2. 检查同名分支已存在 `branchFile.exists()` → `throw Utils.error("A branch with that name already exists.")`
3. 获取当前 commit ID → `String commitID = getCurrentCommitID()`
4. 创建新分支 → `Utils.writeContents(branchFile, commitID)`

## 4. 复用与被复用

- **调用的 Utils 方法**：`join`、`writeContents`、`error`
- **调用的对象接口**：无
- **被谁调用**：仅由 `Repository.run` 分发，当前无其他命令复用
- **新提取的辅助方法**：无。调用已提取的 `getCurrentCommitID()`（commit 卡提取，见 03 §6）

## 5. 状态修改与失败保护

- **分界线**：唯一的检查在步骤 2 完成，其后步骤 3 只读 commit ID，步骤 4 首次写磁盘
- **改动项**：
    - working directory：不改
    - staging area：不改
    - `.gitlet`：不改
    - branch pointer：改（新增一个指针，指向当前 commit ID）
    - HEAD：不改
- **失败保护**：唯一的业务错误在步骤 2 抛出、先于写盘，失败时零改动

## 6. 性能核对

- **spec 要求**：runtime 相对于任何 significant measure 为常数；未单列 memory 要求
- **达标分析**：
    - runtime：`exists()` 直接检查单个路径，全程为常数
    - memory：未单列；峰值为一个 `File` 引用与一个 commit ID 字符串，均为定长
