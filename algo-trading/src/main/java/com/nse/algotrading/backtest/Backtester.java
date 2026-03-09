package com.nse.algotrading.backtest;

import com.nse.algotrading.model.OHLCV;
import com.nse.algotrading.model.Signal;
import com.nse.algotrading.model.Tick;
import com.nse.algotrading.strategy.TradingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Backtesting Engine
 *
 * Simulates strategy execution on historical OHLCV data.
 * Calculates comprehensive performance metrics:
 *   - Total PnL, Win Rate, Profit Factor
 *   - Sharpe Ratio, Max Drawdown, Calmar Ratio
 *   - Win/Loss streaks
 *
 * Usage:
 *   Backtester bt = new Backtester();
 *   BacktestResult result = bt.run(strategy, historicalData, 100000.0);
 *   result.printReport();
 */
public class Backtester {

    private static final Logger log = LoggerFactory.getLogger(Backtester.class);

    // Transaction cost model (NSE realistic estimates)
    private static final double BROKERAGE_PERCENT  = 0.0003;  // 0.03% per side (Zerodha flat)
    private static final double STT_PERCENT        = 0.001;   // 0.1% on sell (equity intraday)
    private static final double EXCHANGE_CHARGES   = 0.0000335; // NSE exchange transaction charge
    private static final double SEBI_CHARGES       = 0.000001;  // SEBI turnover charge
    private static final double GST_PERCENT        = 0.18;    // 18% GST on brokerage

    public BacktestResult run(TradingStrategy strategy,
                               List<OHLCV> data,
                               double initialCapital) {
        log.info("Starting backtest: {} on {} bars, capital={}",
                strategy.getName(), data.size(), initialCapital);

        strategy.initialize(new ArrayList<>());

        List<BacktestTrade> trades     = new ArrayList<>();
        double capital                 = initialCapital;
        double peakCapital             = initialCapital;
        double maxDrawdown             = 0;
        BacktestTrade openTrade        = null;

        double openStopLoss  = 0;
        double openTarget    = 0;

        List<Double> dailyReturns = new ArrayList<>();
        double dayStartCapital   = capital;

        for (int i = 0; i < data.size(); i++) {
            OHLCV bar = data.get(i);
            strategy.onBar(bar);

            // Simulate tick from OHLCV bar
            Tick tick = barToTick(bar);
            strategy.onTick(tick);

            // ── Check if open trade hits SL or Target ──────────────────────
            if (openTrade != null) {
                boolean hitSL     = false;
                boolean hitTarget = false;
                double exitPrice  = bar.getClose();

                if (openTrade.getDirection() == BacktestTrade.Direction.LONG) {
                    if (bar.getLow() <= openStopLoss) {
                        hitSL = true; exitPrice = openStopLoss;
                    } else if (bar.getHigh() >= openTarget && openTarget > 0) {
                        hitTarget = true; exitPrice = openTarget;
                    }
                } else {  // SHORT
                    if (bar.getHigh() >= openStopLoss && openStopLoss > 0) {
                        hitSL = true; exitPrice = openStopLoss;
                    } else if (bar.getLow() <= openTarget && openTarget > 0) {
                        hitTarget = true; exitPrice = openTarget;
                    }
                }

                if (hitSL || hitTarget) {
                    String reason = hitSL ? "STOP_LOSS" : "TARGET";
                    closeTrade(openTrade, bar.getTimestamp(), exitPrice, reason,
                            trades, capital);
                    capital += openTrade.getPnl() - calculateTxCost(openTrade);
                    openTrade = null;

                    // Track drawdown
                    if (capital > peakCapital) peakCapital = capital;
                    double dd = peakCapital - capital;
                    if (dd > maxDrawdown) maxDrawdown = dd;

                    // Daily returns tracking
                    if (i % 75 == 0) {  // approx 1 day of 5-min bars
                        dailyReturns.add((capital - dayStartCapital) / dayStartCapital);
                        dayStartCapital = capital;
                    }
                }
            }

            // ── Generate signal from strategy ─────────────────────────────
            if (strategy.isReady()) {
                Signal signal = strategy.generateSignal();

                if (openTrade == null && signal != null) {
                    if (signal.getAction() == Signal.Action.BUY) {
                        int qty = calculateQty(capital, bar.getClose(),
                                signal.getStopLoss(), initialCapital);
                        if (qty > 0) {
                            openTrade = new BacktestTrade(bar.getSymbol(),
                                    BacktestTrade.Direction.LONG,
                                    bar.getTimestamp(), bar.getClose(), qty);
                            openStopLoss = signal.getStopLoss();
                            openTarget   = signal.getTarget();
                        }

                    } else if (signal.getAction() == Signal.Action.SELL) {
                        int qty = calculateQty(capital, bar.getClose(),
                                signal.getStopLoss(), initialCapital);
                        if (qty > 0) {
                            openTrade = new BacktestTrade(bar.getSymbol(),
                                    BacktestTrade.Direction.SHORT,
                                    bar.getTimestamp(), bar.getClose(), qty);
                            openStopLoss = signal.getStopLoss();
                            openTarget   = signal.getTarget();
                        }

                    } else if (openTrade != null &&
                            (signal.getAction() == Signal.Action.EXIT_LONG ||
                             signal.getAction() == Signal.Action.EXIT_SHORT)) {
                        closeTrade(openTrade, bar.getTimestamp(), bar.getClose(),
                                "SIGNAL", trades, capital);
                        capital += openTrade.getPnl() - calculateTxCost(openTrade);
                        openTrade = null;
                    }
                }
            }
        }

        // Close any open trade at the end of data
        if (openTrade != null) {
            OHLCV lastBar = data.get(data.size() - 1);
            closeTrade(openTrade, lastBar.getTimestamp(), lastBar.getClose(),
                    "EOD_SQUAREOFF", trades, capital);
            capital += openTrade.getPnl() - calculateTxCost(openTrade);
        }

        return buildResult(strategy.getName(),
                data.isEmpty() ? "UNKNOWN" : data.get(0).getSymbol(),
                trades, initialCapital, capital, maxDrawdown, dailyReturns);
    }

