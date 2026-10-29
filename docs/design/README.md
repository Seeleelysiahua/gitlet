# Gitlet 设计文档

> 这里收录的是**实现之前**定稿的设计文档：6 篇共享设计、18 张命令卡，
> 以及设计过程中的决策登记与接口清单。实现代码见仓库根目录的
> [`gitlet/`](../../gitlet/)。

## 怎么读

1. 先读 [00 设计概述](00-overview.md)，建立全局图景，再按 01 → 05 的顺序读共享底座；
2. 关心某个命令怎么实现时，查 [`commands/`](commands/) 下对应的卡片；远程命令在 [`remotes/`](remotes/)；
3. 想知道“为什么这样设计”，查[关键决策登记](decisions.md)；想查跨命令的方法签名，查[对象接口清单](interfaces.md)。

## 共享设计

| 文档 | 内容 |
| --- | --- |
| [00 设计概述](00-overview.md) | 系统简介、设计目标与非目标、文档导航 |
| [01 总体架构](01-architecture.md) | 类全景图、依赖方向、入口检查管线与命令分发 |
| [02 对象模型](02-object-model.md) | blob、Commit 与 StagingArea 的字段、不变量与 ID 计算 |
| [03 持久化与文件布局](03-persistence-and-layout.md) | `.gitlet` 目录树、序列化格式、读写规则与跨命令 helper 登记 |
| [04 错误处理](04-error-handling.md) | 逐字错误消息清单、统一汇报链与退出行为 |
| [05 测试策略与设计取舍](05-testing-and-tradeoffs.md) | 三层测试策略、覆盖清单、设计取舍与已知风险 |

## 本地命令卡（13 张）

本地卡固定六节：方法签名 / 依赖数据 / 执行步骤 / 复用与被复用 /
状态修改与失败保护 / 性能核对；远程卡为前五节（无性能核对）。

| 命令 | 卡片 | 一句话 |
| --- | --- | --- |
| `init` | [init.md](commands/init.md) | 建立 `.gitlet`、initial commit 与 `master` |
| `add` | [add.md](commands/add.md) | 暂存文件；内容与当前提交相同则取消暂存 |
| `commit` | [commit.md](commands/commit.md) | 提交暂存区并前移当前分支 |
| `rm` | [rm.md](commands/rm.md) | 取消暂存；被追踪时标记删除并删工作文件 |
| `log` | [log.md](commands/log.md) | 沿 first parent 打印当前分支历史 |
| `global-log` | [global-log.md](commands/global-log.md) | 打印全库所有 commit |
| `find` | [find.md](commands/find.md) | 按提交信息精确查找 |
| `status` | [status.md](commands/status.md) | 五节状态报告 |
| `checkout` | [checkout.md](commands/checkout.md) | 取当前文件 / 历史文件 / 切换分支三种用法 |
| `branch` | [branch.md](commands/branch.md) | 新建分支指针 |
| `rm-branch` | [rm-branch.md](commands/rm-branch.md) | 删除分支指针，不删 commit |
| `reset` | [reset.md](commands/reset.md) | 回退当前分支与工作目录 |
| `merge` | [merge.md](commands/merge.md) | 三路合并：split point、决策表与冲突处理 |

## 远程命令卡（5 张）

| 命令 | 卡片 | 一句话 |
| --- | --- | --- |
| `add-remote` | [add-remote.md](remotes/add-remote.md) | 登记远程名与远程 `.gitlet` 路径 |
| `rm-remote` | [rm-remote.md](remotes/rm-remote.md) | 删除远程登记 |
| `fetch` | [fetch.md](remotes/fetch.md) | 复制缺失对象并更新远程跟踪分支 |
| `push` | [push.md](remotes/push.md) | 快进检查通过后向远程推送 |
| `pull` | [pull.md](remotes/pull.md) | `fetch` 后 `merge` 的命令级拼装 |

## 参考

- [关键决策登记](decisions.md)：决策 / 理由 / 出处的索引，便于回原文核对设计
- [对象接口清单](interfaces.md)：写命令卡前查的一览表，防止重复定义构造方法或方法

## 说明

- 本目录**不含课程原始 spec 与需求整理**（版权原因）。文中出现的 `spec` 指课程
  需求文档，`(spec:665)` 之类的写法是当时引用的行号。
- 文档写于实现之前，保留设计阶段的原貌；若个别细节与最终代码不一致，以代码与
  测试为准。
- [05 测试策略与设计取舍](05-testing-and-tradeoffs.md)第 2 节保留了设计阶段的覆盖
  清单（复选框未回勾）；实际落地的用例见 [`testing/student_tests/`](../../testing/student_tests/)。
- 错误消息的逐字清单以 [04 错误处理](04-error-handling.md)为准，代码中的字面量
  与该清单一致。
