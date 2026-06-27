#!/usr/bin/env python3

from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional


DEFAULT_PROFILE_FILE = "knowledge/wiki/portfolios/personal-position-sizing-framework.md"
FALLBACK_DEFAULTS = {
    "entry_watch_threshold": 40.0,
    "ic_open_threshold": 30.0,
    "pb_add_threshold": 20.0,
    "im_priority_threshold": 10.0,
    "rebalance_risk": 0.55,
    "post_add_max_risk": 0.70,
    "post_add_stress_drop": 0.20,
}


def parse_drop_list(raw: str) -> List[float]:
    return [float(part.strip()) for part in raw.split(",") if part.strip()]


def money(value: float) -> str:
    return f"{value / 10000:.2f}万"


def percent(value: float) -> str:
    return f"{value * 100:.1f}%"


def table(headers: List[str], rows: List[List[str]]) -> str:
    widths = [len(h) for h in headers]
    for row in rows:
        for i, cell in enumerate(row):
            widths[i] = max(widths[i], len(cell))
    parts = []
    head = " | ".join(headers[i].ljust(widths[i]) for i in range(len(headers)))
    sep = "-+-".join("-" * widths[i] for i in range(len(headers)))
    parts.append(head)
    parts.append(sep)
    for row in rows:
        parts.append(" | ".join(row[i].ljust(widths[i]) for i in range(len(headers))))
    return "\n".join(parts)


@dataclass(frozen=True)
class Config:
    ic_points: float
    im_points: float
    multiplier: float
    margin_rate: float
    initial_capital: float
    futures_capital: float
    ic_only_drops: List[float]
    add_im_drops: List[float]
    post_add_drops: List[float]
    post_add_stress_drop: float
    post_add_max_risk: float
    rebalance_risk: float
    current_drop: Optional[float]
    ic_pb_percentile: Optional[float]
    pb_percentile: Optional[float]
    pb_add_threshold: float
    entry_watch_threshold: float
    ic_open_threshold: float
    im_priority_threshold: float

    @property
    def reserve_capital(self) -> float:
        return self.initial_capital - self.futures_capital

    @property
    def ic_nominal(self) -> float:
        return self.ic_points * self.multiplier

    @property
    def im_nominal(self) -> float:
        return self.im_points * self.multiplier


def occupied_margin(nominal: float, margin_rate: float) -> float:
    return nominal * margin_rate


def top_up_needed(equity: float, occupied: float, target_risk: float) -> float:
    if target_risk <= 0:
        raise ValueError("target_risk 必须大于 0")
    return max(0.0, occupied / target_risk - equity)


def safe_input(prompt: str) -> str:
    try:
        return input(prompt)
    except EOFError:
        return ""


def prompt_float(label: str, default: float) -> float:
    raw = safe_input(f"{label} [{default}]: ").strip()
    return default if not raw else float(raw)


def prompt_optional_float(label: str, default: Optional[float]) -> Optional[float]:
    default_label = "留空跳过" if default is None else str(default)
    raw = safe_input(f"{label} [{default_label}]: ").strip()
    if not raw:
        return default
    return float(raw)


def prompt_drop_list(label: str, default: List[float]) -> List[float]:
    default_text = ",".join(str(item) for item in default)
    raw = safe_input(f"{label} [{default_text}]: ").strip()
    return default if not raw else parse_drop_list(raw)


def resolve_profile_path(profile_path: str) -> Path:
    candidate = Path(profile_path)
    if candidate.is_absolute():
        return candidate
    cwd_candidate = Path.cwd() / candidate
    if cwd_candidate.exists():
        return cwd_candidate
    return Path(__file__).resolve().parent.parent / candidate


