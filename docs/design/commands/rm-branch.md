# `rm-branch`

> 对象结构见 [02 对象模型](../02-object-model.md)，磁盘读写见 [03 持久化与文件布局](../03-persistence-and-layout.md)，错误消息与抛出机制见 [04 错误处理](../04-error-handling.md)。

## 1. 方法签名

`static void rmBranch(String branchName)`

## 2. 依赖数据

- `refs/heads/` 下的分支文件
- HEAD 中的当前分支名

## 3. 执行步骤

1. 构造分支文件对象 → `File branchFile = Utils.join(".gitlet", "refs", "heads", branchName)`
2. 检查待删除的分支不存在 `!branchFile.exists()` → `throw Utils.error("A branch with that name does not exist.")`
3. 获取当前分支名 → `String currentBranchName = getCurrentBranchName()`
4. 检查待删除的分支是当前分支 `currentBranchName.equals(branchName)` → `throw Utils.error("Cannot remove the current branch.")`
5. 删除分支文件 → `branchFile.delete()`

    注意：不能用 `Utils.restrictedDelete`。该方法要求被删文件所在目录含 `.gitlet`（见 Utils.java），只为保护工作目录文件而设计；分支指针是 `.gitlet/refs/heads/` 下的内部文件，用它会在运行期抛 `IllegalArgumentException`。内部文件直接 `File.delete()`。

## 4. 复用与被复用

- **调用的 Utils 方法**：`join`、`error`（删除分支用原生 `File.delete()`，不走 Utils）
- **调用的对象接口**：无
- **被谁调用**：仅由 `Repository.run` 分发，当前无其他命令复用
- **新提取的辅助方法**：无。调用已提取的 `getCurrentBranchName()`（commit 卡提取，见 03 §6）

## 5. 状态修改与失败保护

- **分界线**：两个检查在步骤 2、4 完成，步骤 5 首次写磁盘
- **改动项**：
    - working directory：不改
    - staging area：不改
    - `.gitlet`：不改
    - branch pointer：改（删除一个指针）
    - HEAD：不改
- **失败保护**：两个业务错误都先于删除抛出，失败时零改动

## 6. 性能核对

- **spec 要求**：runtime 相对于任何 significant measure 为常数；未单列 memory 要求
- **达标分析**：
    - runtime：`exists()` 直接检查单个路径，删除单个文件，全程为常数
    - memory：未单列；峰值为一个 `File` 引用与一个分支名字符串
