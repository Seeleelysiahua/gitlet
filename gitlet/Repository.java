package gitlet;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** 协调层：执行入口检查、按命令名分发、实现各命令业务逻辑，
 *  并读写 .gitlet 中的对象与仓库状态。
 *  @author seeleHUAelysia
 */
public class Repository {

    /* ==================== 路径常量 ==================== */

    /** 当前工作目录。 */
    public static final File CWD =
            new File(System.getProperty("user.dir"));

    /** .gitlet 目录：Gitlet 全部内部状态的根目录。 */
    public static final File GITLET_DIR =
            Utils.join(CWD, ".gitlet");

    /** .gitlet/objects/：对象（commit、blob）的根目录。 */
    static final File OBJECTS_DIR =
            Utils.join(GITLET_DIR, "objects");

    /** .gitlet/objects/commits/：commit 对象，文件名为裸 40 位 ID。 */
    static final File COMMITS_DIR =
            Utils.join(OBJECTS_DIR, "commits");

    /** .gitlet/objects/blobs/：blob 对象，文件名为裸 40 位 ID。 */
    static final File BLOBS_DIR =
            Utils.join(OBJECTS_DIR, "blobs");

    /** .gitlet/refs/：按名引用的指针根目录（本地/远程分支）。 */
    static final File REFS_DIR =
            Utils.join(GITLET_DIR, "refs");

    /** .gitlet/refs/heads/：本地分支指针，文件名为分支名、内容为 commit ID。 */
    static final File HEADS_DIR =
            Utils.join(REFS_DIR, "heads");

    /** .gitlet/refs/remotes/：远程跟踪分支根目录，下分 <remote name>/。 */
    static final File REMOTE_REFS_DIR =
            Utils.join(REFS_DIR, "remotes");

    /** .gitlet/HEAD：单行文本文件，内容为当前分支名。 */
    static final File HEAD_FILE =
            Utils.join(GITLET_DIR, "HEAD");

    /** .gitlet/stage：StagingArea 整体序列化文件。 */
    static final File STAGE_FILE =
            Utils.join(GITLET_DIR, "stage");

    /** .gitlet/remotes：remote 映射整体序列化文件；
     *  init 不建，首次 add-remote 才创建。 */
    static final File REMOTES_FILE =
            Utils.join(GITLET_DIR, "remotes");


    /* ==================== 总入口 ==================== */

    /** 总入口：入口检查 → 分发 → 执行。
     *  <p>catch 块是全程序唯一的错误打印出口。
     *  @param args 原始命令行参数，args[0] 为命令名，其余为操作数。 */
    public static void run(String[] args) {
        try {
            /* 第 1 步：空输入。最先检查，否则 args[0] 越界。 */
            if (args.length == 0) {
                throw Utils.error("Please enter a command.");
            }

            // args[0] 一定存在；切出命令名与操作数。
            String command = args[0];
            String[] operands =
                    Arrays.copyOfRange(args, 1, args.length);

            /* 第 2、3 步：未知命令在 default 拦截；
             *  其余 case 只校验操作数数量。 */
            switch (command) {
                case "init": case "log": case "global-log":
                case "status":
                    checkOperandCount(operands, 0);
                    break;

                case "add": case "commit": case "rm": case "find":
                case "branch": case "rm-branch": case "reset":
                case "merge": case "rm-remote":
                    checkOperandCount(operands, 1);
                    break;

                case "add-remote": case "fetch": case "push":
                case "pull":
                    checkOperandCount(operands, 2);
                    break;

                case "checkout":
                    /* 三种用法共 1~3 个操作数；-- 的位置校验留给该命令。 */
                    if (operands.length < 1 || operands.length > 3) {
                        throw Utils.error("Incorrect operands.");
                    }
                    break;

                default:
                    throw Utils.error("No command with that name exists.");
            }

            /* 第 4 步：未初始化；init 豁免。 */
            if (!command.equals("init") && !GITLET_DIR.isDirectory()) {
                throw Utils.error("Not in an initialized Gitlet directory.");
            }

            /* 分发：按命令名调用同名方法，只传操作数。
             *  地基阶段只接 init，其余随实现逐个补 case。 */
            switch (command) {
                case "init":
                    init();
                    break;

                case "add":
                    add(operands[0]);
                    break;

                case "commit":
                    commit(operands[0]);
                    break;

                case "log":
                    log();
                    break;

                case "global-log":
                    globalLog();
                    break;

                case "find":
                    find(operands[0]);
                    break;

                default:
                    throw Utils.error("No command with that name exists.");
            }
        } catch (GitletException e) {
            /* 统一汇报链唯一出口：逐字打印消息后以 0 退出。 */
            Utils.message(e.getMessage());
            System.exit(0);
        }
    }

