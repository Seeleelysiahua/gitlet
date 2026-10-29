# `pull`（加分项）

> 对象结构见 [02 对象模型](../02-object-model.md)，磁盘读写见 [03 持久化与文件布局](../03-persistence-and-layout.md)，错误消息与抛出机制见 [04 错误处理](../04-error-handling.md)。

## 1. 方法签名

`static void pull(String remoteName, String remoteBranchName)`

## 2. 依赖数据

- 本卡无新增依赖：fetch 段的依赖见 fetch 卡，merge 段的依赖见 merge 卡

## 3. 执行步骤

1. 拉取 → `fetch(remoteName, remoteBranchName)`
2. 拼远程跟踪分支名 → `String trackingBranch = remoteName + "/" + remoteBranchName`
3. 合并取回的分支 → `merge(trackingBranch)`

失败消息全部由 `fetch` 与 `merge` 各自的检查行沿汇报链抛出，本卡无检查行。

## 4. 复用与被复用

- **调用的 Utils 方法**：无（分支名拼接为 String 运算）
- **调用的对象接口**：无
- **被谁调用**：仅由 `Repository.run` 分发，当前无其他命令复用
- **新提取的辅助方法**：无。本卡为 `fetch` + `merge` 的命令级拼装；为支持合并远程跟踪分支，merge 卡的分支解析已扩展为双目录

## 5. 状态修改与失败保护

- **分界线**：本卡无检查无写盘；检查与写盘全在 fetch、merge 各自段内，各自分界线不变
- **改动项**：
    - working directory：改（merge 段）
    - staging area：改（merge 段）
    - `.gitlet`：改（fetch 段新增缺失对象；merge 段新增 merge commit 与冲突 blob）
    - branch pointer：改（fetch 段更新 `refs/remotes/<remote>/<branch>`；merge 段移动当前分支指针）
    - HEAD：不改
- **失败保护**：fetch 段失败则全程零改动；merge 段失败则 fetch 段的只增对象与远程跟踪指针保留，且 fetch 重跑幂等、merge 段原子性同 merge 卡
