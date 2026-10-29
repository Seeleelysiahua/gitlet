# `merge`

> 对象结构见 [02 对象模型](../02-object-model.md)，磁盘读写见 [03 持久化与文件布局](../03-persistence-and-layout.md)，错误消息与抛出机制见 [04 错误处理](../04-error-handling.md)。

## 1. 方法签名

`static void merge(String branchName)`

## 2. 依赖数据

- 公共前置（三条路径都读）：
    - stage：addition + removal
    - 被合并分支指针文件（`refs/heads/<branchName>`，不存在时退查 `refs/remotes/<branchName>`）：存在性、内容
    - HEAD：当前分支名
    - `refs/heads/<当前分支>` 指针文件：当前分支 head（当前分支指针指向的 commit）的 commit ID
- 求 split point：两个 head 沿 parent 回溯沿途各 commit 的 `firstParent`/`secondParent` 字段
- 边界二 fast-forward（含 `replaceWorkingDir` 内所读）：current（当前分支的 head）与 given（被合并分支的 head）两 commit 的 `filenameToBlob`、given 各 blob 字节、工作目录全部普通文件名
- 正常 merge：current / given / split point 三个 commit 的 `filenameToBlob`、冲突文件两侧的 blob 内容、写工作文件时按 blob ID 读 objects/blobs 字节、检查用的工作目录文件存在性

## 3. 执行步骤

1. 读取 stage → `StagingArea stage = Utils.readObject(Utils.join(".gitlet", "stage"), StagingArea.class)`
2. 检查 stage 不为空 `!stage.addition.isEmpty() || !stage.removal.isEmpty()` → `throw Utils.error("You have uncommitted changes.")`
3. 解析被合并分支指针文件（先 `refs/heads`、不存在再查 `refs/remotes`，后者服务 `pull` 合并远程跟踪分支）：

    ```java
    File branchFile = Utils.join(".gitlet", "refs", "heads", branchName);
    if (!branchFile.exists()) {
        branchFile = Utils.join(".gitlet", "refs", "remotes", branchName);
    }
    ```

4. 检查被合并分支不存在 `!branchFile.exists()` → `throw Utils.error("A branch with that name does not exist.")`
5. 获取当前分支名 → `String currentBranchName = getCurrentBranchName()`
6. 检查被合并分支是当前分支 `currentBranchName.equals(branchName)` → `throw Utils.error("Cannot merge a branch with itself.")`
7. 取当前 commit ID → `String currentCommitID = getCurrentCommitID()`
8. 获取当前 commit 对象 → `Commit currentCommit = getCurrentCommit()`
9. 获取被合并分支 head 的 commit ID → `String givenCommitID = Utils.readContentsAsString(branchFile)`
10. 取被合并分支 head 的 commit 对象 → `Commit givenCommit = Utils.readObject(Utils.join(".gitlet", "objects", "commits", givenCommitID), Commit.class)`
11. 获取 split point → `String splitPointID = findSplitPoint(currentCommitID, givenCommitID)`

12. 按 split point 分发（自上而下；命中一条即处置并结束，否则继续下一条）：

    - split point 是被合并分支 head `splitPointID.equals(givenCommitID)` → 走边界一（步骤 13）
    - split point 是当前分支 head `splitPointID.equals(currentCommitID)` → 走边界二 fast-forward（步骤 14–17）
    - 其余情况 → 走正常 merge（决策表 + 步骤 18–24）

边界一（split point 是被合并分支 head）：

13. 打印消息并结束本命令 → `Utils.message("Given branch is an ancestor of the current branch.");`，不动任何状态

边界二（split point 是当前分支 head，fast-forward 到被合并分支 head）：

14. 更新工作目录为被合并分支快照 → `replaceWorkingDir(currentCommit, givenCommit)`
15. 移动当前分支指针到被合并分支 head → `Utils.writeContents(Utils.join(".gitlet", "refs", "heads", currentBranchName), givenCommitID)`
16. 清空 stage → `Utils.writeObject(Utils.join(".gitlet", "stage"), new StagingArea())`
17. 打印 fast-forward 消息 → `Utils.message("Current branch fast-forwarded.")`