    /** 校验操作数数量是否等于期望值，不符则抛错。
     *  @param operands 已切去命令名的操作数数组。
     *  @param expected 该命令要求的操作数个数。 */
    private static void checkOperandCount(String[] operands,
                                          int expected) {
        if (operands.length != expected) {
            throw Utils.error("Incorrect operands.");
        }
    }

    /* ==================== 命令方法 ==================== */

    /** init 命令：在当前目录创建 Gitlet 版本控制系统。
     *  <p>自动建立 initial commit（消息 "initial commit"、
     *  时间为 Unix epoch）、master 分支指针、HEAD 与空暂存区。
     *  <p>已在初始化目录中再次执行时抛错，不产生任何改动。 */
    static void init() {
        /* 检查：已初始化则失败。 */
        if (GITLET_DIR.exists()) {
            throw Utils.error("A Gitlet version-control system already exists in the current directory.");
        }

        /* 内存中构造 initial commit 与它的 ID。 */
        Date epoch = new Date(0);
        Commit initialCommit = new Commit("initial commit", epoch,
                null, null, new HashMap<String, String>());
        String commitID = Utils.sha1("commit", "initial commit", String.valueOf(epoch.getTime()), "", "");

        /* 建目录树。 */
        COMMITS_DIR.mkdirs();
        BLOBS_DIR.mkdirs();
        HEADS_DIR.mkdirs();
        REMOTE_REFS_DIR.mkdirs();

        /* 落盘顺序：先写尚无引用的 commit 对象，再写分支指针、HEAD，最后写空暂存区。 */
        Utils.writeObject(Utils.join(COMMITS_DIR, commitID), initialCommit);
        Utils.writeContents(Utils.join(HEADS_DIR, "master"), commitID);
        Utils.writeContents(HEAD_FILE, "master");
        Utils.writeObject(STAGE_FILE, new StagingArea());
    }

    /** add 命令：把工作目录文件暂存为 staged for addition。
     *  <p>读出字节、算出 blob ID 并写入 objects/blobs，再把
     *  文件名 → blob ID 记入暂存区；同时取消该文件可能存在的删除标记。
     *  @param fileName 要暂存的工作目录文件名。 */
    static void add(String fileName) {
        /* 检查：目标文件必须存在。 */
        File file = Utils.join(CWD, fileName);
        if (!file.exists()) {
            throw Utils.error("File does not exist.");
        }

        /* 读取阶段：文件字节、当前暂存区、current commit。 */
        byte[] fileContent = Utils.readContents(file);
        StagingArea stage = Utils.readObject(STAGE_FILE, StagingArea.class);
        Commit currentCommit = getCurrentCommit();

        /* 若该文件此前被标记删除，add 即取消该删除标记。 */
        boolean removedFromRemoval = stage.removal.remove(fileName);

        String blobID = Utils.sha1("blob", fileContent);

        /* 两个标记：是否需要写 blob、暂存区是否发生变化。
         *  清过 removal 本身就意味着 stage 需要写回。 */
        boolean needWriteBlob = false;
        boolean stageChanged = removedFromRemoval;

        /* 判定五种情况，自上而下命中即处置。 */
        if (!currentCommit.getFilenameToBlob().containsKey(fileName)) {
            // 新文件：current commit 不追踪它。
            needWriteBlob = true;
            stageChanged = true;
        }
        else {
            String commitBlobID = currentCommit.getFilenameToBlob().get(fileName);
            if (!blobID.equals(commitBlobID)) {
                // 已追踪但内容与提交版不同。
                needWriteBlob = true;
                stageChanged = true;
            }
            else if (stage.addition.containsKey(fileName)) {
                // 把文件改回为提交版：把早先暂存的新版本撤出 addition。
                stage.addition.remove(fileName);
                stageChanged = true;
            }
            // 其余：内容相同、不在 addition、未清 removal → 无操作。
        }

        /* 落盘阶段（检查与判定都已结束）：先写只增的 blob，
         *  再把文件名记入 addition；最后仅在有变化时整条写回 stage。 */
        if (needWriteBlob) {
            File blobFile = Utils.join(BLOBS_DIR, blobID);
            Utils.writeContents(blobFile, fileContent);
            stage.addition.put(fileName, blobID);
        }
        if (stageChanged) {
            Utils.writeObject(STAGE_FILE, stage);
        }
    }

