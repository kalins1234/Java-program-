package com.nse.algotrading.strategy;

import com.nse.algotrading.config.TradingConfig;
import com.nse.algotrading.model.Signal;

/**
 * RSI Divergence + EMA Filter Strategy
 *
 * Logic:
 *   Bullish Divergence → BUY:
 *     - Price makes a lower low
 *     - RSI makes a higher low (divergence = hidden strength)
 *     - Price is above EMA 50 (trend confirmation)
 *
 *   Bearish Divergence → SELL:
 *     - Price makes a higher high
 *     - RSI makes a lower high (divergence = hidden weakness)
 *     - Price is below EMA 50 (trend confirmation)
 *
 * Best for: Swing trading, 1-hour or daily charts
 */
public class RSIDivergenceStrategy extends BaseStrategy {

    private static final int EMA_TREND_PERIOD  = 50;
    private static final int RSI_PERIOD        = 14;
    private static final int DIVERGENCE_LOOKBACK = 10;

    public RSIDivergenceStrategy(String symbol, TradingConfig config) {
        super(symbol, config);
        this.minBarsRequired = EMA_TREND_PERIOD + DIVERGENCE_LOOKBACK + 5;
    }

    @Override
    public String getName() { return "RSI_DIVERGENCE"; }

    @Override
    public String getDescription() {
        return "RSI Divergence with EMA(50) Trend Filter";
    }

    @Override
    public Signal generateSignal() {
        if (!isReady()) return holdSignal();

        double ltp        = bars.get(bars.size() - 1).getClose();
        double emaTrend   = ema(EMA_TREND_PERIOD);
        double currentRsi = rsi(RSI_PERIOD);

        int end = bars.size() - 1;

        // Find recent swing low (for bullish divergence)
        int prevLowIdx   = findRecentSwingLow(end - 1, DIVERGENCE_LOOKBACK);
        int prevHighIdx  = findRecentSwingHigh(end - 1, DIVERGENCE_LOOKBACK);

        if (prevLowIdx > 0) {
            double prevLowPrice = bars.get(prevLowIdx).getLow();
            double currLowPrice = bars.get(end).getLow();
            double prevRsiLow   = rsiAtBar(prevLowIdx, RSI_PERIOD);

            // Bullish Divergence: price lower low but RSI higher low
            boolean bullishDiv = currLowPrice < prevLowPrice && currentRsi > prevRsiLow;
            boolean aboveEma   = ltp > emaTrend;
            boolean rsiOversold = currentRsi < 45;

            if (bullishDiv && aboveEma && rsiOversold) {
                double sl  = currLowPrice * 0.995;   // just below current low
                double tgt = ltp + (ltp - sl) * 2.0; // 1:2 RR
                Signal signal = buildSignal(Signal.Action.BUY, ltp, sl, tgt);
                signal.setReason(String.format(
                        "Bullish RSI divergence: price %.2f<%.2f but RSI %.1f>%.1f",
                        currLowPrice, prevLowPrice, currentRsi, prevRsiLow));
                log.info("RSI Divergence BUY: {}", signal);
                return signal;
            }
        }

        if (prevHighIdx > 0) {
            double prevHighPrice = bars.get(prevHighIdx).getHigh();
            double currHighPrice = bars.get(end).getHigh();
            double prevRsiHigh   = rsiAtBar(prevHighIdx, RSI_PERIOD);

            // Bearish Divergence: price higher high but RSI lower high
            boolean bearishDiv    = currHighPrice > prevHighPrice && currentRsi < prevRsiHigh;
            boolean belowEma      = ltp < emaTrend;
            boolean rsiOverbought = currentRsi > 55;

            if (bearishDiv && belowEma && rsiOverbought) {
                double sl  = currHighPrice * 1.005;  // just above current high
                double tgt = ltp - (sl - ltp) * 2.0;
                Signal signal = buildSignal(Signal.Action.SELL, ltp, sl, tgt);
                signal.setReason(String.format(
                        "Bearish RSI divergence: price %.2f>%.2f but RSI %.1f<%.1f",
                        currHighPrice, prevHighPrice, currentRsi, prevRsiHigh));
                log.info("RSI Divergence SELL: {}", signal);
                return signal;
            }
        }

        return holdSignal();
    }

    /** Calculate RSI value at a specific bar index. */
    private double rsiAtBar(int endIdx, int period) {
        if (endIdx < period) return 50;
        double gainSum = 0, lossSum = 0;
        for (int i = endIdx - period + 1; i <= endIdx; i++) {
            double change = bars.get(i).getClose() - bars.get(i - 1).getClose();
            if (change > 0) gainSum += change;
            else            lossSum += Math.abs(change);
        }
        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;
        if (avgLoss == 0) return 100;
        return 100 - (100 / (1 + avgGain / avgLoss));
    }

    /** Find most recent swing low within lookback period. */
    private int findRecentSwingLow(int startIdx, int lookback) {
        int minIdx = -1;
        double minPrice = Double.MAX_VALUE;
        for (int i = Math.max(1, startIdx - lookback); i <= startIdx; i++) {
            if (bars.get(i).getLow() < minPrice) {
                minPrice = bars.get(i).getLow();
                minIdx = i;
            }
        }
        return minIdx;
    }

    /** Find most recent swing high within lookback period. */
    private int findRecentSwingHigh(int startIdx, int lookback) {
        int maxIdx = -1;
        double maxPrice = Double.MIN_VALUE;
        for (int i = Math.max(0, startIdx - lookback); i <= startIdx; i++) {
            if (bars.get(i).getHigh() > maxPrice) {
                maxPrice = bars.get(i).getHigh();
                maxIdx = i;
            }
        }
        return maxIdx;
    }
}
