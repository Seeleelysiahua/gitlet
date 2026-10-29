# Gitlet Implementation Specification (CLAUDE.en.md)

> This file is the code-writing and AI-collaboration spec for this repo, followed by both the user and Claude Code (Claude Code auto-loads it each session).
> This file only specifies "how to translate the finalized design into code"; it does not re-decide any design.
> Chinese original: [CLAUDE.md](CLAUDE.md).

## 0. Resource Locations and Authority Hierarchy

- Official spec (highest authority): `/path/to/spec.md`
- Finalized design (single source of truth): `/path/to/design-docs/`
  - 00–05 shared design; `commands/` 13 local command cards; `remotes/` 5 remote command cards
  - Before writing any code, read the corresponding command card's six sections and the 02/03/04 chapters it references
- Reference implementation (read-only; usage discipline see §7): `/path/to/reference/gitlet/`

Authority hierarchy: **spec > finalized design docs > this spec**.

Conflict handling:

1. spec conflicts with design: spec wins, and clearly tell the user.
2. A design doc contradicts itself or design is uncovered while coding: **stop implementation, tell the user, first fix the design docs (including the key decision registry) then write code; do not privately decide in code**.
3. A command card conflicts with 02/03/04: handle per the previous rule, do not pick a side arbitrarily.

## 1. Architecture and Encapsulation

The class split is fixed at four (01 §2); no new business classes:

| Class | Coding rules |
| --- | --- |
| `Main` | Very thin; only `main(String[] args)` whose body is just `Repository.run(args);`. No dispatch, no checks |
| `Repository` | All static; holds path constants, the `run` entry, 18 command methods, the cross-command helpers registered in 03 §6, and per-command private helpers. Commands are not classes |
| `Commit` | Pure data, immutable: `implements Serializable`, `private static final long serialVersionUID = 1L`; five fields `private final`; five-arg constructor; defensively copies the input Map at construction; getter returns an unmodifiable view; **does not compute its own commit ID** (ID computed externally by Repository per 02 §7) |
| `StagingArea` | Pure data, mutable: `implements Serializable`, `serialVersionUID = 1L`; `addition` (TreeMap) and `removal` (TreeSet) are **package-private final fields** (not public); same-package Repository operates on them directly; no-arg constructor creates an empty object; **implements no staging rules** (01:29 finalized) |
| `Utils` | Course-provided, **do not modify**. blob is not a class; in memory it is just `byte[]` |

### Repository internal sections (comment sections; methods strictly in this order)

```text
1. Path constants (CWD, GITLET_DIR and File constants for objects/refs/HEAD/stage/remotes)
2. run: entry four-step check pipeline + try/catch + dispatch (the only catch is here)
3. Command methods (in §5 implementation order; method names same as commands, hyphen to camelCase, only operands)
4. Cross-command helpers (methods in the 03 §6 registry; the body is defined only at its owning command's specified place)
5. Single-card private helpers (e.g. checkout's three private dispatches, merge's split-point three methods)
```

- Mark each section with a block comment line, e.g. `/* ---------- Command methods ---------- */`.
- **Within a section, also follow §5 implementation order**: helper order matches the implementation order of the commands they serve (e.g. three-hop → `makeCommit` (commit) → `printCommit` (log)), so a top-to-bottom read matches development/implementation order.
- Method length soft limit ~60 lines (~one screen). Longer: extract private methods per command card section 3.
- Cross-command helpers follow "extract only when the same logic appears in a second command": when extracting, sync backfill the 03 §6 table, object interface list, and the owning command's and callers' section 4; keep the three signatures consistent.
- Except for the skeleton's existing `CWD`/`GITLET_DIR` constants, no public fields.

## 2. Java Style Baseline (CS61B style61b + project additions)

1. Indent **4 spaces, no tabs**.
   - **Code line ≤ 140 chars** (hard limit by char count); wrap if longer, continuation indented 8 spaces relative to line start or aligned by expression. Mainly constrains extremely long ifs, parameter lists, and chained calls.
   - **Comments / Javadoc by readability, not mechanical char counting**: wrap Chinese at semantic short-phrase boundaries (phrase / comma / clause), not mid-word.
   - This line width is wider than the CS61B checkstyle default of 80; if checkstyle is enabled set line width to 140 (usually let it ignore comment lines), otherwise rely on manual review.
2. **K&R braces**: opening brace does not start a new line; even single-line loop/branch bodies must have braces. User preference: put `else` / `else if` on a new line, i.e. the previous branch's `}` stands on its own line, then `else` (or `else if`) on the next line, not `} else {`.
   ```java
   if (cond) {
       ...
   }
   else {
       ...
   }
   ```
