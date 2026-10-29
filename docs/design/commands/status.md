# `status`

> 对象结构见 [02 对象模型](../02-object-model.md)，磁盘读写见 [03 持久化与文件布局](../03-persistence-and-layout.md)，错误消息与抛出机制见 [04 错误处理](../04-error-handling.md)。

## 1. 方法签名

`static void status()`

## 2. 依赖数据

- 工作目录中的全部普通文件（忽略子目录）：读字节算 blob ID 用于内容比较
- `refs/heads/` 目录：列出全部本地分支名
- stage：addition（文件名 → blob ID）与 removal（文件名）两组
- current commit：文件名 → blob ID 的映射
- HEAD：得到当前分支名来标注 `*`

## 3. 执行步骤

1. 读取 `refs/heads/` 目录中所有分支名 → `List<String> allBranches = Utils.plainFilenamesIn(Utils.join(".gitlet", "refs", "heads"))`
2. 取当前分支名 → `String branchName = getCurrentBranchName()`
3. 读取 stage → `StagingArea stage = Utils.readObject(Utils.join(".gitlet", "stage"), StagingArea.class)`
4. 取当前 commit 对象 → `Commit currentCommit = getCurrentCommit()`
5. 获取每个工作目录文件名 → `List<String> allFileName = Utils.plainFilenamesIn(".")`
6. 遍历工作目录文件，读字节算 blob ID，收进映射：

    ```java
    Map<String, String> allWID = new HashMap<>();
    for (String filename : allFileName) {
        byte[] fileContent = Utils.readContents(new File(filename));
        String id = Utils.sha1("blob", fileContent);
        allWID.put(filename, id);
    }
    ```

7. 合并 current commit、stage、工作目录的文件名，构造遍历范围：

    ```java
    Set<String> union = new HashSet<>();
    union.addAll(allFileName);
    union.addAll(stage.addition.keySet());
    union.addAll(stage.removal);
    union.addAll(currentCommit.filenameToBlob.keySet());
    ```

8. 构造 `modifiedNotStaged` → `TreeMap<String, Boolean> modifiedNotStaged = new TreeMap<>()`（值约定：`true`=modified、`false`=deleted）
9. 构造 `untracked` → `TreeSet<String> untracked = new TreeSet<>()`
10. 遍历 `union` 分类：

    ```java
    for (String name : union) {
        boolean inW = allWID.containsKey(name);
        boolean inA = stage.addition.containsKey(name);
        boolean inR = stage.removal.contains(name);
        boolean inC = currentCommit.filenameToBlob.containsKey(name);

        boolean expectedExists = inA || (inC && !inR);

        if (inW) {
            if (expectedExists) {
                String expectedID = inA
                    ? stage.addition.get(name)
                    : currentCommit.filenameToBlob.get(name);
                String wID = allWID.get(name);
                if (!wID.equals(expectedID)) {
                    modifiedNotStaged.put(name, true);
                }
            } else {
                untracked.add(name);
            }
        } else {
            if (inA) {
                modifiedNotStaged.put(name, false);
            } else if (inC && !inR) {
                modifiedNotStaged.put(name, false);
            }
        }
    }
    ```

11. 按五节版式输出：

    ```java
    Utils.message("=== Branches ===");
    for (String branch : allBranches) {
        if (branchName.equals(branch)) {
            Utils.message("*%s", branch);
        } else {
            Utils.message("%s", branch);
        }
    }
    Utils.message("");

    Utils.message("=== Staged Files ===");
    for (String filename : stage.addition.keySet()) {
        Utils.message("%s", filename);
    }
    Utils.message("");

    Utils.message("=== Removed Files ===");
    for (String filename : stage.removal) {
        Utils.message("%s", filename);
    }
    Utils.message("");

    Utils.message("=== Modifications Not Staged For Commit ===");
    for (String name : modifiedNotStaged.keySet()) {
        if (modifiedNotStaged.get(name)) {
            Utils.message("%s (modified)", name);
        } else {
            Utils.message("%s (deleted)", name);
        }
    }
    Utils.message("");

    Utils.message("=== Untracked Files ===");
    for (String name : untracked) {
        Utils.message("%s", name);
    }
    Utils.message("");
    ```

## 4. 复用与被复用

- **调用的 Utils 方法**：`plainFilenamesIn`、`join`、`readObject`、`readContents`、`sha1`、`message`
- **调用的对象接口**：
    - Commit：读 `filenameToBlob`
    - StagingArea：读 `addition`、`removal`
- **被谁调用**：仅由 `Repository.run` 分发，当前无其他命令复用
- **新提取的辅助方法**：无；调用 commit 卡已提取的 `getCurrentBranchName()`、`getCurrentCommit()`

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

- **spec 要求**：runtime 仅取决于工作目录中的数据量、staged for addition / removal 的文件数量与分支数量；未单列 memory 要求
- **达标分析**：
    - runtime：列 `refs/heads/` 对分支数线性；列工作目录并逐个读字节算哈希，对工作目录数据量线性；读、遍历 stage 对 addition 与 removal 的条目数线性；分类遍历时，每个文件只做常数次哈希表 `containsKey`/`get`。此外还要遍历 current commit 的 `filenameToBlob`，这是固有输入：它只反序列化当前一个 commit（按规豁免），规模仅取决于当前工作状态、与历史 commit 总数无关；即便其中有工作目录已删且未暂存、因而不计入前述三量的文件名，也不引入随全库增长的开销。故满足 spec 的 “only” 约束
    - memory：未单列；峰值为工作目录 blob ID 映射、`union` 集合与两个有序结果容器（`modifiedNotStaged`、`untracked`），规模对“工作目录文件数 + stage 条目数 + `filenameToBlob` 条目数”线性，循环内另有单个文件的字节与若干布尔、ID；不持有任何历史 commit 对象