def load_profile_defaults(profile_path: str) -> dict[str, float]:
    path = resolve_profile_path(profile_path)
    if not path.exists():
        return {}

    start_marker = "<!-- strategy-script-defaults:start -->"
    end_marker = "<!-- strategy-script-defaults:end -->"
    in_block = False
    defaults: dict[str, float] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if line == start_marker:
            in_block = True
            continue
        if line == end_marker:
            break
        if not in_block or not line or line.startswith("#"):
            continue
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        key = key.strip()
        value = value.strip()
        try:
            defaults[key] = float(value)
        except ValueError:
            continue
    return defaults


def resolved_value(cli_value: Optional[float], profile_defaults: dict[str, float], key: str) -> float:
    if cli_value is not None:
        return cli_value
    if key in profile_defaults:
        return profile_defaults[key]
    return FALLBACK_DEFAULTS[key]


def ic_only_rows(cfg: Config) -> List[List[str]]:
    rows: List[List[str]] = []
    for drop in cfg.ic_only_drops:
        nominal = cfg.ic_nominal * (1 - drop)
        equity = cfg.futures_capital - cfg.ic_nominal * drop
        occupied = occupied_margin(nominal, cfg.margin_rate)
        risk = float("inf") if equity <= 0 else occupied / equity
        rows.append([
            percent(drop),
            money(equity),
            money(occupied),
            "爆仓/失效" if equity <= 0 else percent(risk),
            money(top_up_needed(equity, occupied, cfg.rebalance_risk)),
        ])
    return rows


def recommended_extra_for_im_add(cfg: Config, drop_before_add: float) -> float:
    current_equity = cfg.futures_capital - cfg.ic_nominal * drop_before_add
    combined_nominal = (cfg.ic_nominal + cfg.im_nominal) * (1 - drop_before_add)
    stressed_equity_without_extra = current_equity - combined_nominal * cfg.post_add_stress_drop
    stressed_occupied = occupied_margin(combined_nominal * (1 - cfg.post_add_stress_drop), cfg.margin_rate)
    return max(0.0, stressed_occupied / cfg.post_add_max_risk - stressed_equity_without_extra)


def should_add_im(cfg: Config) -> bool:
    if cfg.pb_percentile is None or cfg.current_drop is None:
        return False
    return cfg.pb_percentile <= cfg.pb_add_threshold


def current_state_rows(cfg: Config) -> List[List[str]]:
    if cfg.current_drop is None:
        return []
    rows: List[List[str]] = []
    current_nominal = cfg.ic_nominal * (1 - cfg.current_drop)
    current_equity = cfg.futures_capital - cfg.ic_nominal * cfg.current_drop
    current_occupied = occupied_margin(current_nominal, cfg.margin_rate)
    current_risk = float("inf") if current_equity <= 0 else current_occupied / current_equity
    rows.append([
        "当前仅IC",
        percent(cfg.current_drop),
        money(current_equity),
        money(current_occupied),
        "爆仓/失效" if current_equity <= 0 else percent(current_risk),
        money(top_up_needed(current_equity, current_occupied, cfg.rebalance_risk)),
    ])
    if cfg.pb_percentile is not None:
        extra = recommended_extra_for_im_add(cfg, cfg.current_drop)
        combined_nominal = (cfg.ic_nominal + cfg.im_nominal) * (1 - cfg.current_drop)
        add_equity = current_equity + extra
        add_occupied = occupied_margin(combined_nominal, cfg.margin_rate)
        add_risk = float("inf") if add_equity <= 0 else add_occupied / add_equity
        rows.append([
            "当前加1手IM后",
            percent(cfg.current_drop),
            money(add_equity),
            money(add_occupied),
            "爆仓/失效" if add_equity <= 0 else percent(add_risk),
            money(top_up_needed(add_equity, add_occupied, cfg.rebalance_risk)),
        ])
    return rows


