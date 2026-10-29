# `init`

> 对象结构见 [02 对象模型](../02-object-model.md)，磁盘读写见 [03 持久化与文件布局](../03-persistence-and-layout.md)，错误消息与抛出机制见 [04 错误处理](../04-error-handling.md)。

## 1. 方法签名

`static void init()`

## 2. 依赖数据

无（init 在未初始化的空目录上执行，仓库尚不存在，没有对象或文件可读）。

## 3. 执行步骤

1. 检查 `.gitlet` 已初始化：
   构造 .gitlet 目录对象 → `File gitletDir = Utils.join(".gitlet")`
   检查 .gitlet 已存在 `gitletDir.exists()` → `throw Utils.error("A Gitlet version-control system already exists in the current directory.")`
2. 内存构造 initial commit → `Commit commit = new Commit("initial commit", new Date(0), null, null, new HashMap<>())`
3. 算出 initial commit 的 ID → `String commitID = Utils.sha1("commit", "initial commit", String.valueOf(new Date(0).getTime()), "", "")`
4. 建目录树 → 分别对以下目录调用 `mkdirs()`：
   `Utils.join(".gitlet","objects","commits").mkdirs()`、
   `Utils.join(".gitlet","objects","blobs").mkdirs()`、
   `Utils.join(".gitlet","refs","heads").mkdirs()`、
   `Utils.join(".gitlet","refs","remotes").mkdirs()`
5. 写 initial commit → `Utils.writeObject(Utils.join(".gitlet","objects","commits",commitID), commit)`
6. 写 master 指针 → `Utils.writeContents(Utils.join(".gitlet","refs","heads","master"), commitID)`
7. 写 HEAD → `Utils.writeContents(Utils.join(".gitlet","HEAD"), "master")`
8. 写空 stage → `Utils.writeObject(Utils.join(".gitlet","stage"), new StagingArea())`

## 4. 复用与被复用

- **调用的 Utils 方法**：`error`、`sha1`、`join`、`writeObject`、`writeContents`
- **调用的对象接口**：Commit 五参构造、StagingArea 无参构造（均见对象接口清单）
- **被谁调用**：仅由 `Repository.run` 按命令名分发，当前无其他命令复用
- **新提取的辅助方法**：无

## 5. 状态修改与失败保护

- **分界线**：唯一的检查在第 1 步完成；第 2–3 步只在内存构造对象、算 ID，不碰磁盘；从第 4 步 `mkdirs()` 起首次写磁盘
- **改动项**：
  - working directory：不改
  - staging area：改（新建空 stage）
  - `.gitlet`：改（新建目录树、新增 initial commit 对象）
  - branch pointer：改（新建 `master`，指向 initial commit）
  - HEAD：改（新建，内容为 `master`）
- **失败保护**：唯一失败点（`.gitlet` 已存在）在一切写之前；落盘先写无引用的 initial commit，再写分支指针与 HEAD

## 6. 性能核对

- **spec 要求**：runtime 相对任何显著度量为常数；未单列 memory 要求
- **达标分析**：不读取现有数据、不遍历目录，只做固定次数的建目录与写文件，与仓库规模无关，runtime 为常数
