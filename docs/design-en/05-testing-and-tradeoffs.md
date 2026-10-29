# 05 Testing Strategy and Design Tradeoffs

This doc answers two questions: how to verify the implementation is correct (test strategy and coverage checklist), and why the design looks like this (tradeoffs and known risks).

The tradeoff pool has 59 full entries (including command-level details and source line numbers); section 3 here is a 42-entry selected version.

## 1. Test Strategy

Three layers: integration tests as main, unit tests as support, manual reconciliation as fallback.

- **Integration tests (main)**: per spec, write integration tests for each command (spec:665), using `testing/tester.py` to run `.in` scripts (`python3 testing/tester.py --verbose <file>`, add `--keep` for debugging to keep temp dir and inspect `.gitlet` internal state). Own cases go in `testing/student_tests/` (do not touch samples); preset files go in `testing/src/`. Each command covers at least: all normal behavior, each failure message verbatim, state invariance after failure.
- **Unit tests (support)**: spec does not provide or require them (implementation-specific). Originally planned JUnit only for pure pieces not depending on command flow (deferred during implementation, not landed): `Utils.sha1` stability and type-tag distinction ("commit"/"blob" different inputs give different IDs), Commit / StagingArea serialization round-trip field invariance. Actually covered by 54 integration tests.
- **Manual reconciliation (fallback)**: error messages verbatim against [04 Error Handling](04-error-handling.md) list (not just spec); after commit, `.gitlet` increase ≤ total size of staged files; after dangerous command failure, five states (working directory / staging area / `.gitlet` / branch pointer / HEAD) byte-for-byte identical to before.

Acceptance anchor: skeleton's four sample checkpoint tests (init / basic checkout / basic log / backtrack checkout) must pass first.

High-risk areas (write first, run repeatedly): merge (split point two boundaries, decision table five branches, conflict format including one-side-deleted empty section, merge commit two parents and message verbatim); dangerous commands' untracked blocking (rm/checkout/reset/merge); abbreviated ID 0/1/multiple split; remote command object integrity and fast-forward checks.

## 2. Test Coverage Checklist

List key scenarios per command; check off one item each time an `.in` case is written during implementation.

**Entry pipeline (one per command is enough)**

- [ ] Empty input reports `Please enter a command.`; unknown command reports `No command with that name exists.`; wrong operand count/format reports `Incorrect operands.`; non-init command in uninitialized dir reports `Not in an initialized Gitlet directory.`

**init**

- [x] First success creates `.gitlet` skeleton and auto-commits initial commit
- [x] Repeated init reports `A Gitlet version-control system already exists in the current directory.`

**add**

- [ ] New file staged successfully (blob on disk, ID in addition)
- [ ] Repeated add with same content has no effect (no disk write)
- [ ] After staging, change file back to original version; add auto-unstages
- [ ] Overwrite already-staged version (same-key update)
- [ ] add a file already marked for removal by rm → cancel removal mark
- [ ] Missing file reports `File does not exist.`

**commit**

- [ ] Normal commit: new map = parent map + addition − removal
- [ ] Empty message reports `Please enter a commit message.`
- [ ] No staged changes reports `No changes added to the commit.`
- [ ] Mixed add/delete/modify in one commit
- [ ] Commit does not clear working directory files

**rm**

- [ ] Staged new file → remove from stage (working file kept)
- [ ] Tracked file → mark removal + delete working file
- [ ] Neither → report `No reason to remove the file.`
- [ ] After delete then commit, file removed from new commit map

**log / global-log / find**

- [ ] log walks firstParent to initial, format verbatim (including `Date:` and blank line after block)
- [ ] Merge commit prints extra `Merge: <7 chars> <7 chars>` line
- [ ] global-log prints all even if creation order is shuffled
- [ ] find single match / multiple matches all printed / no match reports `Found no commit with that message.`

**status**