def valuation_summary_lines(cfg: Config) -> List[str]:
    lines: List[str] = []
    if cfg.pb_percentile is None:
        lines.append("未输入 PB 百分位，本次跳过估值触发判断。")
        return lines
    lines.append(f"当前 PB 百分位: {cfg.pb_percentile:.1f}%")
    lines.append(f"IM 加仓阈值: ≤ {cfg.pb_add_threshold:.1f}%")
    if should_add_im(cfg):
        lines.append("估值条件: 满足，可进入“只加 1 手 IM”的资金测算。")
        if cfg.current_drop is not None:
            extra = recommended_extra_for_im_add(cfg, cfg.current_drop)
            lines.append(f"按当前跌幅 {percent(cfg.current_drop)} 测算，建议额外补资: {money(extra)}")
            lines.append(f"其中建议转入期货账户: {money(extra * 0.85)}，留作备用池: {money(extra * 0.15)}")
    else:
        lines.append("估值条件: 不满足，当前更适合维持 IC 底仓，不建议新增 IM。")
    return lines


def signal_zone(pb_percentile: float, watch_threshold: float, open_threshold: float) -> str:
    if pb_percentile <= open_threshold:
        return "执行区"
    if pb_percentile <= watch_threshold:
        return "观察区"
    return "等待区"


def distance_to_threshold(pb_percentile: float, threshold: float) -> float:
    return max(0.0, pb_percentile - threshold)


def entry_signal_lines(cfg: Config) -> List[str]:
    if cfg.ic_pb_percentile is None:
        raise ValueError("entry signal 模式需要提供 --ic-pb-percentile")
    if cfg.pb_percentile is None:
        raise ValueError("entry signal 模式需要提供 --pb-percentile（IM 的 PB 百分位）")

    ic_zone = signal_zone(cfg.ic_pb_percentile, cfg.entry_watch_threshold, cfg.ic_open_threshold)
    im_zone = signal_zone(cfg.pb_percentile, cfg.entry_watch_threshold, cfg.pb_add_threshold)
    overall_zone = signal_zone(min(cfg.ic_pb_percentile, cfg.pb_percentile), cfg.entry_watch_threshold, cfg.ic_open_threshold)

    lines = [
        "未建仓信号模式",
        f"- IC PB 百分位: {cfg.ic_pb_percentile:.2f}% → {ic_zone}",
        f"- IM PB 百分位: {cfg.pb_percentile:.2f}% → {im_zone}",
        f"- 综合判断: {overall_zone}",
    ]

    if cfg.ic_pb_percentile <= cfg.ic_open_threshold:
        lines.append(f"- 第一手 IC: 允许进入建仓评估区（阈值 ≤ {cfg.ic_open_threshold:.1f}%）。")
    elif cfg.ic_pb_percentile <= cfg.entry_watch_threshold:
        lines.append(f"- 第一手 IC: 进入观察区，但还没到执行区；距离开仓阈值还差 {distance_to_threshold(cfg.ic_pb_percentile, cfg.ic_open_threshold):.2f} 个百分点。")
    else:
        lines.append(f"- 第一手 IC: 当前不建议开仓；距离观察阈值还差 {distance_to_threshold(cfg.ic_pb_percentile, cfg.entry_watch_threshold):.2f} 个百分点，距离开仓阈值还差 {distance_to_threshold(cfg.ic_pb_percentile, cfg.ic_open_threshold):.2f} 个百分点。")

    if cfg.pb_percentile <= cfg.im_priority_threshold:
        lines.append(f"- IM: 已进入极低估优先区（阈值 ≤ {cfg.im_priority_threshold:.1f}%），若已有 IC 底仓且资金允许，可优先评估单次加仓。")
    elif cfg.pb_percentile <= cfg.pb_add_threshold:
        lines.append(f"- IM: 已进入候选区（阈值 ≤ {cfg.pb_add_threshold:.1f}%），若已有 IC 底仓，可测算单次加仓。")
    else:
        lines.append(f"- IM: 当前不纳入单次加仓候选；距离候选阈值还差 {distance_to_threshold(cfg.pb_percentile, cfg.pb_add_threshold):.2f} 个百分点。")

    if cfg.ic_pb_percentile > cfg.entry_watch_threshold and cfg.pb_percentile > cfg.entry_watch_threshold:
        lines.append("- 结论: 当前整体处于等待区，建议继续空仓观察，不启动这套期货杠杆策略。")
    elif cfg.ic_pb_percentile <= cfg.ic_open_threshold:
        lines.append("- 结论: 可以开始评估第一手 IC 底仓，但 IM 仍需单独看其 PB 百分位是否进入候选区。")
    else:
        lines.append("- 结论: 当前更适合观察，不急于开第一笔；等 IC 再回到执行区再启动底仓逻辑。")

    return lines


