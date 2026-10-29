# `add`

> 对象结构见 [02 对象模型](../02-object-model.md)，磁盘读写见 [03 持久化与文件布局](../03-persistence-and-layout.md)，错误消息与抛出机制见 [04 错误处理](../04-error-handling.md)。

## 1. 方法签名

`static void add(String fileName)`

## 2. 依赖数据

- 参数 fileName 对应的工作目录文件
- stage：其中的 addition 映射与 removal 集合
- HEAD：当前分支的名字
- 当前分支指针：所指 commit 的 ID
- current commit：文件名 → blob ID 的映射

## 3. 执行步骤

1. 检查目标文件是否存在：
   构造目标文件 → `File file = Utils.join(CWD, fileName)`
   检查目标文件不存在 `!file.exists()` → `throw Utils.error("File does not exist.")`
2. 读取目标文件字节 → `byte[] fileContent = Utils.readContents(file)`
3. 读取 stage → `StagingArea stage = Utils.readObject(Utils.join(".gitlet", "stage"), StagingArea.class)`
4. 清理 removal → `boolean removedFromRemoval = stage.removal.remove(fileName)`
5. 取 current commit 对象（调用已提取的 `getCurrentCommit()`，其内部经 `getCurrentCommitID()`/`getCurrentBranchName()` 完成三跳，见 03 §6） → `Commit currentCommit = getCurrentCommit()`

6. 计算目标文件的 blob ID → `String blobID = Utils.sha1("blob", fileContent)`
7. 初始化两个标记 → `boolean needWriteBlob = false`、`boolean stageChanged = removedFromRemoval`
8. 判定本次改动（自上而下；命中一条即处置并结束判定，否则继续下一条）：

    - 不被追踪 `!currentCommit.filenameToBlob.containsKey(fileName)` → `needWriteBlob = true; stageChanged = true;`
    - 取提交版 blob ID（走到此条说明已被追踪） → `String commitBlobID = currentCommit.filenameToBlob.get(fileName)`
    - 内容不同 `!blobID.equals(commitBlobID)` → `needWriteBlob = true; stageChanged = true;`
    - 内容相同且在 addition 中 `stage.addition.containsKey(fileName)` → `stage.addition.remove(fileName); stageChanged = true;`
    - 内容相同、不在 addition，但本次清过 removal `removedFromRemoval` → 不另改标记（第 10 步凭 `stageChanged` 写回）
    - 其余情况（内容相同、不在 addition、未清 removal） → 不做任何处理

9. 若 `needWriteBlob` → 幂等写 blob（内容相同则 ID 相同，覆盖写与跳过等价）：`Utils.writeContents(Utils.join(".gitlet", "objects", "blobs", blobID), fileContent)`；随后加入 addition → `stage.addition.put(fileName, blobID)`
10. 若 `stageChanged` → 写回 stage：`Utils.writeObject(Utils.join(".gitlet", "stage"), stage)`；否则不写盘，命令正常结束

## 4. 复用与被复用

- **调用的 Utils 方法**：`error`、`readContents`、`readObject`、`sha1`、`writeContents`、`writeObject`、`join`
- **调用的对象接口**：
    - Commit：读字段 `filenameToBlob`（调用 `containsKey`/`get`）
    - StagingArea：读改字段 `addition`（`containsKey`/`remove`/`put`）、`removal`（`remove`）
- **被谁调用**：仅由 `Repository.run` 分发，当前无其他命令复用
- **新提取的辅助方法**：无。调用已提取的 `getCurrentBranchName()`/`getCurrentCommitID()`/`getCurrentCommit()`（commit 卡提取，见 03 §6）

## 5. 状态修改与失败保护

- **分界线**：唯一的检查在第 1 步完成；第 2–8 步只读磁盘、再内存计算与改动，不写盘；从第 9 步起首次写磁盘
- **改动项**：
  - working directory：不改
  - staging area：改（覆盖写回 stage）
  - `.gitlet`：改（新增一个 blob 对象）
  - branch pointer：不改
  - HEAD：不改
- **失败保护**：落盘阶段先写 blob、再覆盖写回 stage，中途失败至多残留无引用的新 blob

## 6. 性能核对

- **spec 要求**：runtime 与被加文件大小线性、加 lgN（N 为 commit 中文件数），spec 未单列 memory 要求
- **达标分析**：
    - runtime：读字节/算哈希/写 blob 操作各与文件大小成线性；查找 commit 的 filenameToBlob 为常数、不随 N 增长；读 stage/current commit 为反序列化，按规豁免。故实际 runtime 为 O(文件大小)，优于 spec 要求的 O(文件大小+lgN)
    - memory：spec 未单列要求，峰值为被加文件字节、与文件大小线性。