- [ ] Five sections complete (empty sections still print headers), blank line count between sections correct
- [ ] Four modified/deleted combination suffixes correct (including "deleted not staged")
- [ ] Untracked included (including revived copy after rm)
- [ ] Current branch marked with `*`, branches lexicographic

**checkout (three usages)**

- [ ] Usage 1 current version / usage 2 historical version (full ID + abbreviated + ambiguous abbreviated reports `No commit with that id exists.`)
- [ ] File not in commit reports `File does not exist in that commit.`
- [ ] Usage 3 switch branch: write target files, delete extra tracked files, clear stage, HEAD switched last
- [ ] Usage 3 failures: branch missing / is current branch / untracked blocking reports `There is an untracked file in the way; delete it, or add and commit it first.`
- [ ] Usage 3 does not touch untracked files

**branch / rm-branch**

- [ ] New branch points to current commit / duplicate reports `A branch with that name already exists.`
- [ ] Delete branch / missing reports `A branch with that name does not exist.` / delete current branch reports `Cannot remove the current branch.`

**reset**

- [ ] Restore old files + move current branch pointer + clear stage
- [ ] Untracked blocking reports / ID missing reports / abbreviated resolution works

**merge (highest risk)**

- [ ] split point == merged branch head: prints `Given branch is an ancestor of the current branch.`, zero changes
- [ ] split point == current branch head: fast-forward, prints `Current branch fast-forwarded.`, HEAD unchanged
- [ ] Decision table five branches one each: given changed but I did not → adopt + stage; given deleted but I did not → delete + removal; both changed/deleted same → no-op; different changes → conflict
- [ ] Conflict format verbatim (`<<<<<<< HEAD` / `=======` / `>>>>>>>`), including empty section when one side deleted
- [ ] On conflict, merge commit is still created, then prints `Encountered a merge conflict.`
- [ ] Merge commit has two parents, message verbatim `Merged [given] into [current].`
- [ ] Zero changes reports `No changes added to the commit.`
- [ ] Failures: non-empty stage reports `You have uncommitted changes.` / branch missing / self-merge / untracked blocking

**Remote commands (bonus)**

- [ ] add-remote adds + duplicate reports `A remote with that name already exists.`; first run creates remotes file
- [ ] rm-remote deletes + missing name reports; can re-add after delete
- [ ] fetch: remote dir missing and unregistered remote name both report `Remote directory not found.`; remote missing branch reports `That remote does not have that branch.`; first run creates `refs/remotes/<r>/<b>`; re-fetch with no new content writes no objects; diamond history copied completely
- [ ] push: fast-forward success (remote missing objects filled, pointer advanced) / non-fast-forward reports `Please pull down remote changes before pushing.` / remote missing branch creates it
- [ ] pull: success path = fetch + merge; merge failure keeps fetch result

**General regression**

- [ ] Failure atomicity: after each failure scenario, five states byte-for-byte identical to before
- [ ] After commit, `.gitlet` increase ≤ total staged file size

## 3. Design Tradeoffs

Selected version (four columns: decision / alternative / reason / cost).

### Architecture and object model

| Decision | Alternative (rejected) | Reason | Cost |
| --- | --- | --- | --- |
| Blob is not a Java class; in memory just `byte[]` | Create a class for blob (with filename/path fields) | blob is just file content; content addressing only needs bytes, filename ownership is maintained by commit snapshot | blob has no independent behavior; operations are all Utils byte read/write |
| StagingArea is a separate class (addition Map + removal Set) | Put into Repository static fields / merge into Commit | Need to hold two entry groups and execute staging rules; as a class it can be serialized whole and init directly new empty object | One more class and one stage file |
| Main is very thin; command logic/entry checks/dispatch all in Repository; entry `Repository.run(args)` | Main does dispatch / each command is a class | Centralized coordination, one-way dependency | Repository is large |
| Commit stores full snapshot, immutable after construction | Store diff from parent | checkout/reset can restore by taking one commit, no history replay | Map entries re-stored per commit (blob bytes shared by content address; cost only map table) |
| HEAD stores only branch name string (no detached HEAD) | HEAD points directly to commit ID | current commit looked up via HEAD → branch pointer → commit two levels, unified model, no detached state | No real Git detached HEAD (spec does not require) |
| Remote map can only add/delete whole entries, cannot change value | Allow changing existing remote path | Matches spec semantics; simple implementation | Changing path requires delete and re-add |