def auto_brief_lines(cfg: Config) -> List[str]:
    if cfg.current_drop is None:
        if cfg.ic_pb_percentile is None or cfg.pb_percentile is None:
            raise ValueError("auto brief 模式在未建仓场景下需要提供 --ic-pb-percentile 与 --pb-percentile")
        ic_zone = signal_zone(cfg.ic_pb_percentile, cfg.entry_watch_threshold, cfg.ic_open_threshold)
        im_zone = signal_zone(cfg.pb_percentile, cfg.entry_watch_threshold, cfg.pb_add_threshold)
        lines = [
            "【自动看板】未建仓场景",
            f"- IC PB: {cfg.ic_pb_percentile:.2f}%（{ic_zone}）",
            f"- IM PB: {cfg.pb_percentile:.2f}%（{im_zone}）",
        ]
        if cfg.ic_pb_percentile > cfg.entry_watch_threshold and cfg.pb_percentile > cfg.entry_watch_threshold:
            lines.append("- 当前动作: 继续空仓等待，不启动策略。")
        elif cfg.ic_pb_percentile <= cfg.ic_open_threshold:
            lines.append("- 当前动作: 可以开始评估第一手 IC 底仓。")
        else:
            lines.append("- 当前动作: 进入观察区，继续等 IC 回到执行阈值。")
        if cfg.pb_percentile <= cfg.im_priority_threshold:
            lines.append("- IM 状态: 已进入极低估优先区，但前提仍是先有 IC 底仓。")
        elif cfg.pb_percentile <= cfg.pb_add_threshold:
            lines.append("- IM 状态: 已进入候选区，后续若已有 IC 底仓可评估单次加仓。")
        else:
            lines.append("- IM 状态: 暂不纳入加仓候选。")
        lines.append(f"- 关键阈值: IC ≤ {cfg.ic_open_threshold:.1f}% 启动底仓；IM ≤ {cfg.pb_add_threshold:.1f}% 进入候选；IM ≤ {cfg.im_priority_threshold:.1f}% 进入优先区。")
        return lines

    if cfg.pb_percentile is None:
        raise ValueError("auto brief 模式在已建仓场景下需要提供 --pb-percentile")
    current_nominal = cfg.ic_nominal * (1 - cfg.current_drop)
    current_equity = cfg.futures_capital - cfg.ic_nominal * cfg.current_drop
    current_occupied = occupied_margin(current_nominal, cfg.margin_rate)
    current_risk = float("inf") if current_equity <= 0 else current_occupied / current_equity
    lines = [
        "【自动看板】已持有 IC 场景",
        f"- 当前已跌幅: {percent(cfg.current_drop)}",
        f"- 当前 IM PB: {cfg.pb_percentile:.2f}%（加仓阈值 ≤ {cfg.pb_add_threshold:.1f}%）",
        f"- 当前仅 IC 风险度: {'爆仓/失效' if current_equity <= 0 else percent(current_risk)}",
    ]
    if not should_add_im(cfg):
        lines.append("- 当前动作: 不加 IM，维持 IC 底仓。")
        return lines
    extra = recommended_extra_for_im_add(cfg, cfg.current_drop)
    lines.extend([
        "- 当前动作: 满足 IM 单次加仓条件，可进入补资执行评估。",
        f"- 建议额外补资: {money(extra)}",
        f"- 其中转入期货账户: {money(extra * 0.85)}；留作备用池: {money(extra * 0.15)}",
    ])
    return lines