正常 merge（split point 既非 given 也非 current head） —— 逐文件决策表：

记 `s` / `c` / `g` 为文件 f 在 split point / current head / given head 的 `filenameToBlob` 中的 blob ID（`null` = 该 commit 不含此文件），`cChanged = !Objects.equals(c, s)`、`gChanged = !Objects.equals(g, s)`。内容寻址：比 blob ID 即比内容，除冲突分支读 blob 内容外，全程不读文件内容。

| 分支  | 条件                                              | 动作                                                                  |
| --- | ----------------------------------------------- | ------------------------------------------------------------------- |
| 1   | `!gChanged`                                     | 不动（given 未动，保留 current 现状；含两边都未动）                                   |
| 2   | `gChanged && !cChanged && g != null`            | 采纳 given 版本：写工作文件为 g 的 blob 内容 + `stage.addition.put(f, g)`         |
| 3   | `gChanged && !cChanged && g == null`            | given 已删：删工作文件 + `stage.removal.add(f)`                             |
| 4   | `gChanged && cChanged && Objects.equals(c, g)`  | 不动（两边同改，含同删）                                                        |
| 5   | `gChanged && cChanged && !Objects.equals(c, g)` | 冲突：五段内容写工作文件 + 新 blob 落盘 + `stage.addition.put(f, 新blobID)` + 记冲突标记 |

冲突内容为 `"<<<<<<< HEAD\n" + cText + "=======\n" + gText + ">>>>>>>\n"`

以下步骤 18–24 为 `mergeSnapshots` 的方法体（`merge` 在正常 merge 分支调用它）：

18. 读 split point 的 commit 对象 → `Commit splitCommit = Utils.readObject(Utils.join(".gitlet", "objects", "commits", splitPointID), Commit.class)`
19. 逐文件分析：

    ```java
    TreeSet<String> names = new TreeSet<>(splitCommit.filenameToBlob.keySet());

    names.addAll(currentCommit.filenameToBlob.keySet());
    names.addAll(givenCommit.filenameToBlob.keySet());

    Map<String, String> writeBlob = new HashMap<>();
    Map<String, String> newBlobContents = new HashMap<>();
    List<String> deletions = new ArrayList<>();
    boolean conflict = false;

    for (String f : names) {
        String s = splitCommit.filenameToBlob.get(f);
        String c = currentCommit.filenameToBlob.get(f);
        String g = givenCommit.filenameToBlob.get(f);
        boolean cChanged = !Objects.equals(c, s);
        boolean gChanged = !Objects.equals(g, s);
        if (!gChanged) {
            continue;
        }
        if (!cChanged) {
            if (g != null) {
                stage.addition.put(f, g);
                writeBlob.put(f, g);
            } else {
                stage.removal.add(f);
                deletions.add(f);
            }
            continue;
        }
        if (Objects.equals(c, g)) {
            continue;
        }
        String cText = c == null ? "" : Utils.readContentsAsString(Utils.join(".gitlet", "objects", "blobs", c));
        String gText = g == null ? "" : Utils.readContentsAsString(Utils.join(".gitlet", "objects", "blobs", g));
        String merged = "<<<<<<< HEAD\n" + cText + "=======\n" + gText + ">>>>>>>\n";
        String newBlobID = Utils.sha1("blob", merged);
        newBlobContents.put(newBlobID, merged);
        writeBlob.put(f, newBlobID);
        stage.addition.put(f, newBlobID);
        conflict = true;
    }
    ```

20. 挡路检查（先于一切写盘）：

    ```java
    Set<String> touched = new HashSet<>(writeBlob.keySet());
    touched.addAll(deletions);
    for (String f : touched) {
        if (!currentCommit.filenameToBlob.containsKey(f) && Utils.join(f).exists()) {
            throw Utils.error("There is an untracked file in the way; delete it, or add and commit it first.");
        }
    }
    ```