3. **Declare File objects as local variables before use**: whenever a path produced by `Utils.join(...)` participates in read/write, first write `File xxxFile = Utils.join(...)`, then pass that variable to `Utils.readContents/writeContents/readObject/writeObject` etc.; do not inline `Utils.join(...)` in call arguments.
4. **Take intermediate locals for long chained calls**: for chains like `targetedCommit.getFilenameToBlob().containsKey(...)`, first write `Map<String,String> filenameToBlob = commit.getFilenameToBlob();` then use it, shortening lines and improving readability.
5. **Extract private methods for repeated logic**: when two methods share large identical logic, extract one private helper for both (e.g. checkoutFile/checkoutCommit share restoring one file from a commit); existence checks may use the result of `get()` then `== null` instead of a `containsKey` + `get` double lookup.
6. Naming: classes `UpperCamelCase`; methods and variables `lowerCamelCase`; constants `UPPER_SNAKE_CASE`. Names must be meaningful; no `a1`, `tmp2`, etc.
7. Every class and every method must have Javadoc: methods write `@param` per parameter, non-void writes `@return`, helpers throwing GitletException write `@throws` explaining the trigger (no design section numbers).
8. Import specific classes, no wildcards; Utils calls always `Utils.`-qualified (no static import).
9. Spaces around operators and after commas; one blank line between methods; declare locals near first use.
10. No swallowing exceptions; no catch except `catch (GitletException e)` in `run`; do not use exceptions for non-normal control flow.
11. Error message literals may only be copied verbatim from the design error message list; no concat/rewrite; do not duplicate the same message in multiple places (converge via helper); throw sites generally need no comment; if necessary state only the trigger condition, no design section numbers.
12. Paths must always be composed from constants via `Utils.join(...)`; no bare `".gitlet"` scattered strings (except card step prose — code must use constants).
13. checkstyle: at the foundation stage try to run CS61B checkstyle (jar at `/path/to/checkstyle-jar/`) and include it in L0; if it cannot run under JDK 26, fall back to AI review checking this section item by item.

## 3. Comment Specification

- Comment language: **Chinese**; terms follow the English requirement glossary (e.g. stage, current commit, untracked).
- Class Javadoc: what it is + responsibility boundary. Keep the skeleton `@author TODO`; fill uniformly before submission.
- Method Javadoc: only what the method **does**, its parameters, return value, and boundary. **Do not write "who calls it / who shares it / which usage"** (e.g. do not write "usage 3 shared with reset", "called by merge") — call relationships are visible from code; comments do not carry them.
- Field comments: the five Commit fields, the two StagingArea fields, and each path constant get one `/** */` line explaining meaning and physical location.
- Inline comments only write what the code itself cannot say (why, constraints, invariants):
  - write order (new objects → branch pointer → clear stage) and the reason;
  - check-line failure direction (why `!exists()`), MECE branches, blocking-check pass conditions;
  - spec timing exemptions, reasons for actively traversing the objects dir;
  - complex steps summarized in short Chinese.
- **Code comments do not write design doc section references** (no `02 §2`, `03 §1`, `01:29`, `card §3` numbers); source cross-checking is done via the design docs; comments are self-explanatory only.
- No boilerplate comments restating code (e.g. `i++; // increment i`); no commented-out code; TODOs must carry a description and owner.

## 4. Error Handling and Failure Atomicity (coding discipline)

- All failures go only through the unified reporting chain: `throw Utils.error("verbatim message from the error message list")` → `run` catch → `Utils.message(e.getMessage())` → `System.exit(0)`. The whole program prints errors only here.
- Normal output only via `Utils.message(...)`; no System.out direct error printing.
- Entry four-step pipeline order fixed: empty input → unknown command → operand count/format → not initialized (`init` skips the last step); step 3 only checks arg shape, no `.gitlet` read.
- Every write command strictly has three phases: **check (read-only) → in-memory compute → write to disk**. Any statement that can throw must be before the first write.
- Multi-file write order fixed: ① add-only new objects (blob/commit) → ② move branch pointer → ③ clear stage; step ③ must not come early; checkout usage 3 writes HEAD last.
- Five protected states: working directory, staging area, `.gitlet`, branch pointer, HEAD.
- Serialization only for Commit and StagingArea (`serialVersionUID = 1L`); objects store only ID strings; blob uses raw bytes; HEAD and refs use single-line text.
- Except for abbreviated resolution, `global-log`, and `find` (spec-authorized), no listing the `objects/` dir to obtain objects; anything reachable by parent/pointer is read directly by ID.

## 5. Implementation Order and Single-Command Definition of Done (DoD)

Implementation order (dependency order; advance in this order, do not implement later commands early):

0. Foundation: path constants, `run` (pipeline + empty dispatch shell), Commit, StagingArea; try checkstyle
1. `init`
2. `add`
3. `commit` (including three-hop helpers, `makeCommit`)
4. `log` (including `printCommit`), `global-log`, `find`
5. `rm`
6. `branch`, `rm-branch`
7. `checkout` (extract `resolveCommitID`, `replaceWorkingDir` here)
8. `reset`
9. `status`
10. `merge` (split-point three methods private; evaluate whether to introduce JUnit now)
11. `add-remote` (extract `readRemotes`), `rm-remote`
12. `fetch` (extract `copyMissingObjects`), `push`, `pull`

Per-command DoD (done only when all satisfied):