def decision_lines(cfg: Config) -> List[str]:
    if cfg.current_drop is None:
        raise ValueError("decision 模式需要提供 --current-drop")
    if cfg.pb_percentile is None:
        raise ValueError("decision 模式需要提供 --pb-percentile")

    current_nominal = cfg.ic_nominal * (1 - cfg.current_drop)
    current_equity = cfg.futures_capital - cfg.ic_nominal * cfg.current_drop
    current_occupied = occupied_margin(current_nominal, cfg.margin_rate)
    current_risk = float("inf") if current_equity <= 0 else current_occupied / current_equity

    lines = [
        "极简决策模式",
        f"- 当前 PB 百分位: {cfg.pb_percentile:.1f}%（阈值: ≤ {cfg.pb_add_threshold:.1f}%）",
        f"- 当前已跌幅: {percent(cfg.current_drop)}",
        f"- 当前仅持有 IC 的风险度: {'爆仓/失效' if current_equity <= 0 else percent(current_risk)}",
        f"- 当前仅持有 IC 时，补到 {percent(cfg.rebalance_risk)} 需补: {money(top_up_needed(current_equity, current_occupied, cfg.rebalance_risk))}",
    ]

    if not should_add_im(cfg):
        lines.append("- 结论: 当前不满足 IM 加仓估值条件，维持 IC 底仓，不建议新增 IM。")
        return lines

    extra = recommended_extra_for_im_add(cfg, cfg.current_drop)
    combined_nominal = (cfg.ic_nominal + cfg.im_nominal) * (1 - cfg.current_drop)
    add_equity = current_equity + extra
    add_occupied = occupied_margin(combined_nominal, cfg.margin_rate)
    add_risk = float("inf") if add_equity <= 0 else add_occupied / add_equity
    further_top_up = top_up_needed(
        add_equity - combined_nominal * cfg.post_add_stress_drop,
        occupied_margin(combined_nominal * (1 - cfg.post_add_stress_drop), cfg.margin_rate),
        cfg.rebalance_risk,
    )

    lines.extend([
        "- 结论: 当前满足 IM 单次加仓的估值条件。",
        f"- 建议额外补资: {money(extra)}",
        f"- 建议转入期货账户: {money(extra * 0.85)}；建议留作备用池: {money(extra * 0.15)}",
        f"- 若此刻加 1 手 IM，加完当下风险度约: {'爆仓/失效' if add_equity <= 0 else percent(add_risk)}",
        f"- 若加完后再跌 {percent(cfg.post_add_stress_drop)}，补到 {percent(cfg.rebalance_risk)} 还需再补: {money(further_top_up)}",
    ])
    return lines


def im_add_rows(cfg: Config) -> List[List[str]]:
    rows: List[List[str]] = []
    for drop in cfg.add_im_drops:
        extra = recommended_extra_for_im_add(cfg, drop)
        rows.append([
            percent(drop),
            money(extra),
            money(extra * 0.85),
            money(extra * 0.15),
        ])
    return rows