    /** commit 命令：根据当前暂存区的内容提交一个新 commit。
     *  <p>普通提交只有一个 parent，故 makeCommit 时第二 parent 传 null。
     *  @param message 提交信息，不能为空或纯空白。 */
    static void commit(String message) {
        makeCommit(message, null);
    }

    /** log 命令：从 current commit 沿 firstParent 回溯到 initial commit，
     *  逐块打印提交信息（含两端）。全程只读，不修改任何状态。 */
    static void log() {
        String commitID = getCurrentCommitID();
        Commit currentCommit = getCurrentCommit();
        while (commitID != null) {
            printCommit(currentCommit, commitID);
            commitID = currentCommit.getFirstParent();
            if (commitID != null) {
                File commitFile = Utils.join(COMMITS_DIR, commitID);
                currentCommit = Utils.readObject(commitFile, Commit.class);
            }
        }
    }

    /** global-log 命令：列出 objects/commits 下的全部 commit 并逐块打印，
     *  不依赖 current commit 与分支。
     *  <p>输出顺序取目录文件名的字典序。 */
    static void globalLog() {
        List<String> allCommitIDs = Utils.plainFilenamesIn(COMMITS_DIR);
        for (String id : allCommitIDs) {
            File commitFile = Utils.join(COMMITS_DIR, id);
            Commit commit = Utils.readObject(commitFile, Commit.class);
            printCommit(commit, id);
        }
    }

    /** find 命令：打印消息与参数完全相等的所有 commit 的 ID（每个一行）。
     *  <p>需遍历完全部 commit 才知道是否有匹配；一个都没找到时抛错。
     *  全程只读，不修改状态。
     *  @param message 要查找的提交消息（精确相等）。 */
    static void find(String message) {
        List<String> allCommitIDs = Utils.plainFilenamesIn(COMMITS_DIR);
        boolean matched = false;
        for (String id : allCommitIDs) {
            File commitFile = Utils.join(COMMITS_DIR, id);
            Commit commit = Utils.readObject(commitFile, Commit.class);
            if (message.equals(commit.getMessage())) {
                matched = true;
                Utils.message(id);
            }
        }
        if (!matched) {
            throw Utils.error("Found no commit with that message.");
        }
    }

    /* ==================== 跨命令 helper ==================== */

    /** 读 HEAD，得当前分支名。
     *  @return HEAD 中记录的分支名，如 "master"。 */
    static String getCurrentBranchName() {
        return Utils.readContentsAsString(HEAD_FILE);
    }

    /** 经当前分支名读 refs/heads 下的指针文件，得当前 commit ID。
     *  @return 当前分支指针所指的完整 40 位 commit ID。 */
    static String getCurrentCommitID() {
        File branchFile = Utils.join(HEADS_DIR, getCurrentBranchName());
        return Utils.readContentsAsString(branchFile);
    }

