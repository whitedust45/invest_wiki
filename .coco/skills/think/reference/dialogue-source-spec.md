# `type: dialogue` 来源格式说明

## 背景

`/think` skill 产出的知识来自用户与 Agent 的对话讨论，不同于外部素材。
需要专门的来源类型来标记其性质和可信度。

## 完整格式

```yaml
sources:
  - type: dialogue
    context: "关于腾讯回购力度与内在价值的关系讨论"
    date: 2026-05-14
    participants: [用户, Agent]
    trigger: "我在想，腾讯现在的回购力度是不是说明管理层认为被低估了"
    rounds: 5                    # 追问轮数（快记模式写 0）
    depth: refined               # refined / raw / seed
```

## 字段说明

| 字段 | 必填 | 说明 |
|------|------|------|
| `type` | 是 | 固定为 `dialogue` |
| `context` | 是 | 对话主题，一句话 |
| `date` | 是 | 对话日期 |
| `participants` | 是 | 固定为 `[用户, Agent]` |
| `trigger` | 是 | 用户最初那句触发思考的话（原文） |
| `rounds` | 否 | 追问轮数，快记模式写 0 |
| `depth` | 否 | 讨论充分度：`refined`（深度讨论过）/ `raw`（快记）/ `seed`（种子想法） |

## 与查询的关系

当 `/query` 命中来源为 `type: dialogue` 的内容时：

- 置信度天然为 **medium**（无外部权威背书）
- 输出中标注"来源：个人思考讨论"
- 如果该观点后来被外部素材验证（通过 `/ingest`），
  建议用户将 `type: dialogue` 旁追加验证来源，提升置信度

## 示例

### 深度讨论产出

```yaml
sources:
  - type: dialogue
    context: "回购信号与内在价值判断的关系"
    date: 2026-05-14
    participants: [用户, Agent]
    trigger: "腾讯回购力度加大是不是低估信号"
    rounds: 4
    depth: refined
```

### 快记产出

```yaml
sources:
  - type: dialogue
    context: "关于消费降级趋势的直觉"
    date: 2026-05-14
    participants: [用户, Agent]
    trigger: "快记一下：今天看到拼多多增速超预期，感觉消费降级趋势比我预想的更持久"
    rounds: 0
    depth: raw
```

### 种子想法

```yaml
sources:
  - type: dialogue
    context: "AI 对估值框架的潜在颠覆"
    date: 2026-05-14
    participants: [用户, Agent]
    trigger: "有个模糊的想法：AI 会不会让传统 DCF 的假设体系失效"
    rounds: 2
    depth: seed
```