def post_add_rows(cfg: Config, drop_before_add: float, extra_capital: float) -> List[List[str]]:
    current_equity = cfg.futures_capital - cfg.ic_nominal * drop_before_add + extra_capital
    combined_nominal = (cfg.ic_nominal + cfg.im_nominal) * (1 - drop_before_add)
    rows: List[List[str]] = []
    for further_drop in cfg.post_add_drops:
        equity = current_equity - combined_nominal * further_drop
        occupied = occupied_margin(combined_nominal * (1 - further_drop), cfg.margin_rate)
        risk = float("inf") if equity <= 0 else occupied / equity
        rows.append([
            percent(further_drop),
            money(equity),
            money(occupied),
            "爆仓/失效" if equity <= 0 else percent(risk),
            money(top_up_needed(equity, occupied, cfg.rebalance_risk)),
        ])
    return rows


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="IC/IM 滚贴水长期持有策略压力测试")
    parser.add_argument("--interactive", action="store_true", help="进入交互式输入模式")
    parser.add_argument("--decision-mode", action="store_true", help="极简决策模式，只输出当前能否加 IM 与所需补资")
    parser.add_argument("--entry-signal-mode", action="store_true", help="未建仓信号模式，只输出等待/观察/执行区与建仓资格")
    parser.add_argument("--auto-brief-mode", action="store_true", help="Agent 自动模式：根据是否提供 current-drop 自动输出未建仓或已持仓简洁看板")
    parser.add_argument("--ic-points", type=float, default=5600, help="IC 指数点位")
    parser.add_argument("--im-points", type=float, default=5900, help="IM 指数点位")
    parser.add_argument("--multiplier", type=float, default=200, help="合约乘数")
    parser.add_argument("--margin-rate", type=float, default=0.16, help="保证金率，例如 0.16")
    parser.add_argument("--initial-capital", type=float, default=1_000_000, help="初始总资金")
    parser.add_argument("--futures-capital", type=float, default=550_000, help="初始放入期货账户的资金")
    parser.add_argument("--profile-file", default=DEFAULT_PROFILE_FILE, help="默认阈值配置文件，优先使用相对路径")
    parser.add_argument(
        "--ic-only-drops",
        default="0,0.10,0.15,0.20,0.25,0.30,0.35",
        help="只持有 1 手 IC 时的压力测试跌幅列表，逗号分隔",
    )
    parser.add_argument(
        "--add-im-drops",
        default="0.10,0.15,0.20,0.25",
        help="准备加 1 手 IM 时，相对初始已跌多少，逗号分隔",
    )
    parser.add_argument(
        "--post-add-drops",
        default="0,0.10,0.15,0.20,0.25",
        help="加完 IM 后继续下跌的压力测试跌幅列表，逗号分隔",
    )
    parser.add_argument(
        "--post-add-stress-drop",
        type=float,
        default=0.20,
        help="用于计算保守加仓资金时，假设加完 IM 后还会继续下跌多少",
    )
    parser.add_argument(
        "--post-add-max-risk",
        type=float,
        default=None,
        help="用于计算保守加仓资金时，希望在压力测试下最大风险度不超过多少",
    )
    parser.add_argument(
        "--rebalance-risk",
        type=float,
        default=None,
        help="风险度拉回目标，例如 0.55 表示 55%%",
    )
    parser.add_argument("--current-drop", type=float, default=None, help="当前相对初始建仓已跌多少，例如 0.18")
    parser.add_argument("--ic-pb-percentile", type=float, default=None, help="IC 的 PB 百分位，例如 25 表示 25%%")
    parser.add_argument("--pb-percentile", type=float, default=None, help="当前 PB 百分位，例如 18 表示 18%%")
    parser.add_argument(
        "--pb-add-threshold",
        type=float,
        default=None,
        help="只有 PB 百分位低于该阈值才允许新增 IM，默认 20",
    )
    parser.add_argument("--entry-watch-threshold", type=float, default=None, help="未建仓时的观察区阈值，默认从个人仓位框架读取")
    parser.add_argument("--ic-open-threshold", type=float, default=None, help="第一手 IC 的执行区阈值，默认从个人仓位框架读取")
    parser.add_argument("--im-priority-threshold", type=float, default=None, help="IM 的极低估优先区阈值，默认从个人仓位框架读取")
    return parser