    /** 按当前 commit ID 从 objects/commits 反序列化出当前 commit。
     *  @return 当前分支所指的 Commit 对象。 */
    static Commit getCurrentCommit() {
        String commitID = getCurrentCommitID();
        File commitFile = Utils.join(COMMITS_DIR, commitID);
        return Utils.readObject(commitFile, Commit.class);
    }

    /** 根据当前暂存区的内容提交一个新 commit，是 commit 与 merge 共用的实现。
     *  <p>以 current commit 的完整快照为底，应用 removal 删除与 addition
     *  更新，构造新快照并算 ID。
     *  <p>落盘顺序为新 commit 对象、当前分支指针、最后清空暂存区。
     *  @param message 提交信息，不能为空或纯空白。
     *  @param secondParentID merge commit 的第二 parent ID；
     *         普通提交传 null，参与哈希时当作空串。 */
    static void makeCommit(String message, String secondParentID) {
        /* 检查 1：消息不能为空或纯空白。 */
        if (message.isBlank()) {
            throw Utils.error("Please enter a commit message.");
        }

        StagingArea stage = Utils.readObject(STAGE_FILE, StagingArea.class);

        /* 检查 2：暂存区不能为空。 */
        if (stage.addition.isEmpty() && stage.removal.isEmpty()) {
            throw Utils.error("No changes added to the commit.");
        }

        /* parentID 即新 commit 的 first parent。 */
        String branchName = getCurrentBranchName();
        String parentID = getCurrentCommitID();
        Commit currentCommit = getCurrentCommit();

        /* 新快照 = 父快照复制后，先删 removal 再覆盖 addition。 */
        Map<String, String> newMap = new HashMap<>(currentCommit.getFilenameToBlob());
        newMap.keySet().removeAll(stage.removal);
        newMap.putAll(stage.addition);

        Date timestamp = new Date();
        Commit newCommit = new Commit(message, timestamp, parentID, secondParentID, newMap);

        /* 哈希参数按固定顺序收集；映射部分先排序再成对加入，
         *  secondParent 为 null 时折成空串，保证 ID 确定且可复现。 */
        List<Object> sha1Vals = new ArrayList<>();
        TreeMap<String, String> sortedMap = new TreeMap<>(newMap);
        sha1Vals.add("commit");
        sha1Vals.add(message);
        sha1Vals.add(String.valueOf(timestamp.getTime()));
        sha1Vals.add(parentID);
        sha1Vals.add(secondParentID == null ? "" : secondParentID);
        for (Map.Entry<String, String> entry : sortedMap.entrySet()) {
            sha1Vals.add(entry.getKey());
            sha1Vals.add(entry.getValue());
        }
        String newCommitID = Utils.sha1(sha1Vals);

        /* 落盘：先只增的 commit 对象，再前移分支指针，最后清空暂存区。 */
        File commitFile = Utils.join(COMMITS_DIR, newCommitID);
        Utils.writeObject(commitFile, newCommit);

        File branchFile = Utils.join(HEADS_DIR, branchName);
        Utils.writeContents(branchFile, newCommitID);

        Utils.writeObject(STAGE_FILE, new StagingArea());
    }

    /** 按固定版式打印单个 commit 块（log 与 global-log 共用）。
     *  <p>块依次为 ===、commit 行、仅 merge commit 有的 Merge 行、
     *  Date 行、消息行与一个空行。
     *  @param commit 要打印的 commit 对象。
     *  @param commitID 该对象对应的完整 40 位 commit ID。 */
    static void printCommit(Commit commit, String commitID) {
        Utils.message("===");
        Utils.message("commit %s", commitID);
        if (commit.getSecondParent() != null) {
            String firstParentShortSha = commit.getFirstParent().substring(0, 7);
            String secondParentShortSha = commit.getSecondParent().substring(0, 7);
            Utils.message("Merge: %s %s", firstParentShortSha, secondParentShortSha);
        }
        String dateStr = new SimpleDateFormat(
                "EEE MMM d HH:mm:ss yyyy Z", Locale.US)
                .format(commit.getTimestamp());
        Utils.message("Date: %s", dateStr);
        Utils.message(commit.getMessage());
        Utils.message("");
    }
}
