# 00 设计概述

> 本文件是 Gitlet 设计文档的总览，完整索引见 [README](README.md)。需求文档回答“做什么”，本套设计文档回答“怎么做”。

## 1. 系统简介

Gitlet 是一个用 Java 编写的版本控制系统：支持保存当前项目的版本、恢复历史版本、以分支并行开发，并将分支的改动合并。系统以内容寻址方式存储 Commit 与 Blob（相同内容共享同一对象、不重复存储），全部内部状态持久化于 `.gitlet` 目录。

## 2. 设计目标

- Gitlet 的所有内部数据（如 commit、blob 等对象）都必须储存在 `.gitlet` 中。
- Gitlet 的所有命令都必须满足需求文档中对应命令文档提出的 runtime、memory usage 要求。
- Gitlet 输出的所有错误信息必须按照 spec 给出的格式输出，大小写、空格、标点、结尾句号都要保留。
- Gitlet 命令出错的时候不能修改、增加、删除任何数据，包括 working directory file 和 `.gitlet` 内部的数据。
- Gitlet 不重复储存数据：一个文件在两个 commit 中都相同，那就只存一个 blob。因此，提交后 `.gitlet` 增量不超过本次提交暂存文件总大小。


## 3. 非目标

- Gitlet 不递归处理子目录。
- Gitlet 保存的 merge commit 只支持两个 parent，不能超过两个。
- Gitlet 不处理 spec 未列出的错误情况。
- Gitlet 的 HEAD 引用 branch，而不是 commit ID。

## 4. 文档导航

| 主题                  | 文档                  |
| ------------------- | ------------------- |
| 类划分与命令分发            | [01 总体架构](01-architecture.md)         |
| 对象字段、集合选型与 SHA-1 规则 | [02 对象模型](02-object-model.md)         |
| `.gitlet` 目录结构、序列化与缩写 ID 解析机制 | [03 持久化与文件布局](03-persistence-and-layout.md)     |
| 错误消息、入口检查           | [04 错误处理](04-error-handling.md)         |
| 本地命令实现（13 个）        | [commands/](commands/) 目录 |
| 远程命令（加分项，5 个）       | [remotes/](remotes/) 目录    |
| 测试策略、设计取舍与风险        | [05 测试策略与设计取舍](05-testing-and-tradeoffs.md)    |

## 5. 全局约定

- 术语一律沿用课程需求文档的术语表，本文档不重新定义。
- 对象的字段结构统一在 [02 对象模型](02-object-model.md)定义，磁盘上的存放方式统一在 [03 持久化与文件布局](03-persistence-and-layout.md)定义。命令文件需要这些信息时，用链接指向这两篇，不再重新描述 —— 否则同一内容出现在多处，改动时容易漏改、自相矛盾。例如 checkout.md 中需要说明 commit 的字段时，只写“见 [02 对象模型](02-object-model.md)”。