    private void closeTrade(BacktestTrade trade, LocalDateTime exitTime,
                             double exitPrice, String reason,
                             List<BacktestTrade> trades, double capital) {
        trade.close(exitTime, exitPrice, reason);
        trades.add(trade);
        log.debug("Trade closed: {}", trade);
    }

    private int calculateQty(double capital, double price,
                              double stopLoss, double initialCapital) {
        // Risk 1% of initial capital per trade
        double risk = initialCapital * 0.01;
        double slDistance = Math.abs(price - stopLoss);
        if (slDistance <= 0) {
            // Default: use 5% of capital as position size
            return (int)((capital * 0.05) / price);
        }
        int qty = (int)(risk / slDistance);
        // Cap at 10% of capital
        int maxQtyByCapital = (int)((capital * 0.10) / price);
        return Math.min(qty, maxQtyByCapital);
    }

    private double calculateTxCost(BacktestTrade trade) {
        double turnover   = trade.getEntryPrice() * trade.getQuantity()
                          + trade.getExitPrice()  * trade.getQuantity();
        double brokerage  = Math.min(20.0, turnover * BROKERAGE_PERCENT) * 2; // both sides, max ₹20
        double stt        = trade.getExitPrice() * trade.getQuantity() * STT_PERCENT;
        double exchange   = turnover * EXCHANGE_CHARGES;
        double sebi       = turnover * SEBI_CHARGES;
        double gst        = (brokerage + exchange + sebi) * GST_PERCENT;
        return brokerage + stt + exchange + sebi + gst;
    }

    private Tick barToTick(OHLCV bar) {
        Tick tick = new Tick(bar.getSymbol(), bar.getClose(),
                bar.getVolume(), bar.getTimestamp());
        tick.setOpen(bar.getOpen());
        tick.setHigh(bar.getHigh());
        tick.setLow(bar.getLow());
        return tick;
    }

    private BacktestResult buildResult(String strategyName, String symbol,
                                        List<BacktestTrade> trades,
                                        double initialCapital, double finalCapital,
                                        double maxDrawdown, List<Double> dailyReturns) {
        BacktestResult result = new BacktestResult(strategyName, symbol, trades);
        result.setInitialCapital(initialCapital);
        result.setFinalCapital(finalCapital);
        result.setTotalPnL(finalCapital - initialCapital);
        result.setTotalPnLPercent((finalCapital - initialCapital) / initialCapital * 100);
        result.setTotalTrades(trades.size());
        result.setMaxDrawdown(maxDrawdown);
        result.setMaxDrawdownPercent(maxDrawdown / initialCapital * 100);

        if (!trades.isEmpty()) {
            long wins = trades.stream().filter(BacktestTrade::isWinner).count();
            long losses = trades.stream().filter(BacktestTrade::isLoser).count();
            result.setWinningTrades((int) wins);
            result.setLosingTrades((int) losses);
            result.setWinRate((double) wins / trades.size());

            double grossProfit = trades.stream().filter(BacktestTrade::isWinner)
                    .mapToDouble(BacktestTrade::getPnl).sum();
            double grossLoss = Math.abs(trades.stream().filter(BacktestTrade::isLoser)
                    .mapToDouble(BacktestTrade::getPnl).sum());

            result.setAvgWin(wins > 0 ? grossProfit / wins : 0);
            result.setAvgLoss(losses > 0 ? grossLoss / losses : 0);
            result.setProfitFactor(grossLoss > 0 ? grossProfit / grossLoss : Double.MAX_VALUE);

            // Sharpe Ratio (annualized, risk-free rate ~6% for India)
            if (dailyReturns.size() > 1) {
                double avgReturn = dailyReturns.stream()
                        .mapToDouble(Double::doubleValue).average().orElse(0);
                double stdDev = calcStdDev(dailyReturns, avgReturn);
                double riskFree = 0.06 / 252;  // daily risk-free rate
                double sharpe = stdDev > 0
                        ? (avgReturn - riskFree) / stdDev * Math.sqrt(252)
                        : 0;
                result.setSharpeRatio(sharpe);

                // Calmar Ratio = Annualized Return / Max Drawdown %
                double annualReturn = (finalCapital - initialCapital) / initialCapital
                        * (252.0 / Math.max(1, dailyReturns.size()));
                double maxDdPct = result.getMaxDrawdownPercent() / 100;
                result.setCalmarRatio(maxDdPct > 0 ? annualReturn / maxDdPct : 0);
            }

            // Win/Loss streaks
            result.setMaxWinStreak(calcMaxStreak(trades, true));
            result.setMaxLossStreak(calcMaxStreak(trades, false));
        }

        return result;
    }

    private double calcStdDev(List<Double> values, double mean) {
        double variance = values.stream()
                .mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0);
        return Math.sqrt(variance);
    }

    private int calcMaxStreak(List<BacktestTrade> trades, boolean winners) {
        int max = 0, current = 0;
        for (BacktestTrade t : trades) {
            if (winners ? t.isWinner() : t.isLoser()) {
                current++;
                max = Math.max(max, current);
            } else {
                current = 0;
            }
        }
        return max;
    }
}
