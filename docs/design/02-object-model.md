# 02 对象模型

> 【共享底座】本文件是对象结构的单一事实来源。

## 1. Blob

- blob 保存被追踪文件的原始字节，不保存文件名与路径。
- blob 没有专门的 Java 类，在内存中只是一个 `byte[]`，文件名和 blob 的对应关系由 Commit 中的映射维护。
- blob 的 ID 由 SHA-1 根据其原始字节得出，为 40 位十六进制字符串，文件内容相同则 ID 相同，因此多个 commit 可以引用同一个 blob，相同内容只存一份。

## 2. Commit

commit 表示某一时刻所有被追踪文件的一次快照。

一个 commit 有以下字段：

| 字段名            | 类型                    | 存储                 | 可空性                    |
| -------------- | --------------------- | ------------------ | ---------------------- |
| message        | `String`              | 提交信息文本             | 否                      |
| timestamp      | `Date`                | 提交时刻               | 否                      |
| firstParent    | `String`              | first parent 的 ID  | 是                      |
| secondParent   | `String`              | second parent 的 ID | 是                      |
| filenameToBlob | `Map<String, String>` | 文件名到 blob 的映射      | 否（initial commit 为空映射） |

三种 commit 的 parent 取值：

| commit 类型      | firstParent                       | secondParent                  |
| -------------- | --------------------------------- | ----------------------------- |
| initial commit | null                              | null                          |
| normal commit  | first parent 的 ID                 | null                          |
| merge commit   | current branch 的 branch pointer 所指 commit 的 ID | 被合并分支的 branch pointer 所指 commit 的 ID |

merge commit 的两个 parent 均取 `merge` 开始时 current branch 与被合并分支各自的 head commit。

核心性质：
- commit 保存完整的快照，而非相对父提交的差异。
- commit 是 immutable：commit 一经构造，其字段不再改变。

## 3. StagingArea

StagingArea 记录下一次 commit 相对 current commit 的更新。

有两组更新：

| 组                   | 类型                    | 存储            | 含义          |
| ------------------- | --------------------- | ------------- | ----------- |
| staged for addition | `Map<String, String>` | 文件名 → blob ID | 文件待新增 / 待更新 |
| staged for removal  | `Set<String>`         | 文件名           | 文件待移除       |

StagingArea 创建时为空，其状态会被 `add`、`rm`、`commit` 改变：

- `add` / `rm`：按 `add`/`rm` 规则修改两组中的记录。
- `commit`：成功后把暂存区清空回皆空，不是普通修改。

核心性质：
- StagingArea 是 mutable：在下次提交前，暂存的文件名及其 blob ID 可被修改。

## 4. Remote 映射

remote 映射不独立成类，本节只描述它的逻辑数据形态。

remote 映射逻辑上是一个 remote name 到远程 `.gitlet` 目录路径的映射。因此关联新的远程仓库就是在这个映射中新增一个条目：remote name → 远程 `.gitlet` 目录路径。

初始时 remote 映射为空，其状态会被 `add-remote` 和 `rm-remote` 改变：

- `add-remote`：在 remote 映射里新增一个 remote name → 路径。
- `rm-remote`：按 remote name 删除 remote 映射中的一个条目。

`fetch` 产生的 `[remote name]/[remote branch name]` 分支，复用第 5 节已定的 branch 模型，只是名字里含 `/`、存放目录不同。

核心性质：
- remote 映射是 mutable，不能修改已有 remote name 对应的路径。

## 5. Branch 与 HEAD 的逻辑表示

branch 与 HEAD 都不独立成类，本节只描述它们的逻辑数据形态。

branch 逻辑上是一个 branch name 到 commit ID 的映射。映射中每个分支名对应的 commit ID，即该分支的 branch pointer。因此新建分支就是在这个映射中新增一个条目：新建分支的 branch name → current commit 的 commit ID。

HEAD 本身是一个 branch name 的字符串。因此可以根据 HEAD 中的分支名在 branch 映射中查出 commit ID，即得到 current commit。HEAD 只引用分支、永不直接指向 commit。

初始时 branch 映射只有：master → initial commit ID；HEAD = master。

核心性质：
- branch 是 mutable，branch name 对应的 commit ID 可以改。
- HEAD 是 mutable，只在切换分支的时候改变，它不直接跟随 commit 变化。

## 6. 集合选型

| 位置                    | 实现类     | 理由                                 |
| --------------------- | ------- | ---------------------------------- |
| Commit.filenameToBlob | HashMap | 只按键查、从不整体有序输出                      |
| Remote 映射             | HashMap | 只按键查、从不整体有序输出                      |
| StagingArea addition  | TreeMap | 会被 `status` 按名有序列出，让集合自带序、输出不用手动排序 |
| StagingArea removal   | TreeSet | 会被 `status` 按名有序列出，让集合自带序、输出不用手动排序 |
| branch 映射             | `refs/heads/` 文件（一分支一文件） | 不是内存集合；`status` 用 `plainFilenamesIn` 的字典序输出 |

约束：commit 的映射构造后不可改、不对外泄露可修改的内部集合。

## 7. SHA-1 计算规则

blob：根据类型标签和原始字节内容计算 ID。

```
sha1("blob", byte[] 原始字节)
```

commit：根据类型标签和所有字段计算 ID。

```
sha1("commit",
     message,
     时间表示,
     firstParent  (null → ""),
     secondParent (null → ""),
     文件名1, blobID1, 文件名2, blobID2, …)
```

- 最前放类型标签；
- 全部字段按固定顺序纳入，任一改变都会改变 ID；
- 时间表示用 `Date` 内部的毫秒数（`getTime()`）转字符串传入；
- 映射部分：先把文件名排序，再把每个（文件名、blob ID）作为独立参数传入，不裸拼字符串；
- 两个 parent 为 null 时统一用一个固定表示 `""`。

commit 与 blob 分目录存储，保证即使哈希相同也不会在磁盘上互相覆盖；目录布局见 [03 持久化与文件布局](03-persistence-and-layout.md)。
