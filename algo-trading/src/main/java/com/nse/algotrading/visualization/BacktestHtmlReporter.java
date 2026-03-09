package com.nse.algotrading.visualization;

import com.nse.algotrading.backtest.BacktestResult;
import com.nse.algotrading.backtest.BacktestTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates a self-contained HTML report with interactive Chart.js charts.
 *
 * Charts produced:
 *   1. Equity Curve           — cumulative capital over trades
 *   2. Drawdown Chart         — underwater equity chart
 *   3. Monthly Returns Heatmap— calendar view of monthly PnL %
 *   4. Trade PnL Scatter      — each trade's PnL vs entry time
 *   5. PnL Distribution       — histogram of trade returns
 *   6. Win/Loss Donut         — win rate visualization
 *   7. Exit Reason Breakdown  — TARGET vs SL vs SIGNAL bar chart
 *
 * No external Java dependencies — uses Chart.js 4.x from CDN.
 * Output: reports/backtest_<strategy>_<symbol>_<timestamp>.html
 */
public class BacktestHtmlReporter {

    private static final Logger log = LoggerFactory.getLogger(BacktestHtmlReporter.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public String generate(BacktestResult result) throws IOException {
        Path dir = Paths.get("reports");
        Files.createDirectories(dir);

        String filename = String.format("reports/backtest_%s_%s_%d.html",
                result.getStrategyName(), result.getSymbol(),
                System.currentTimeMillis());

        String html = buildHtml(result);
        Files.writeString(Paths.get(filename), html);
        log.info("Backtest report saved: {}", filename);
        return filename;
    }

    private String buildHtml(BacktestResult result) {
        List<BacktestTrade> trades = result.getTrades();

        // ── Equity curve data ──────────────────────────────────────────────
        List<String> equityLabels  = new ArrayList<>();
        List<Double> equityValues  = new ArrayList<>();
        List<Double> drawdownValues = new ArrayList<>();
        double capital = result.getInitialCapital();
        double peak    = capital;

        equityLabels.add("\"Start\"");
        equityValues.add(capital);
        drawdownValues.add(0.0);

        for (BacktestTrade t : trades) {
            capital += t.getPnl();
            if (capital > peak) peak = capital;
            double dd = (peak - capital) / peak * 100;
            equityLabels.add("\"" + (t.getExitTime() != null
                    ? t.getExitTime().format(DT_FMT) : "?") + "\"");
            equityValues.add(round(capital));
            drawdownValues.add(round(-dd));
        }

        // ── Trade scatter data ─────────────────────────────────────────────
        List<String> scatterWins  = new ArrayList<>();
        List<String> scatterLoss  = new ArrayList<>();
        for (BacktestTrade t : trades) {
            if (t.getExitTime() == null) continue;
            String point = String.format("{x:\"%s\",y:%.2f}",
                    t.getExitTime().format(DT_FMT), t.getPnl());
            if (t.isWinner()) scatterWins.add(point);
            else              scatterLoss.add(point);
        }

        // ── PnL distribution histogram ─────────────────────────────────────
        int[] bins     = new int[20];
        double minPnl  = trades.stream().mapToDouble(BacktestTrade::getPnl)
                .min().orElse(-1000);
        double maxPnl  = trades.stream().mapToDouble(BacktestTrade::getPnl)
                .max().orElse(1000);
        double binSize = (maxPnl - minPnl) / 20.0;
        List<String> histLabels = new ArrayList<>();
        for (BacktestTrade t : trades) {
            int idx = (int)((t.getPnl() - minPnl) / binSize);
            bins[Math.min(idx, 19)]++;
        }
        for (int i = 0; i < 20; i++) {
            histLabels.add(String.format("\"%.0f\"", minPnl + i * binSize));
        }

        // ── Monthly returns ────────────────────────────────────────────────
        Map<String, Double> monthlyPnl = new TreeMap<>();
        DateTimeFormatter MON_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
        for (BacktestTrade t : trades) {
            if (t.getExitTime() == null) continue;
            String key = t.getExitTime().format(MON_FMT);
            monthlyPnl.merge(key, t.getPnl(), Double::sum);
        }
        List<String> monthLabels = new ArrayList<>(monthlyPnl.keySet())
                .stream().map(k -> "\"" + k + "\"").collect(Collectors.toList());
        List<String> monthColors = monthlyPnl.values().stream()
                .map(v -> v >= 0 ? "'rgba(16,185,129,0.8)'" : "'rgba(239,68,68,0.8)'")
                .collect(Collectors.toList());
        List<String> monthValues = monthlyPnl.values().stream()
                .map(v -> String.format("%.2f", v / result.getInitialCapital() * 100))
                .collect(Collectors.toList());

        // ── Exit reasons ───────────────────────────────────────────────────
        Map<String, Long> exitCounts = trades.stream()
                .filter(t -> t.getExitReason() != null)
                .collect(Collectors.groupingBy(BacktestTrade::getExitReason, Collectors.counting()));

        boolean profitable = result.getTotalPnL() >= 0;
        String pnlColor    = profitable ? "#10B981" : "#EF4444";

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Backtest Report — %s on %s</title>
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
<style>
  *{box-sizing:border-box;margin:0;padding:0}
  body{font-family:'Segoe UI',system-ui,sans-serif;background:#0f172a;color:#e2e8f0;padding:24px}
  h1{font-size:1.6rem;font-weight:700;color:#f1f5f9;margin-bottom:4px}
  .subtitle{color:#94a3b8;font-size:.9rem;margin-bottom:24px}
  .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:16px;margin-bottom:24px}
  .card{background:#1e293b;border:1px solid #334155;border-radius:12px;padding:20px}
  .card h3{font-size:.75rem;font-weight:600;text-transform:uppercase;letter-spacing:.1em;color:#64748b;margin-bottom:8px}
  .stat{font-size:1.8rem;font-weight:700}
  .stat.pos{color:#10B981} .stat.neg{color:#EF4444} .stat.neu{color:#60A5FA}
  .sub{font-size:.8rem;color:#64748b;margin-top:4px}
  .chart-card{background:#1e293b;border:1px solid #334155;border-radius:12px;padding:20px;margin-bottom:20px}
  .chart-card h2{font-size:1rem;font-weight:600;color:#cbd5e1;margin-bottom:16px}
  .two-col{display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-bottom:20px}
  .three-col{display:grid;grid-template-columns:1fr 1fr 1fr;gap:20px;margin-bottom:20px}
  table{width:100%%;border-collapse:collapse;font-size:.82rem}
  th{background:#0f172a;color:#64748b;text-transform:uppercase;font-size:.7rem;letter-spacing:.05em;padding:10px 12px;text-align:left}
  td{padding:9px 12px;border-bottom:1px solid #1e293b;color:#cbd5e1}
  tr:hover td{background:#1e293b}
  .win{color:#10B981} .loss{color:#EF4444}
  .badge{display:inline-block;padding:2px 8px;border-radius:4px;font-size:.7rem;font-weight:600}
  .badge.TARGET{background:#064e3b;color:#10B981}
  .badge.STOP_LOSS{background:#450a0a;color:#EF4444}
  .badge.SIGNAL{background:#1e3a5f;color:#60A5FA}
  .badge.EOD_SQUAREOFF{background:#2d1f00;color:#F59E0B}
  @media(max-width:900px){.two-col,.three-col{grid-template-columns:1fr}}
</style>
</head>
<body>

<h1>Backtest Report: %s on %s</h1>
<div class="subtitle">Strategy: %s &nbsp;|&nbsp; Initial Capital: ₹%,.0f &nbsp;|&nbsp; %d trades analyzed</div>

<!-- KPI Cards -->
<div class="grid">
  <div class="card">
    <h3>Total PnL</h3>
    <div class="stat %s">%s₹%,.0f</div>
    <div class="sub">%+.2f%% return</div>
  </div>
  <div class="card">
    <h3>Win Rate</h3>
    <div class="stat neu">%.1f%%</div>
    <div class="sub">%d wins / %d losses</div>
  </div>
  <div class="card">
    <h3>Profit Factor</h3>
    <div class="stat %s">%.2f</div>
    <div class="sub">Gross profit ÷ Gross loss</div>
  </div>
  <div class="card">
    <h3>Max Drawdown</h3>
    <div class="stat neg">%.2f%%</div>
    <div class="sub">₹%,.0f peak-to-trough</div>
  </div>
  <div class="card">
    <h3>Sharpe Ratio</h3>
    <div class="stat %s">%.3f</div>
    <div class="sub">Annualized (6%% risk-free)</div>
  </div>
  <div class="card">
    <h3>Avg Win / Avg Loss</h3>
    <div class="stat neu">%.0f / %.0f</div>
    <div class="sub">Risk-Reward: %.2f</div>
  </div>
</div>

<!-- Equity Curve + Drawdown -->
<div class="chart-card">
  <h2>Equity Curve &amp; Drawdown</h2>
  <canvas id="equityChart" height="90"></canvas>
</div>

<!-- Monthly Returns + Win-Loss Donut -->
<div class="two-col">
  <div class="chart-card">
    <h2>Monthly Returns (%%)</h2>
    <canvas id="monthlyChart" height="200"></canvas>
  </div>
  <div class="chart-card">
    <h2>Win / Loss Breakdown</h2>
    <canvas id="donutChart" height="200"></canvas>
  </div>
</div>

<!-- Scatter + Histogram -->
<div class="two-col">
  <div class="chart-card">
    <h2>Trade PnL Over Time</h2>
    <canvas id="scatterChart" height="220"></canvas>
  </div>
  <div class="chart-card">
    <h2>PnL Distribution</h2>
    <canvas id="histChart" height="220"></canvas>
  </div>
</div>

<!-- Exit Reasons -->
<div class="two-col">
  <div class="chart-card">
    <h2>Exit Reason Breakdown</h2>
    <canvas id="exitChart" height="220"></canvas>
  </div>
  <div class="chart-card">
    <h2>Streaks</h2>
    <canvas id="streakChart" height="220"></canvas>
  </div>
</div>

<!-- Trade Log Table -->
<div class="chart-card">
  <h2>Trade Log (last 50)</h2>
  <div style="overflow-x:auto">
  <table>
    <thead><tr>
      <th>#</th><th>Direction</th><th>Entry Time</th><th>Exit Time</th>
      <th>Entry ₹</th><th>Exit ₹</th><th>Qty</th><th>PnL ₹</th><th>PnL %%</th><th>Exit</th>
    </tr></thead>
    <tbody>
%s
    </tbody>
  </table>
  </div>
</div>

<div style="text-align:center;color:#334155;font-size:.75rem;margin-top:32px;padding-top:16px;border-top:1px solid #1e293b">
  Generated by NSE Algo Trading App &nbsp;•&nbsp; Not financial advice &nbsp;•&nbsp; Always backtest before live trading
</div>

<script>
const CHART_DEFAULTS = {
  plugins: { legend: { labels: { color: '#94a3b8', font: { size: 11 } } } },
  scales: {
    x: { ticks: { color: '#64748b', maxTicksLimit: 8 }, grid: { color: '#1e293b' } },
    y: { ticks: { color: '#64748b' }, grid: { color: '#1e293b' } }
  }
};

// 1. Equity Curve
new Chart(document.getElementById('equityChart'), {
  type: 'line',
  data: {
    labels: [%s],
    datasets: [
      {
        label: 'Capital (₹)', data: [%s], yAxisID: 'y',
        borderColor: '#60A5FA', backgroundColor: 'rgba(96,165,250,0.08)',
        fill: true, tension: 0.3, pointRadius: 0, borderWidth: 2
      },
      {
        label: 'Drawdown (%%)', data: [%s], yAxisID: 'y2',
        borderColor: '#EF4444', backgroundColor: 'rgba(239,68,68,0.1)',
        fill: true, tension: 0.2, pointRadius: 0, borderWidth: 1.5
      }
    ]
  },
  options: { ...CHART_DEFAULTS,
    interaction: { mode: 'index', intersect: false },
    scales: {
      x: { ticks: { color: '#64748b', maxTicksLimit: 12 }, grid: { color: '#1e293b' } },
      y: { position:'left', ticks: { color: '#64748b',
             callback: v => '₹' + v.toLocaleString('en-IN') },
           grid: { color: '#1e293b' } },
      y2: { position:'right', ticks: { color: '#EF4444',
              callback: v => v.toFixed(1) + '%%' },
            grid: { drawOnChartArea: false } }
    }
  }
});

// 2. Monthly Returns
new Chart(document.getElementById('monthlyChart'), {
  type: 'bar',
  data: {
    labels: [%s],
    datasets: [{ label: 'Monthly Return (%%)', data: [%s],
      backgroundColor: [%s], borderRadius: 4 }]
  },
  options: { ...CHART_DEFAULTS,
    plugins: { legend: { display: false } },
    scales: {
      x: { ticks: { color: '#64748b', maxRotation: 45 }, grid: { color: '#1e293b' } },
      y: { ticks: { color: '#64748b', callback: v => v + '%%' }, grid: { color: '#1e293b' } }
    }
  }
});

// 3. Win/Loss Donut
new Chart(document.getElementById('donutChart'), {
  type: 'doughnut',
  data: {
    labels: ['Wins', 'Losses'],
    datasets: [{ data: [%d, %d],
      backgroundColor: ['rgba(16,185,129,0.8)','rgba(239,68,68,0.8)'],
      borderColor: ['#10B981','#EF4444'], borderWidth: 2 }]
  },
  options: {
    plugins: {
      legend: { position: 'bottom', labels: { color: '#94a3b8' } },
      tooltip: { callbacks: {
        label: ctx => ctx.label + ': ' + ctx.raw + ' (' +
          (ctx.raw / (ctx.dataset.data[0] + ctx.dataset.data[1]) * 100).toFixed(1) + '%%)'
      }}
    },
    cutout: '65%%'
  }
});

// 4. Scatter: Trade PnL
new Chart(document.getElementById('scatterChart'), {
  type: 'scatter',
  data: {
    datasets: [
      { label: 'Winning trades', data: [%s],
        backgroundColor: 'rgba(16,185,129,0.7)', pointRadius: 5 },
      { label: 'Losing trades',  data: [%s],
        backgroundColor: 'rgba(239,68,68,0.7)',  pointRadius: 5 }
    ]
  },
  options: { ...CHART_DEFAULTS,
    scales: {
      x: { type: 'category', ticks: { color: '#64748b', maxTicksLimit: 6 }, grid: { color: '#1e293b' } },
      y: { ticks: { color: '#64748b', callback: v => '₹' + v }, grid: { color: '#1e293b' } }
    }
  }
});

// 5. PnL Histogram
new Chart(document.getElementById('histChart'), {
  type: 'bar',
  data: {
    labels: [%s],
    datasets: [{ label: 'Trades', data: [%s],
      backgroundColor: bins => {
        const v = parseFloat([%s][bins.dataIndex]);
        return v >= 0 ? 'rgba(16,185,129,0.7)' : 'rgba(239,68,68,0.7)';
      },
      borderRadius: 2 }]
  },
  options: { ...CHART_DEFAULTS,
    plugins: { legend: { display: false } },
    scales: {
      x: { ticks: { color: '#64748b', maxTicksLimit: 8 }, grid: { color: '#1e293b' } },
      y: { ticks: { color: '#64748b' }, grid: { color: '#1e293b' } }
    }
  }
});

// 6. Exit Reasons
new Chart(document.getElementById('exitChart'), {
  type: 'bar',
  data: {
    labels: [%s],
    datasets: [{ label: 'Count', data: [%s],
      backgroundColor: ['rgba(16,185,129,0.8)','rgba(239,68,68,0.8)',
                        'rgba(96,165,250,0.8)','rgba(245,158,11,0.8)'],
      borderRadius: 6 }]
  },
  options: { ...CHART_DEFAULTS,
    indexAxis: 'y',
    plugins: { legend: { display: false } },
    scales: {
      x: { ticks: { color: '#64748b' }, grid: { color: '#1e293b' } },
      y: { ticks: { color: '#64748b' }, grid: { color: '#1e293b' } }
    }
  }
});

// 7. Streaks
new Chart(document.getElementById('streakChart'), {
  type: 'bar',
  data: {
    labels: ['Max Win Streak','Max Loss Streak'],
    datasets: [{ data: [%d, %d],
      backgroundColor: ['rgba(16,185,129,0.8)','rgba(239,68,68,0.8)'],
      borderRadius: 6 }]
  },
  options: { ...CHART_DEFAULTS,
    plugins: { legend: { display: false } },
    scales: {
      x: { ticks: { color: '#64748b' }, grid: { color: '#1e293b' } },
      y: { ticks: { color: '#64748b' }, grid: { color: '#1e293b' } }
    }
  }
});
</script>
</body>
</html>
""".formatted(
                // title
                result.getStrategyName(), result.getSymbol(),
                // header
                result.getStrategyName(), result.getSymbol(),
                result.getDescription(), result.getInitialCapital(), result.getTotalTrades(),
                // KPI cards
                result.getTotalPnL() >= 0 ? "pos" : "neg",
                result.getTotalPnL() >= 0 ? "+" : "",
                result.getTotalPnL(),
                result.getTotalPnLPercent(),
                result.getWinRate() * 100,
                result.getWinningTrades(), result.getLosingTrades(),
                result.getProfitFactor() >= 1.5 ? "pos" : result.getProfitFactor() >= 1.0 ? "neu" : "neg",
                result.getProfitFactor(),
                result.getMaxDrawdownPercent(),
                result.getMaxDrawdown(),
                result.getSharpeRatio() >= 1.0 ? "pos" : result.getSharpeRatio() >= 0 ? "neu" : "neg",
                result.getSharpeRatio(),
                result.getAvgWin(), result.getAvgLoss(),
                result.getAvgLoss() > 0 ? result.getAvgWin() / result.getAvgLoss() : 0.0,
                // trade log rows
                buildTradeRows(trades),
                // chart data
                String.join(",", equityLabels),
                equityValues.stream().map(String::valueOf).collect(Collectors.joining(",")),
                drawdownValues.stream().map(String::valueOf).collect(Collectors.joining(",")),
                String.join(",", monthLabels),
                String.join(",", monthValues),
                String.join(",", monthColors),
                result.getWinningTrades(), result.getLosingTrades(),
                String.join(",", scatterWins),
                String.join(",", scatterLoss),
                String.join(",", histLabels),
                Arrays.stream(bins).mapToObj(String::valueOf).collect(Collectors.joining(",")),
                String.join(",", histLabels),
                buildExitLabels(exitCounts),
                buildExitValues(exitCounts),
                result.getMaxWinStreak(), result.getMaxLossStreak()
        );
    }

    private String buildTradeRows(List<BacktestTrade> trades) {
        List<BacktestTrade> last50 = trades.subList(
                Math.max(0, trades.size() - 50), trades.size());
        StringBuilder sb = new StringBuilder();
        int n = trades.size() - last50.size();
        for (BacktestTrade t : last50) {
            n++;
            String cls  = t.isWinner() ? "win" : "loss";
            String sign = t.isWinner() ? "+" : "";
            sb.append(String.format("""
              <tr>
                <td>%d</td>
                <td class="%s">%s</td>
                <td>%s</td>
                <td>%s</td>
                <td>₹%,.2f</td>
                <td>₹%,.2f</td>
                <td>%d</td>
                <td class="%s">%s₹%,.2f</td>
                <td class="%s">%s%.2f%%</td>
                <td><span class="badge %s">%s</span></td>
              </tr>
            """,
                    n, cls, t.getDirection(),
                    t.getEntryTime() != null ? t.getEntryTime().format(DT_FMT) : "-",
                    t.getExitTime()  != null ? t.getExitTime().format(DT_FMT)  : "-",
                    t.getEntryPrice(), t.getExitPrice(), t.getQuantity(),
                    cls, sign, t.getPnl(),
                    cls, sign, t.getPnlPercent(),
                    t.getExitReason(), t.getExitReason()));
        }
        return sb.toString();
    }

    private String buildExitLabels(Map<String, Long> exitCounts) {
        return exitCounts.keySet().stream()
                .map(k -> "\"" + k + "\"").collect(Collectors.joining(","));
    }

    private String buildExitValues(Map<String, Long> exitCounts) {
        return exitCounts.values().stream()
                .map(String::valueOf).collect(Collectors.joining(","));
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
