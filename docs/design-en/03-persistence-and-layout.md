# 03 Persistence and File Layout

> 【Shared base】This file is the single source of truth for on-disk layout.

## 1. `.gitlet` Directory Tree

```text
.gitlet/
├── objects/
│   ├── commits/
│   └── blobs/
├── refs/
│   ├── heads/
│   └── remotes/
│       └── <remote name>/
├── HEAD
├── remotes
└── stage
```

| Data | Location | File name |
| --- | --- | --- |
| commit | objects/commits/ | bare 40-char ID |
| blob | objects/blobs/ | bare 40-char ID |
| local branch pointer | refs/heads/ | branch name |
| remote tracking branch | refs/remotes/<remote name>/ | remote branch name |
| HEAD | .gitlet/HEAD | |
| StagingArea | .gitlet/stage | |
| remote map | .gitlet/remotes | |

- Immutable objects (commit/blob): only add, never modify or delete. Read by ID.
- Name-based refs (local/remote branches): local branches can be added/deleted; remote tracking branches are added or updated by fetch and are not deleted separately. Content is ID and can be rewritten.
- Single state files (HEAD/stage/remotes map): HEAD is read at startup to get branch name. stage/remotes are fully deserialized at startup.

## 2. Object File Storage

Real Git does not separate object types. All objects are stored as `objects/<first 2>/<last 38>`, and type info is in the object content header (commit/blob/tree…). Gitlet does not do this. The spec already exempts timing for abbreviated lookup ("we won't worry about timing"). See [05 Testing and Tradeoffs](05-testing-and-tradeoffs.md).

Object storage rules:

- Each commit object is one file at `objects/commits/<40-char ID>`.
- Each blob object is one file at `objects/blobs/<40-char ID>`.
- Object files are never modified or deleted after writing.
- If a file with the same ID already exists, content is the same, so overwriting and skipping are equivalent (`copyMissingObjects` checks existence and skips first).

## 3. Abbreviated ID Resolution

Abbreviated ID applies to:

- Only commands that accept a commit ID argument: checkout, reset.
- Blobs do not apply. No command lets the user type a blob ID.

Abbreviated ID rules:

- List all file names under `objects/commits/`.
- Filter IDs with the user input as prefix. Convert to lowercase before comparing.
- Three outcomes: 0 matches → fail; exactly 1 → use full ID; multiple → ambiguous fail.
- Error message text is not in this section. See [04 Error Handling](04-error-handling.md).

Abbreviated ID input boundary:

- Prefix length 1–40. A 40-char input is a special case of prefix matching (matches itself or 0). The implementation does not separately validate length; it matches directly with `startsWith`.
- IDs are stored and compared in lowercase. If user input has uppercase, convert to lowercase before matching.

## 4. Single State Files: stage and remotes

stage:

- stage is stored at `.gitlet/stage`, written by serialization and read back by deserialization.
- `init` creates a new empty stage file.
- When staging state changes, rewrite stage.

remotes:

- remote is stored at `.gitlet/remotes`, written by serialization and read back by deserialization.
- `init` does not create remotes. If not found, treat as empty map. The first `add-remote` creates it.
- When reading remotes, deserialize to a HashMap and look up entries in this map.

Both stage and remotes must satisfy: validate before modification; after passing, change memory; then overwrite the whole file. If validation fails, do not touch files.

stage is a core high-frequency state read/written by many commands and is created by init, so reads do not need to handle missing. remotes is an optional bonus feature and may never be used, so init does not create it and reads treat missing as empty map.

## 5. Serialization Plan

Serializable classes: Commit, StagingArea. They need `private static final long serialVersionUID = 1L` to prevent old data deserialization failure after field changes. Main, Repository, and Utils are used to accept arguments, execute command logic, and help read/write files. They are not written to disk, so they do not need serialization.

Data in `.gitlet` has three read/write forms:

- Object serialization: Commit, StagingArea, remotes are written as a whole by Java serialization and read back by deserialization.
- Raw bytes: blob has no object structure, read/write raw bytes directly.
- Single-line text: HEAD stores branch name, branch files under refs store commit ID. Both are plain text strings, no serialization.

Java serialization writes referenced objects too. So objects only store SHA-1 ID strings. String is basic serializable data and does not pull in other objects. So each commit is an independent file.

## 6. Read/Write Helper Conventions