21. 执行写删（先落新 blob，再写工作文件，最后删文件）：

    ```java
    for (Map.Entry<String, String> e : newBlobContents.entrySet()) {
        Utils.writeContents(Utils.join(".gitlet", "objects", "blobs", e.getKey()), e.getValue());
    }
    for (Map.Entry<String, String> e : writeBlob.entrySet()) {
        Utils.writeContents(Utils.join(e.getKey()), Utils.readContents(Utils.join(".gitlet", "objects", "blobs", e.getValue())));
    }
    for (String f : deletions) {
        Utils.restrictedDelete(f);
    }
    ```

22. 若 stage 有改动 `!stage.addition.isEmpty() || !stage.removal.isEmpty()` → `Utils.writeObject(Utils.join(".gitlet", "stage"), stage)`
23. 生成 merge commit → `makeCommit("Merged " + branchName + " into " + currentBranchName + ".", givenCommitID)`
24. 若发生冲突 `conflict` → `Utils.message("Encountered a merge conflict.")`

### 得到 split point

`findSplitPoint(currentCommitID, givenCommitID)` 从两个 head 各自沿 parent 回溯，返回二者最新公共祖先（split point）的完整 ID。先收集 current 的祖先，同时为每个祖先算出到 initial 的最长距离，再沿 given 的祖先走一趟、取其中距离最大者：

```java
private static String findSplitPoint(String currentCommitID, String givenCommitID) {
    Map<String, Integer> depth = new HashMap<>();
    Set<String> ancestors = new HashSet<>();
    collectAncestors(currentCommitID, depth, ancestors);

    Set<String> visited = new HashSet<>();
    return deepestCommonAncestor(givenCommitID, ancestors, depth, visited);
}
```

收集 current 的祖先（含 current），并记下每个祖先到 initial 的最长距离：

```java
private static void collectAncestors(String id, Map<String, Integer> depth, Set<String> ancestors) {
    if (ancestors.contains(id)) {
        return;
    }
    ancestors.add(id);
    Commit commit = Utils.readObject(Utils.join(".gitlet", "objects", "commits", id), Commit.class);
    String first = commit.firstParent;
    String second = commit.secondParent;
    if (first == null) {
        depth.put(id, 0);
        return;
    }
    collectAncestors(first, depth, ancestors);
    if (second != null) {
        collectAncestors(second, depth, ancestors);
    }
    int d = depth.get(first);
    if (second != null) {
        d = Math.max(d, depth.get(second));
    }
    depth.put(id, d + 1);
}
```

沿 given 的祖先走一趟，在 `ancestors` 里挑距离最大者返回：

```java
private static String deepestCommonAncestor(String id, Set<String> ancestors, Map<String, Integer> depth, Set<String> visited) {
    if (!visited.add(id)) {
        return null;
    }
    Commit commit = Utils.readObject(Utils.join(".gitlet", "objects", "commits", id), Commit.class);
    String first = commit.firstParent;
    String second = commit.secondParent;

    String best = ancestors.contains(id) ? id : null;
    if (first != null) {
        String candidate = deepestCommonAncestor(first, ancestors, depth, visited);
        if (candidate != null && (best == null || depth.get(candidate) > depth.get(best))) {
            best = candidate;
        }
    }
    if (second != null) {
        String candidate = deepestCommonAncestor(second, ancestors, depth, visited);
        if (candidate != null && (best == null || depth.get(candidate) > depth.get(best))) {
            best = candidate;
        }
    }
    return best;
}
```


## 4. 复用与被复用

- **调用的 Utils 方法**：`readObject`、`error`、`join`、`readContentsAsString`、`message`、`writeContents`、`writeObject`、`sha1`、`readContents`、`restrictedDelete`
- **调用的对象接口**：
    - Commit：读字段 `filenameToBlob`（`get`/`keySet`/`containsKey`）、`firstParent`、`secondParent`
    - StagingArea：读字段 `addition`、`removal` 并改内存中读出的 stage（步骤 19 的 `addition.put`/`removal.add`）；无参构造 `new StagingArea()` 造空覆盖写（步骤 16）