### Hashing and persistence

| Decision | Alternative (rejected) | Reason | Cost |
| --- | --- | --- | --- |
| Object ID = SHA-1 (type tag first arg + fixed-order fields), sorted map passed per argument, null → "" | Concatenate raw field strings / no type tag | Any field change changes ID; tag prevents commit/blob namespace collision | ID computation must strictly sort; strong encoding discipline |
| commit ID computed externally by Repository; Commit stays pure data class | Compute inside constructor | Hash rule appears only at call site once | Caller must remember to compute ID; cannot new and use directly |
| objects split into commits/, blobs/ subdirs + bare 40-char ID filename | Flat dir + filename prefix commit_/blob_ | Directory distinguishes type, mutually verified with type tag; bare ID is hash itself | Type info depends on directory position; moving file loses type |
| Objects flat, no first-2-char fan-out | Git-style fan-out (objects/ab/cdef…) | spec exempts abbreviated lookup timing; course scale has no dirent pressure | Single dir object count theoretically large (not felt in practice) |
| Objects only add, never modify/delete, no GC | Overwrite / refcount delete | Add-only is naturally idempotent, simplifies concurrency and failure recovery | Orphan objects waste disk (spec does not check or penalize) |
| stage single state file, overwrite whole | One file per staged entry | One writeObject ensures staging self-consistency | Small change rewrites whole stage (usually small) |
| init creates empty stage, does not create remotes (missing = empty map) | init also creates empty remotes | stage is high-frequency core state, avoid null checks; remotes is optional feature may never be used | remotes readers must tolerate missing file |
| Objects store only SHA-1 ID strings, no nested serialization, no transient | Nested object graph | Each commit independent file, read/write not chained (echoes spec "follow the vine") | Getting related object requires reading file by ID again (multiple IO) |
| Branch uses independent ref file under refs/heads (one file one pointer) | Single branches big Map file | Add/delete branch = add/delete file; matches real Git layout | Listing branches requires listing dir (plainFilenamesIn) |
| Repository helpers deliberately left blank, extracted per "second card appears" | Pre-design full helper set | Signatures should come from real reuse; pre-listing misses combinations or creates useless methods | Early cards have local duplication (e.g. three-hop), extracted when triggered |

### Error handling and failure atomicity

| Decision | Alternative (rejected) | Reason | Cost |
| --- | --- | --- | --- |
| Unified reporting chain throw `Utils.error(...)` → run catch → message → `System.exit(0)` | Each command println+return / checked exceptions | Unchecked exception penetrates layers to single exit; verbatim message has one print point | Control flow via exceptions; must catch only in run |
| Entry four-step pipeline order fixed (empty input → unknown command → operands → not initialized), init skips last step | Arbitrary order per entry | Previous step is prerequisite; fixed order mechanically checkable | New commands must follow pipeline |
| Three phases check → in-memory compute → write; multi-file order add-only objects → branch pointer → clear stage | Check-as-you-write / clear stage before pointer | Once writing starts no business failure; clearing stage first loses add changes | All failure conditions must be checked before write (dangerous commands most) |
| Error messages centralized in one list (local+remote), command cards only reference | Messages scattered per command | Verbatim message has single source, easy to reconcile with spec | Adding message requires registering in list, one extra step |

### Command implementation highlights