- [ ] Code conforms item by item to command card §3; new helpers backfilled into 03 §6 / object interface list
- [ ] `make` zero warnings; checkstyle if available zero violations
- [ ] Add this command's `.in` case in `testing/student_tests/`, covering all checked items in 05 §2: normal path, each failure message verbatim, state unchanged after failure
- [ ] Expected output/state **hand-written** (per spec and the 04 list), not generated from own program output
- [ ] `make check` all green (4 official samples + all own cases, no regression)
- [ ] Corresponding checkboxes in 05 §2 checked
- [ ] L3 manual reconciliation done (applicable items: error messages verbatim against 04; `.gitlet` increase; dangerous commands' five states unchanged)
- [ ] AI review + manual review both pass (§8)
- [ ] One git commit per §9

## 6. Test Specification

Three-layer testing, integration tests primary (05 §1):

- **L0 compile gate**: `make clean && make`, zero warnings; checkstyle if runnable also zero violations. Build with make, **not Maven** (pom parent `../library-sp21` missing locally, Java version mismatch).
- **L1 integration tests (main)**: official `testing/tester.py` runs `.in` scripts.
  - Own cases always in `testing/student_tests/`; do not touch `testing/samples/`; preset files in `testing/src/`.
  - Naming: `testNN-command-scenario.in`, own numbering from 10 (avoid samples 01–04).
  - Volatile output like dates and commit IDs use regexes in `definitions.inc` (`${COMMIT_HEAD}` etc.); expected blocks use `<<<*`; byte-exact comparison only uses `<<<`.
  - Full suite: `make check`; single case: `python3 testing/tester.py --verbose testing/student_tests/xxx.in`; add `--keep` to inspect internal state.
  - When starting the first case (init), Claude teaches `.in` syntax and how to run it live.
- **L2 unit tests (deferred)**: do not pre-build JUnit. At merge, if split-point algorithm debugging needs it, download junit-platform-console-standalone for targeted unit tests; `Utils.sha1` and serialization round-trips are already covered end-to-end by L1; do not unit test early.
- **L3 manual reconciliation (fallback)**: error messages byte-exact against the 04 list; after commit, `.gitlet` increase ≤ total staged file size; after a dangerous command failure, five states byte-exact consistent.

## 7. Reference Implementation Usage Discipline

`/path/to/reference/proj2` may be read as reference, rules:

1. First write yourself per the command card; look at it after writing or when stuck; do not line-by-line translate or copy-paste.
2. When it conflicts with a command card/spec, the command card and spec win — the reference implementation may have bugs.
3. Use it only for: in-class section organization, Java/Utils API usage, cross-check after writing.
4. If used for formal CS61B grading, referencing others' (including AI) implementations must first confirm the course academic policy; even for self-study, understanding it yourself first is a prerequisite.

## 8. Review Process (human + AI)

Before each command's DoD reaches commit, two fixed reviews:

1. **AI review**: Claude reviews `git diff` against the checklist below and outputs graded issues (blocking / suggestion), does not directly change code:
   - Command card §3 item-by-item conformance (step order, check-line order and failure direction)
   - 04 messages verbatim (byte level); entry pipeline order
   - Atomicity: all throws before the first write, three-section write order, five protected scopes
   - 03 disk layout, serialization, abbreviated resolution, no-scan-dir constraints
   - Command card §6 runtime/memory reconciliation
   - §2 style and §3 comments/Javadoc
   - Test coverage per 05 §2 and hand-written expectations
2. **Manual review**: the user reads `git diff` and confirms the code matches the intent when writing the design card.

After both pass and the user explicitly instructs, commit.

## 9. Git Commit Specification

- Granularity: **one command one commit**, implementation code and its test cases in the same commit, not split; foundation is the first commit; pure docs/spec use a separate `docs:` commit; bug fixes and refactors each a separate commit.
- Message: Chinese + Conventional Commits prefix — `feat:` (new command), `test:` (pure tests), `fix:`, `refactor:`, `docs:`, `chore:`.
- Subject line short (~≤50 chars spirit, one clear Chinese sentence), imperative mood, no ending period; if background is needed, blank line then body, body lines ≤72 chars, write "why" not "what" (the diff is self-evident).
- Before commit `make check` must be all green; `.class`, sentinel, etc. are already ignored by .gitignore and must not be committed.
- Commit only when the user explicitly instructs; do not proactively push; currently developing on the master branch.
- Commits involving Claude append at the end of the message:

  `Co-Authored-By: Claude Code <noreply@anthropic.com>`

## 10. AI Collaboration Discipline

- Before writing any code, Read the corresponding command card's six sections and the related 02/03/04 original text; "said before" does not count; memory-based conclusions must be re-read and cite line numbers.
- Implement one command at a time (or one explained logic unit); do not casually implement later commands.
- On a design contradiction or uncovered point, immediately stop and tell the user (§0); do not privately decide.
- Do not copy reference implementation code; its API usage and file organization may be referenced.
- Report each round: which files changed, compile result, `make check` result, next-step suggestion.
- Edits must show a tool success receipt; re-read key changes from disk before reporting.