def build_config(args: argparse.Namespace, profile_defaults: dict[str, float]) -> Config:
    return Config(
        ic_points=args.ic_points,
        im_points=args.im_points,
        multiplier=args.multiplier,
        margin_rate=args.margin_rate,
        initial_capital=args.initial_capital,
        futures_capital=args.futures_capital,
        ic_only_drops=parse_drop_list(args.ic_only_drops),
        add_im_drops=parse_drop_list(args.add_im_drops),
        post_add_drops=parse_drop_list(args.post_add_drops),
        post_add_stress_drop=resolved_value(args.post_add_stress_drop, profile_defaults, "post_add_stress_drop"),
        post_add_max_risk=resolved_value(args.post_add_max_risk, profile_defaults, "post_add_max_risk"),
        rebalance_risk=resolved_value(args.rebalance_risk, profile_defaults, "rebalance_risk"),
        current_drop=args.current_drop,
        ic_pb_percentile=args.ic_pb_percentile,
        pb_percentile=args.pb_percentile,
        pb_add_threshold=resolved_value(args.pb_add_threshold, profile_defaults, "pb_add_threshold"),
        entry_watch_threshold=resolved_value(args.entry_watch_threshold, profile_defaults, "entry_watch_threshold"),
        ic_open_threshold=resolved_value(args.ic_open_threshold, profile_defaults, "ic_open_threshold"),
        im_priority_threshold=resolved_value(args.im_priority_threshold, profile_defaults, "im_priority_threshold"),
    )


def interactive_config(profile_defaults: dict[str, float]) -> Config:
    print("进入交互式模式：直接回车可使用默认值。")
    defaults = Config(
        ic_points=5600,
        im_points=5900,
        multiplier=200,
        margin_rate=0.16,
        initial_capital=1_000_000,
        futures_capital=550_000,
        ic_only_drops=[0, 0.10, 0.15, 0.20, 0.25, 0.30, 0.35],
        add_im_drops=[0.10, 0.15, 0.20, 0.25],
        post_add_drops=[0, 0.10, 0.15, 0.20, 0.25],
        post_add_stress_drop=profile_defaults.get("post_add_stress_drop", FALLBACK_DEFAULTS["post_add_stress_drop"]),
        post_add_max_risk=profile_defaults.get("post_add_max_risk", FALLBACK_DEFAULTS["post_add_max_risk"]),
        rebalance_risk=profile_defaults.get("rebalance_risk", FALLBACK_DEFAULTS["rebalance_risk"]),
        current_drop=0.20,
        ic_pb_percentile=25.0,
        pb_percentile=18.0,
        pb_add_threshold=profile_defaults.get("pb_add_threshold", FALLBACK_DEFAULTS["pb_add_threshold"]),
        entry_watch_threshold=profile_defaults.get("entry_watch_threshold", FALLBACK_DEFAULTS["entry_watch_threshold"]),
        ic_open_threshold=profile_defaults.get("ic_open_threshold", FALLBACK_DEFAULTS["ic_open_threshold"]),
        im_priority_threshold=profile_defaults.get("im_priority_threshold", FALLBACK_DEFAULTS["im_priority_threshold"]),
    )
    return Config(
        ic_points=prompt_float("IC 点位", defaults.ic_points),
        im_points=prompt_float("IM 点位", defaults.im_points),
        multiplier=prompt_float("合约乘数", defaults.multiplier),
        margin_rate=prompt_float("保证金率", defaults.margin_rate),
        initial_capital=prompt_float("初始总资金", defaults.initial_capital),
        futures_capital=prompt_float("初始期货账户资金", defaults.futures_capital),
        ic_only_drops=prompt_drop_list("只持有 1 手 IC 的跌幅列表", defaults.ic_only_drops),
        add_im_drops=prompt_drop_list("计划观察的 IM 加仓跌幅列表", defaults.add_im_drops),
        post_add_drops=prompt_drop_list("加完 IM 后继续下跌的测试列表", defaults.post_add_drops),
        post_add_stress_drop=prompt_float("计算保守加仓资金时，假设加完 IM 后继续下跌多少", defaults.post_add_stress_drop),
        post_add_max_risk=prompt_float("上述压力下允许的最大风险度", defaults.post_add_max_risk),
        rebalance_risk=prompt_float("你希望补资后回到的目标风险度", defaults.rebalance_risk),
        current_drop=prompt_optional_float("当前实际已跌幅（用于即时决策）", defaults.current_drop),
        ic_pb_percentile=prompt_optional_float("当前 IC PB 百分位（用于未建仓信号）", defaults.ic_pb_percentile),
        pb_percentile=prompt_optional_float("当前 PB 百分位（用于估值触发）", defaults.pb_percentile),
        pb_add_threshold=prompt_float("IM 加仓的 PB 百分位阈值", defaults.pb_add_threshold),
        entry_watch_threshold=prompt_float("未建仓观察区阈值", defaults.entry_watch_threshold),
        ic_open_threshold=prompt_float("第一手 IC 的执行区阈值", defaults.ic_open_threshold),
        im_priority_threshold=prompt_float("IM 的极低估优先区阈值", defaults.im_priority_threshold),
    )