| Decision | Alternative (rejected) | Reason | Cost |
| --- | --- | --- | --- |
| add writes blob on the spot (read bytes → compute ID → write objects → ID into stage) | Only stage content, write blob at commit | addition already stores blob ID (02 finalized); on-the-spot write matches content addressing, blob add-only idempotent | May create orphan blob not yet referenced by commit (no GC, spec does not penalize) |
| add uses `stageChanged` flag to converge disk writes; stage written once only if changed | Each branch writes writeObject / unconditional write-back | Multiple branches share one "should write" decision; no change means no disk write | Must initialize flag correctly or miss write |
| commit snapshot = copy parent map then batch removeAll+putAll; feed hash with TreeMap | Modify parent map in place / loop + manual sort | Commit immutable, no in-place change; batch one-liner, TreeMap gives order in one line | Each commit copies whole current map (linear, meets target) |
| Clear stage by `new StagingArea()` overwrite, do not clear read object | clear() read object then write back | Empty object is canonical "cleared" form, one less step, does not touch read object | Read old object still non-empty in memory (command ends immediately, no effect) |
| rm write order: delete working file first, then write stage | Write stage then delete file | In crash window file still recoverable via checkout from current commit; reverse order silently loses tracking on next commit | Extreme crash leaves file deleted but stage unchanged (recoverable, non-zero cost) |
| find no match uses throw chain, not direct print in command body | Print directly per spec:327 wording | Message is in 04 list; whole program has one error exit | Check can only happen after traversal (post-check line) |
| status decision tree first level branches on inW (file in working dir or not) | First level on inR (staged removal or not) | Split by operation nature: only when W exists compare content; blob fetch appears once in safe branch | R revival case degraded to !inR veto, slightly less intuitive |
| checkout usage 3 delete condition does not add `&& !inR` (R revived copy also deleted) | Add !inR to protect revived working file after staged removal | spec:397 delete obligation has no R exception; 411 blocking only protects overwrite; 413 does not protect staged removal | User's uncommitted copy recovered after rm is deleted on branch switch (spec intends this) |
| checkout reads blob and writes working dir on the fly, no pre-read whole snapshot | Pre-read all blobs then write uniformly | 04 already guarantees no business failure after write; pre-read does not add real atomicity, only memory | Extreme system-level crash may half-switch working dir (recoverable via write order + retry) |
| Abbreviated helper resolveCommitID returns full ID String | Return Commit / return (ID,Commit) pair new type | reset needs both readObject and ID to write pointer; Commit does not store its own ID | Caller readObject again (constant, exempt) |
| Working dir switch extracts wide helper replaceWorkingDir (blocking check inside) | Narrow helper leaves blocking to caller / inline each | checkout usage 3 and reset blocking rules verbatim isomorphic; only inside ensures no missed check order | Helper responsibility wide (read stage + list dir + check + write/delete) |

### Graph algorithm topic (merge)

| Decision | Alternative (rejected) | Reason | Cost |
| --- | --- | --- | --- |
| split point = common ancestor with max depth (depth(v)=1+max(two parents); lemma: u ancestor of w ⟹ depth(u)<depth(w)) | BFS nearest-first intersection / "shortest distance to head" / classic LCA | Only "max" fully layers ancestor/descendant and is not fooled by merge second parent shortcut; worst Ω(N) must read all, memoized O(N) already optimal | Need extra proof of monotonic lemma; depth is external table (Commit does not store own ID) |
| Boundary handling: compute split point first, then compare with two head IDs string-wise three-way dispatch | Inline early exit during traversal | max-depth loop outputs correct s at boundaries; flat logic, no in-loop special case, easy final review | Boundary cases still run full second pass (constant) |
| Normal merge uses two boolean main switches (cChanged/gChanged) to collapse 4-branch decision table | Expand spec seven rules verbatim into 13+ line if chain | Compare blob ID = compare content; except conflict, no file content read; Objects.equals unifies present/absent and same/different | Decision table (exhaustive display) and implementation (4 branches) two layers; final review must cross-verify coverage |
| Merge commit extracts shared helper makeCommit(message, secondParentID), commit delegates passing null | Merge inlines copy of commit write logic | Isomorphic logic copy drifts; helper's empty stage check naturally satisfies spec:552 "let normal commit error pass" | Back-fix finalized commit card; merge writes stage then helper re-reads constant disk round-trips |
| split point no read cache, depth tie no tie-breaker | Add HashMap cache / add id lexicographic tie-break | Each commit at most two reads still linear; spec:512 split point is "a latest common ancestor" (any valid) | Common ancestor read one extra time; tie result depends on traversal order (valid) |
| Conflict content String five-segment direct concat, no newline fix | Byte-wise concat / patch newline | spec:534 straight concatenation, 539 explicitly pathological files not patched | Non-UTF-8 file via String round-trip may distort (spec does not test) |
| merge stage conditional write-back (writeObject only if non-empty) | Unconditional write-back (same content overwrite harmless on zero action) | Zero-action path throws via makeCommit empty stage check; conditional write ensures "throw before any write" verbatim | One more condition branch |

