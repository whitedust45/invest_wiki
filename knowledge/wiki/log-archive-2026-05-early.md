---
id: log-archive-2026-05-early
title: 2026-05 早期操作日志归档 / Early May 2026 Operation Log Archive
type: source
domain: history
topic: [asset-allocation]
tags: [操作日志, 归档, 2026-05]
status: active
version: 2026-06-23
effective_date: 2026-05-14
supersedes: []
superseded_by: []
see_also: [log-archive-2026-05]
sources:
  - type: personal
    context: 2026-05-13 至 2026-05-14 操作日志从 2026-05 月度归档继续拆分
    date: 2026-06-23
summary: 归档 2026-05-13 至 2026-05-14 的知识库初始化与早期素材摄入记录
---

# 2026-05 早期操作日志归档

> 本页从 [[log-archive-2026-05]] 继续拆分而来，只保留 2026-05-13 至 2026-05-14 的历史操作记录。

## [2026-05-14] ingest | 资产配置基础概念补充

- 新增 raw 原文：`raw/2026-05-14-investor-gov-asset-allocation-guide.md`
- 新增来源摘要：[[2026-05-14-investor-gov-asset-allocation-guide]]
- 新增概念页：[[asset-allocation]]
- 更新 `schema/glossary.md`：将 asset-allocation 标记为已建
- 更新 `wiki/index.md`：收录新增来源页与概念页，并清空待创建页面表
- 更新 [[overview]]：将 asset-allocation 纳入组合阅读路径

## [2026-05-14] ingest | 行为金融偏误补充（loss aversion / confirmation bias）

- 新增 raw 原文：`raw/2026-05-14-thedecisionlab-loss-aversion.md`
- 新增 raw 原文：`raw/2026-05-14-simplypsychology-confirmation-bias.md`
- 新增来源摘要：[[2026-05-14-thedecisionlab-loss-aversion]]、[[2026-05-14-simplypsychology-confirmation-bias]]
- 新增概念页：[[loss-aversion]]、[[confirmation-bias]]
- 更新 `schema/glossary.md`：将 loss-aversion、confirmation-bias 标记为已建
- 更新 `wiki/index.md`：收录新增来源页与概念页，并清理对应待创建项
- 更新 [[overview]]：将心理路径从待建改为可读，并补充组合阅读路径

## [2026-05-14] ingest | 巴菲特 2025 致股东信

- 新增 raw 原文：`raw/2026-05-14-buffett-2025-shareholder-letter.md`
- 新增来源摘要：[[2026-05-14-buffett-2025-shareholder-letter]]
- 新增实体页：[[berkshire-hathaway]]
- 新增实体页：[[warren-buffett]]
- 新增概念页：[[capital-allocation]]、[[insurance-float]]、[[intrinsic-value]]、[[margin-of-safety]]、[[dcf-model]]、[[moat]]、[[roe]]、[[diversification]]、[[position-sizing]]
- 新增分析页：[[buffett-2025-shareholder-letter-framework]]
- 更新 `schema/glossary.md`：补充 capital-allocation、insurance-float 并将 intrinsic-value、margin-of-safety、dcf-model、moat、roe、diversification、position-sizing 标记为已建
- 更新 `wiki/index.md`：收录 roe、diversification、position-sizing，并清理已完成的待创建项
- 更新 [[overview]]：吸收“尽快纠错、资本配置、浮存金质量”三条顶层原则

## [2026-05-13] init | 项目初始化

- 创建目录结构（wiki/entities, concepts, sources, analyses, portfolios）
- 编写 CLAUDE.md（Agent 冷启动指引）
- 编写 schema/（frontmatter, naming, linking, glossary）
- 创建 wiki/index.md、wiki/log.md、wiki/overview.md
- 创建 .claude/skills/（query, ingest, lint）
- 知识库就绪，等待第一次素材摄入