Methods provided by Utils:

| Method | Purpose |
| --- | --- |
| readContents / writeContents | Read/write raw bytes or text |
| readContentsAsString | Read file as string (for HEAD, refs) |
| readObject / writeObject | Deserialize read object / serialize write object (uses serialize inside) |
| plainFilenamesIn | List normal file names in a directory (for abbreviated resolution, status). Return null if directory does not exist |
| restrictedDelete | Delete a normal file (for rm). Parent directory must contain `.gitlet`; does not delete directories |
| join | Join paths |
| sha1 | Compute SHA-1. Supports varargs / List forms |
| error / message | error only creates exception, caller must throw. message prints one line (reporting chain in 04) |

Repository helper methods: extracted when reused by command cards. Registered here; command cards refer to this table.

| Signature | Purpose | First registered |
| --- | --- | --- |
| `static String getCurrentBranchName()` | Read HEAD, get current branch name | commit card |
| `static String getCurrentCommitID()` | Call getCurrentBranchName(), read refs/heads branch pointer, get current commit ID | commit card |
| `static Commit getCurrentCommit()` | Call getCurrentCommitID(), deserialize current commit from objects/commits | commit card |
| `static void printCommit(Commit commit, String commitID)` | Print one commit in log format | log card |
| `static String resolveCommitID(String givenID)` | Abbreviated ID resolution: list objects/commits, match lowercase prefix. 0 or multiple matches throw `No commit with that id exists.`, return unique full ID | checkout card |
| `static void replaceWorkingDir(Commit currentCommit, Commit targetedCommit)` | Replace whole working directory: read stage, list normal files, check untracked file blocking (throw `There is an untracked file in the way; delete it, or add and commit it first.`), write all target tracked files, delete files tracked by current but not target. Does not move branch pointer or stage | checkout card |
| `static void makeCommit(String message, String secondParentID)` | Shared commit implementation: blank message / empty stage checks → copy current map and apply stage → five-arg constructor → sha1 (secondParentID null becomes "") → write object → move current branch pointer → clear stage. Used by commit (pass null) and merge (pass merged branch head ID). Method body only in commit card §3 | commit card |
| `static HashMap<String, String> readRemotes()` | Read `.gitlet/remotes` whole deserialize. If file missing (init does not create), return empty map. Shared by remote commands. Method body only in add-remote card §3 | add-remote card |
| `static void copyMissingObjects(String srcGitlet, String dstGitlet, String headID)` | Cross-repo object copy: from src headID walk parents, copy commits and blobs missing in dst. Stop whole branch when dst already has commit. src/dst are `.gitlet` dir path strings (remote path read from map at runtime; local side passes `GITLET_DIR.getPath()`). Shared by fetch (remote → local) and push (local → remote, opposite direction). Method body only in fetch card §3 | fetch card |

The first three are no-arg static, nested bottom-up, with one disk read at the bottom. Commands stop at the layer they need (branch name, ID, or object). `printCommit` is an output helper that holds a Commit object and its own ID (Commit does not store its own ID); log and global-log share it. `resolveCommitID` is an abbreviated-resolution helper that holds the raw string (possibly abbreviated/uppercase) and returns the full ID; checkout usage 2 and reset share it; callers readObject by the returned ID themselves. `replaceWorkingDir` is a whole-working-directory replacement helper that holds current/target Commit objects and does the blocking check and full write/delete; checkout usage 3 and reset share it; branch pointer movement and stage clearing are done by callers. `makeCommit` is a commit helper that holds the message and second parent ID (null becomes "" per 02 and participates in the hash); commit and merge share it; method body only in commit card §3. `readRemotes` is a remotes-read helper that returns an empty map when missing (init does not create remotes); remote commands share it; method body only in add-remote card §3. `copyMissingObjects` is a cross-repo copy helper that holds source/target `.gitlet` directory path strings (both String: remote path read from map at runtime, local side passes `GITLET_DIR.getPath()`) and does existence-pruned traversal copy; fetch and push use it in opposite directions; method body only in fetch card §3. When a new reusable read/write fragment appears in later command cards, add it to this table by the "extract when the second card appears" convention.

Except for abbreviated ID resolution, global-log, and find, no command may locate objects by listing the objects/ directory. Objects reachable by parent ID or branch pointer (e.g. log walking head history along firstParent) must be read directly by ID along references.