### Remote commands

| Decision | Alternative (rejected) | Reason | Cost |
| --- | --- | --- | --- |
| remotes access only narrow helper readRemotes() (read+null check), write-back left to caller | Wide helper includes write-back / five cards inline | Shared logic only "read+null check"; write-back is logicless one line, wrapping is thin wrapper | Write-back one line duplicated across remote cards (acceptable) |
| fetch/push copy uses existence-pruned traversal (stop whole branch when dst has commit) | Full history traversal copy | Requirement asks for "not yet existing" subset; correctness relies on invariant "has commit ⟹ all ancestors present" (init/commit/merge/fetch structural guarantee) | Invariant no runtime guard, maintained by command set structure |
| Copy extracts bidirectional generic helper copyMissingObjects(src, dst, headID) | fetch/push each writes direction-hardcoded traversal | Two traversals line-by-line isomorphic except src/dst; one place maintain | Caller must think which is src which is dst |
| pull = fetch + merge command-level composition; merge branch resolution extended to two dirs (heads first then remotes) | Extract merge core helper taking ID / fetch writes two branch copies | Two dirs only changes merge one step; pull zero new logic, failure union natural | merge [remote]/[branch] manual also works (superset behavior, spec does not test) |

## 4. Known Risks

- **Abbreviated ID resolution is linear directory scan**: checkout/reset abbreviated resolution lists all filenames in objects/commits/ and does prefix matching, O(object count). spec explicitly exempts timing and it is same-source as flat no-shard layout decision (see §3 hashing/persistence group).
- **Graph traversal recursion depth**: merge split point uses recursive DFS, depth grows with history length; theoretical stack overflow risk on extremely long history (course scale fine). fetch/push copy traversal uses explicit stack (ArrayDeque) iteration, no such risk.
- **Merge crash residue during write**: add-only objects harmless, but "current untracked" files already written to working dir will be blocked by blocking check on rerun; manually delete or reset first (merge card §5 accounts per window). System-level crash is outside spec failure atomicity requirement (which targets business failures).
- **Cross-repo copy integrity depends on structural invariant**: "repo has commit X ⟹ all ancestors of X present" maintained by init/commit/merge/fetch/push copy methods, no runtime guard; if future command incompletely introduces a commit, pruned traversal will miss copying (§3 remote group has line).
- **Pathological files and inputs not defended**: conflict concat of file without trailing newline sticks (spec explicitly ignores); non-UTF-8 byte file conflict content via String round-trip may distort (spec does not test); same-name directory blocking, restrictedDelete silent failure rely on Utils self-protection.
- **Orphan objects waste disk**: add-only, no delete, no GC — blobs staged by add but not committed, commits left by crash all permanently occupy disk; spec does not check or penalize.
- **Remote path not validated**: path stored by add-remote may be bad until fetch/push/pull reports `Remote directory not found.` (spec explicitly says add-remote does not validate).
