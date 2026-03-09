package com.nse.algotrading.visualization;

import com.nse.algotrading.model.Position;
import com.nse.algotrading.model.Tick;
import com.nse.algotrading.risk.RiskManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Real-time ASCII terminal dashboard for live / paper trading.
 *
 * Renders a full-screen dashboard every tick including:
 *   - Header: time, mode, market status
 *   - Risk meter bar
 *   - Live positions table with color-coded PnL
 *   - Daily P&L and capital summary
 *   - Mini sparkline price charts (per symbol)
 *   - Recent signals log
 *   - Order activity feed
 *
 * Uses ANSI escape codes for color — works in any modern terminal.
 * Call refresh() each time you want to redraw the screen.
 */
public class ConsoleDashboard {

    // ── ANSI colors ────────────────────────────────────────────────────────
    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String DIM    = "\u001B[2m";
    private static final String RED    = "\u001B[31m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE   = "\u001B[34m";
    private static final String CYAN   = "\u001B[36m";
    private static final String WHITE  = "\u001B[37m";
    private static final String BG_RED = "\u001B[41m";
    private static final String BG_GREEN = "\u001B[42m";
    private static final String CLEAR  = "\u001B[2J\u001B[H";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DT_FMT   = DateTimeFormatter.ofPattern("dd-MMM HH:mm:ss");

    // Dashboard state
    private final String tradingMode;
    private final RiskManager riskManager;

    // Price history for sparklines (symbol → last 40 prices)
    private final Map<String, Deque<Double>> priceHistory = new LinkedHashMap<>();

    // Recent log lines
    private final Deque<String> signalLog  = new ArrayDeque<>();
    private final Deque<String> orderLog   = new ArrayDeque<>();
    private static final int    LOG_LINES  = 6;
    private static final int    SPARK_WIDTH = 20;

    // Tick cache
    private final Map<String, Tick> latestTicks = new LinkedHashMap<>();

    public ConsoleDashboard(String tradingMode, RiskManager riskManager) {
        this.tradingMode = tradingMode;
        this.riskManager = riskManager;
    }

    /**
     * Update dashboard with a new tick. Call from your tick handler.
     */
    public void onTick(Tick tick) {
        latestTicks.put(tick.getSymbol(), tick);
        priceHistory
                .computeIfAbsent(tick.getSymbol(), k -> new ArrayDeque<>())
                .addLast(tick.getLastTradedPrice());
        // Keep only SPARK_WIDTH+20 points for memory
        Deque<Double> hist = priceHistory.get(tick.getSymbol());
        while (hist.size() > SPARK_WIDTH + 20) hist.pollFirst();
    }

    /**
     * Log a signal event (shown in signal feed).
     */
    public void logSignal(String msg) {
        signalLog.addLast(LocalDateTime.now().format(TIME_FMT) + " " + msg);
        while (signalLog.size() > LOG_LINES) signalLog.pollFirst();
    }

    /**
     * Log an order event (shown in order feed).
     */
    public void logOrder(String msg) {
        orderLog.addLast(LocalDateTime.now().format(TIME_FMT) + " " + msg);
        while (orderLog.size() > LOG_LINES) orderLog.pollFirst();
    }

    /**
     * Redraw the entire dashboard. Call every ~1 second or on each tick.
     */
    public void refresh(Map<String, Position> positions) {
        StringBuilder sb = new StringBuilder();
        sb.append(CLEAR);

        int width = 80;
        String now = LocalDateTime.now().format(DT_FMT);

        // ── Header ─────────────────────────────────────────────────────────
        sb.append(BOLD).append(CYAN);
        sb.append("╔" + "═".repeat(width - 2) + "╗\n");
        String title = "  NSE ALGO TRADING DASHBOARD  ";
        String mode  = " [" + tradingMode.toUpperCase() + "] ";
        String time  = " " + now + " ";
        sb.append("║").append(title).append(YELLOW).append(mode)
          .append(CYAN).append(pad(time, width - title.length() - mode.length() - 2)).append("║\n");
        sb.append("╠" + "═".repeat(width - 2) + "╣\n");
        sb.append(RESET);

        // ── Risk Meter ─────────────────────────────────────────────────────
        double dailyPnl      = riskManager.getDailyPnL();
        double maxDailyLoss  = riskManager.getClass().getDeclaredFields().length > 0
                ? -2000 : -2000; // default, replaced below
        // Use riskManager public API
        double drawdownPct   = riskManager.getDrawdownPercent();
        double capital       = riskManager.getCurrentCapital();
        boolean halted       = riskManager.isTradingHalted();

        sb.append("║ ").append(BOLD).append("RISK STATUS: ").append(RESET);
        if (halted) {
            sb.append(BG_RED).append(BOLD).append(" ⛔ TRADING HALTED ").append(RESET);
        } else if (drawdownPct > 3.0) {
            sb.append(YELLOW).append(BOLD).append(" ⚠ HIGH DRAWDOWN ").append(RESET);
        } else {
            sb.append(GREEN).append(BOLD).append(" ✓ ACTIVE ").append(RESET);
        }
        sb.append("  Drawdown: ").append(colorPct(-drawdownPct))
          .append("  Positions: ").append(CYAN)
          .append(riskManager.getOpenPositionCount()).append("/5")
          .append(RESET);
        sb.append(pad("", width - 2)).append("\n");

        // Daily PnL bar
        sb.append("║ ").append(DIM).append("Daily PnL: ").append(RESET);
        sb.append(colorMoney(dailyPnl));
        double pnlBarPct = Math.min(1.0, Math.abs(dailyPnl) / 2000.0);
        sb.append("  ").append(renderBar(pnlBarPct, 20, dailyPnl >= 0));
        sb.append("  Capital: ").append(BOLD).append(CYAN)
          .append(String.format("₹%,.0f", capital)).append(RESET);
        sb.append(pad("", width - 2)).append("\n");

        sb.append(CYAN).append("╠" + "═".repeat(width - 2) + "╣\n").append(RESET);

        // ── Positions Table ────────────────────────────────────────────────
        sb.append("║ ").append(BOLD).append(WHITE)
          .append(padR("SYMBOL", 12)).append(padR("DIR", 6)).append(padR("QTY", 6))
          .append(padR("AVG BUY", 10)).append(padR("LTP", 10))
          .append(padR("UNREAL PnL", 12)).append(padR("CHANGE%", 9)).append("CHART")
          .append(RESET).append(pad("", 5)).append("║\n");
        sb.append(CYAN).append("║ " + "─".repeat(width - 4) + " ║\n").append(RESET);

        if (positions.isEmpty()) {
            sb.append("║  ").append(DIM).append("No open positions")
              .append(RESET).append(pad("", width - 22)).append("║\n");
        } else {
            for (Position p : positions.values()) {
                Tick tick = latestTicks.get(p.getSymbol());
                double ltp = tick != null ? tick.getLastTradedPrice() : p.getLastTradedPrice();
                p.updateUnrealisedPnL(ltp);

                double chgPct = p.getAverageBuyPrice() > 0
                        ? (ltp - p.getAverageBuyPrice()) / p.getAverageBuyPrice() * 100 : 0;
                String dir    = p.isLong() ? GREEN + "LONG " + RESET : RED + "SHORT" + RESET;
                String spark  = sparkline(p.getSymbol());

                sb.append("║  ")
                  .append(BOLD).append(padR(p.getSymbol(), 12)).append(RESET)
                  .append(dir).append("  ")
                  .append(padR(String.valueOf(Math.abs(p.getQuantity())), 6))
                  .append(padR(String.format("%.2f", p.getAverageBuyPrice()), 10))
                  .append(padR(String.format("%.2f", ltp), 10))
                  .append(colorMoney(p.getUnrealisedPnL())).append("  ")
                  .append(colorPct(chgPct)).append("  ")
                  .append(spark)
                  .append(pad("", 3)).append("║\n");
            }
        }

        sb.append(CYAN).append("╠" + "═".repeat(width - 2) + "╣\n").append(RESET);

        // ── Live Market Prices ─────────────────────────────────────────────
        sb.append("║ ").append(BOLD).append(WHITE)
          .append("LIVE PRICES").append(RESET)
          .append(pad("", width - 14)).append("║\n");

        int count = 0;
        sb.append("║  ");
        for (Tick t : latestTicks.values()) {
            if (count > 0 && count % 3 == 0) {
                sb.append(pad("", 3)).append("║\n║  ");
            }
            double chg = t.getChangePercent();
            sb.append(BOLD).append(padR(t.getSymbol(), 12)).append(RESET)
              .append(colorPrice(t.getLastTradedPrice(), chg))
              .append(" ").append(colorPct(chg)).append("    ");
            count++;
        }
        if (count > 0) {
            sb.append(pad("", Math.max(0, width - 4 - (count % 3) * 28))).append("║\n");
        } else {
            sb.append(DIM).append("Waiting for ticks...").append(RESET)
              .append(pad("", width - 24)).append("║\n");
        }

        sb.append(CYAN).append("╠" + "═".repeat(width - 2) + "╣\n").append(RESET);

        // ── Signal Log + Order Log (side by side) ─────────────────────────
        int halfW = (width - 5) / 2;
        sb.append("║ ").append(BOLD).append(YELLOW).append("SIGNALS")
          .append(RESET).append(pad("", halfW - 8))
          .append("║ ").append(BOLD).append(BLUE).append("ORDERS")
          .append(RESET).append(pad("", halfW - 7)).append("║\n");

        List<String> sigLines = new ArrayList<>(signalLog);
        List<String> ordLines = new ArrayList<>(orderLog);
        for (int i = 0; i < LOG_LINES; i++) {
            String sig = i < sigLines.size() ? sigLines.get(i) : "";
            String ord = i < ordLines.size() ? ordLines.get(i) : "";
            sb.append("║ ").append(DIM).append(truncate(sig, halfW - 2)).append(RESET)
              .append(pad("", halfW - stripAnsi(sig).length()))
              .append("║ ").append(DIM).append(truncate(ord, halfW - 2)).append(RESET)
              .append(pad("", halfW - stripAnsi(ord).length())).append("║\n");
        }

        // ── Footer ─────────────────────────────────────────────────────────
        sb.append(CYAN);
        sb.append("╚" + "═".repeat(width - 2) + "╝\n");
        sb.append(RESET).append(DIM)
          .append("  Press Ctrl+C to stop  │  Logs: logs/algo-trading.log  │  Trades: logs/trades.log\n")
          .append(RESET);

        System.out.print(sb);
    }

    // ── Sparkline ──────────────────────────────────────────────────────────

    private String sparkline(String symbol) {
        Deque<Double> hist = priceHistory.get(symbol);
        if (hist == null || hist.size() < 2) return DIM + "────────────────────" + RESET;

        List<Double> prices = new ArrayList<>(hist);
        int take = Math.min(SPARK_WIDTH, prices.size());
        List<Double> slice = prices.subList(prices.size() - take, prices.size());

        double min = slice.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = slice.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        double range = max - min;

        // 8-level braille-style blocks
        char[] levels = {'▁','▂','▃','▄','▅','▆','▇','█'};
        StringBuilder spark = new StringBuilder();

        boolean rising = slice.get(slice.size()-1) >= slice.get(0);
        spark.append(rising ? GREEN : RED);

        for (double p : slice) {
            int idx = range == 0 ? 3 : (int)((p - min) / range * 7);
            spark.append(levels[Math.min(idx, 7)]);
        }
        // Pad to fixed width
        while (spark.length() - GREEN.length() - RESET.length() < SPARK_WIDTH) {
            spark.append('─');
        }
        spark.append(RESET);
        return spark.toString();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String colorMoney(double v) {
        String s = String.format("%+,.0f", v);
        return (v >= 0 ? GREEN : RED) + BOLD + padL(s, 9) + RESET;
    }

    private String colorPct(double v) {
        String s = String.format("%+.2f%%", v);
        return (v >= 0 ? GREEN : RED) + padL(s, 8) + RESET;
    }

    private String colorPrice(double price, double chgPct) {
        return (chgPct >= 0 ? GREEN : RED) + String.format("₹%8.2f", price) + RESET;
    }

    private String renderBar(double fraction, int width, boolean positive) {
        int filled = (int)(fraction * width);
        String color  = positive ? GREEN : RED;
        String bar    = color + "█".repeat(Math.max(0, filled))
                      + DIM + "░".repeat(Math.max(0, width - filled)) + RESET;
        return "[" + bar + "]";
    }

    private String padR(String s, int w) {
        if (s == null) s = "";
        if (s.length() >= w) return s.substring(0, w);
        return s + " ".repeat(w - s.length());
    }

    private String padL(String s, int w) {
        if (s == null) s = "";
        if (s.length() >= w) return s;
        return " ".repeat(w - s.length()) + s;
    }

    private String pad(String s, int extra) {
        if (extra <= 0) return s;
        return s + " ".repeat(extra);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        String stripped = stripAnsi(s);
        if (stripped.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }

    /** Strip ANSI codes for length measurement. */
    private String stripAnsi(String s) {
        if (s == null) return "";
        return s.replaceAll("\u001B\\[[;\\d]*m", "");
    }
}