- **被谁调用**：由 `Repository.run` 分发，并被 `pull` 复用（合并远程跟踪分支）
- **新提取的辅助方法**：
    - `private static void mergeSnapshots(Commit splitCommit, Commit currentCommit, Commit givenCommit, StagingArea stage, String branchName, String currentBranchName, String givenCommitID)` —— 正常 merge 的逐文件分析、挡路检查、写删与提交（§3 步骤 18–24 的方法体），仅服务本卡
    - `findSplitPoint(String, String)` 及私有递归 `collectAncestors`、`deepestCommonAncestor` —— split point 求解三方法，仅服务本卡，不登记 03§6/接口清单（同 checkout 私有分派 helper 口径）；方法体见 §3“得到 split point”
    - 调用已提取的 helper（见 03 §6）：`getCurrentBranchName()`、`getCurrentCommitID()`、`getCurrentCommit()`、`replaceWorkingDir(Commit, Commit)`、`makeCommit(String, String)`

## 5. 状态修改与失败保护

- **分界线**：前置三检查（步骤 2/4/6）与 split point 求解全程只读；边界一不写盘；边界二首次写盘在步骤 14；正常 merge 的挡路检查在步骤 20、空 stage 检查在 makeCommit 内，首次写盘在步骤 21
- **改动项**：
    - working directory：改（边界二整体替换为 given 快照；正常 merge 按写/删清单写删；边界一不改）
    - staging area：改（边界二清空；正常 merge 应用合并改动后写回、提交后清空；边界一不改）
    - `.gitlet`：改（正常 merge 新增冲突 blob 与 merge commit 对象；边界一/二不改）
    - branch pointer：改（边界二移到 given head；正常 merge 移到 merge commit；边界一不改）
    - HEAD：不改
- **失败保护**：
    - 正常 merge 落盘序：先落新 blob、再写删工作文件、再写回 stage、最后 makeCommit（其内先写 commit 对象、再移指针、最后清 stage）
    - 崩溃窗口一（步骤 21 或边界二步骤 14 的写盘中途）：stage 未动仍为空，重跑可过步骤 2，但已写入的 current 未追踪文件（如 split 无、仅 given 有的新增）会被挡路检查当 untracked 拦下 —— 手动删除残留后重试
    - 崩溃窗口二（正常 merge 写回 stage 之后、makeCommit 完成前）：stage 留有 merge 条目，重跑被步骤 2 的 `You have uncommitted changes.` 挡住 —— 先 `reset` 回当前 head 再重试
    - 崩溃窗口三（边界二步骤 15 移指针后）：stage 本就为空、工作目录已替换、指针已在 given head，状态已一致；重跑落入边界一并打印祖先消息，无副作用
    - 零动作路径：步骤 20/21 空转、步骤 22 条件不满足跳过写回，全程零写盘，随后 makeCommit 内空 stage 检查抛 `No changes added to the commit.`

## 6. 性能核对

- **spec 要求**：runtime 为 O(N log N + D) —— N 是两个分支的祖先 commit 总数，D 是这些 commit 下所有文件的总数据量；memory 未单列
- **达标分析**：
    1. 求 split point：两趟 DFS 沿 parent 走祖先图，`visited`/`ancestors` 去重，每节点常数工作加一次 readObject；逐祖先反序列化属主动遍历多对象，计 O(N)
    2. 正常 merge 分析（步骤 18–20）：文件名并集不超过三快照文件数之和，逐文件常数次哈希查表；仅冲突分支读 blob 内容
    3. 写删与提交（步骤 21–23）：blob 读写总量不超过 D；makeCommit 内映射复制随快照文件数 F 线性、TreeMap 排序 O(F log F)，log 因子被上界的 N log N 项吸收
    4. 边界二：replaceWorkingDir 对目标快照线性，blob 读写同样界于 D
    5. 合计 O(N) + O(F log F) + O(D)，满足 O(N log N + D) 上界
- **memory**：峰值 = split point 三表（`ancestors`/`depth`/`visited`，O(N)）+ 分析容器（`writeBlob`/`deletions` 为 O(F)，`newBlobContents` 为冲突内容总量，不超过 D）+ 常数个单 commit 对象