def main() -> None:
    args = build_parser().parse_args()
    profile_defaults = load_profile_defaults(args.profile_file)
    cfg = interactive_config(profile_defaults) if args.interactive else build_config(args, profile_defaults)

    if args.decision_mode:
        for line in decision_lines(cfg):
            print(line)
        return

    if args.entry_signal_mode:
        for line in entry_signal_lines(cfg):
            print(line)
        return

    if args.auto_brief_mode:
        for line in auto_brief_lines(cfg):
            print(line)
        return

    print("IC/IM 滚贴水长期持有策略压力测试")
    print("=" * 48)
    print(f"IC 名义本金: {money(cfg.ic_nominal)}")
    print(f"IM 名义本金: {money(cfg.im_nominal)}")
    print(f"初始总资金: {money(cfg.initial_capital)}")
    print(f"期货账户资金: {money(cfg.futures_capital)}")
    print(f"账户外备用池: {money(cfg.reserve_capital)}")
    print(f"保证金率: {percent(cfg.margin_rate)}")
    print()

    print("零、当前估值触发判断")
    for line in valuation_summary_lines(cfg):
        print(f"- {line}")
    if cfg.current_drop is not None:
        print(table(
            ["状态", "已跌幅", "期货账户权益", "占用保证金", "风险度", f"补到{percent(cfg.rebalance_risk)}需补"],
            current_state_rows(cfg),
        ))
    print()

    print("一、100 万起步、只持有 1 手 IC 的压力测试")
    print(table(
        ["IC跌幅", "期货账户权益", "占用保证金", "风险度", f"补到{percent(cfg.rebalance_risk)}需补"],
        ic_only_rows(cfg),
    ))
    print()

    print("二、在不同低位加 1 手 IM 时，建议额外补充多少资金")
    print(
        f"计算口径：要求“加完 IM 后再跌 {percent(cfg.post_add_stress_drop)}”时，风险度不超过 {percent(cfg.post_add_max_risk)}。"
    )
    print(table(
        ["已跌幅", "建议额外补资", "建议转入期货账户(85%)", "建议留作备用池(15%)"],
        im_add_rows(cfg),
    ))
    print()

    for drop in cfg.add_im_drops:
        extra = recommended_extra_for_im_add(cfg, drop)
        print(f"三、若已跌 {percent(drop)} 时加 1 手 IM（额外补 {money(extra)}），后续继续下跌的二阶段测试")
        print(table(
            ["加完后再跌", "期货账户权益", "占用保证金", "风险度", f"补到{percent(cfg.rebalance_risk)}需补"],
            post_add_rows(cfg, drop, extra),
        ))
        print()

    print("说明：")
    print("1. 这只是参数化压力测试，不构成投资建议。")
    print("2. 若保证金率、点位、期货账户初始资金改变，结果会同步变化。")
    print("3. 脚本只负责把估值触发和资金安全垫算清楚，不替你做方向判断。")
    print("4. 若你的第一原则是绝不爆仓，应优先保证补资能力，再考虑是否加 IM。")


if __name__ == "__main__":
    main()
