# 02 Object Model

> 【Shared base】This file is the single source of truth for object structure.

## 1. Blob

A blob stores the raw bytes of a tracked file. It does not store file name or path.

There is no special Java class for blob. In memory it is just a `byte[]`. The mapping between file names and blobs is kept by Commit.

A blob ID is computed by SHA-1 from its raw bytes. It is a 40-character hex string. Same file content gives same ID, so multiple commits can refer to the same blob and same content is stored only once.

## 2. Commit

A commit represents a snapshot of all tracked files at some time.

A commit has these fields:

| Field | Type | Storage | Nullable |
| --- | --- | --- | --- |
| message | String | Commit message text | No |
| timestamp | Date | Commit time | No |
| firstParent | String | ID of first parent | Yes |
| secondParent | String | ID of second parent | Yes |
| filenameToBlob | `Map<String, String>` | File name to blob ID map | No (initial commit uses empty map) |

Parent values for three commit types:

| Commit type | firstParent | secondParent |
| --- | --- | --- |
| initial commit | null | null |
| normal commit | first parent ID | null |
| merge commit | commit ID pointed by current branch pointer | commit ID pointed by merged branch pointer |

For a merge commit, both parents are the head commits of the current branch and merged branch when merge starts.

Core properties:

- A commit stores a full snapshot, not a diff from parent.
- A commit is immutable. Once created, its fields do not change.

## 3. StagingArea

StagingArea records updates for the next commit compared with the current commit.

It has two groups:

| Group | Type | Storage | Meaning |
| --- | --- | --- | --- |
| staged for addition | `Map<String, String>` | file name → blob ID | file to add / update |
| staged for removal | `Set<String>` | file names | file to remove |

StagingArea is empty when created. Its state is changed by `add`, `rm`, and `commit`:

- `add` / `rm`: modify the two groups by add/rm rules.
- `commit`: after success, clear the staging area back to empty. This is not a normal modification.

Core properties:

- StagingArea is mutable. Before the next commit, staged file names and blob IDs can be changed.

## 4. Remote Mapping

Remote mapping is not a separate class. This section only describes its logical data shape.

Logically, remote mapping is a map from remote name to remote `.gitlet` directory path. Adding a new remote means adding an entry: remote name → remote `.gitlet` directory path.

Initially the remote map is empty. Its state is changed by `add-remote` and `rm-remote`:

- add-remote: add a remote name → path entry.
- rm-remote: delete an entry by remote name.

Branches created by fetch named `[remote name]/[remote branch name]` reuse the branch model in section 5. The only differences are that the name contains `/` and the directory is different.

Core properties:

- Remote mapping is mutable. The path of an existing remote name cannot be changed.

## 5. Logical Representation of Branch and HEAD

Branch and HEAD are not separate classes. This section only describes their logical data shape.

Logically, a branch is a map from branch name to commit ID. Each commit ID in the map is the branch pointer of that branch. So creating a branch means adding an entry: new branch name → current commit ID.

HEAD itself is a branch name string. So we can use the branch name in HEAD to look up the commit ID in the branch map, and get the current commit. HEAD only refers to a branch, never directly to a commit.

Initially the branch map has only: `master` → initial commit ID; `HEAD = master`.

Core properties:

- Branch is mutable. The commit ID for a branch name can change.
- HEAD is mutable. It changes only when switching branches. It does not directly follow commits.

## 6. Collection Choices

| Location | Implementation | Reason |
| --- | --- | --- |
| Commit.filenameToBlob | HashMap | Lookup by key only, never ordered output |
| Remote map | HashMap | Lookup by key only, never ordered output |
| StagingArea addition | TreeMap | status lists by name in order, so the collection has order and output does not sort manually |
| StagingArea removal | TreeSet | status lists by name in order |
| branch map | refs/heads/ files (one file per branch) | Not an in-memory collection; status uses plainFilenamesIn lexicographic output |

Constraint: commit maps cannot be changed after construction, and must not leak modifiable internal collections.

## 7. SHA-1 Rules

- blob: compute ID from type tag and raw bytes.

```text
sha1("blob", byte[] raw bytes)
```

- commit: compute ID from type tag and all fields.

```text
sha1("commit",
     message,
     time representation,
     firstParent  (null → ""),
     secondParent (null → ""),
     filename1, blobID1, filename2, blobID2, …)
```

- Put the type tag first.
- Include all fields in fixed order. Any change changes the ID.
- Time representation uses the millisecond value of Date (`getTime()`) converted to string.
- Map part: sort file names first, then pass each (filename, blob ID) as separate arguments. Do not concatenate raw strings.
- When a parent is null, use one fixed representation `""`.

Commits and blobs are stored in different directories, so even if hashes are the same they do not overwrite each other. See [03 Persistence and File Layout](03-persistence-and-layout.md).
