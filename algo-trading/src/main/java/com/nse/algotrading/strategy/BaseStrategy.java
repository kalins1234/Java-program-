package com.nse.algotrading.strategy;

import com.nse.algotrading.config.TradingConfig;
import com.nse.algotrading.model.OHLCV;
import com.nse.algotrading.model.Signal;
import com.nse.algotrading.model.Tick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class providing common indicator calculations
 * and a rolling price buffer for all strategies.
 */
public abstract class BaseStrategy implements TradingStrategy {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final TradingConfig config;
    protected final String symbol;

    protected final List<OHLCV> bars = new ArrayList<>();   // historical + live bars
    protected Tick latestTick;
    protected int minBarsRequired = 50;                      // warm-up period

    protected BaseStrategy(String symbol, TradingConfig config) {
        this.symbol = symbol;
        this.config = config;
    }

    @Override
    public void initialize(List<OHLCV> historicalData) {
        bars.clear();
        bars.addAll(historicalData);
        log.info("{} initialized with {} historical bars", getName(), bars.size());
    }

    @Override
    public void onBar(OHLCV bar) {
        bars.add(bar);
        // Keep only last 500 bars in memory
        if (bars.size() > 500) {
            bars.remove(0);
        }
    }

    @Override
    public void onTick(Tick tick) {
        this.latestTick = tick;
    }

    @Override
    public boolean isReady() {
        return bars.size() >= minBarsRequired;
    }

    @Override
    public void shutdown() {
        log.info("{} strategy shutdown", getName());
    }

    // ── Technical Indicator Helpers ──────────────────────────────────────────

    /** Simple Moving Average over the last `period` closes. */
    protected double sma(int period) {
        return sma(period, bars.size() - 1);
    }

    protected double sma(int period, int endIndex) {
        if (endIndex < period - 1) return 0;
        double sum = 0;
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            sum += bars.get(i).getClose();
        }
        return sum / period;
    }

    /** Exponential Moving Average (Wilder's method). */
    protected double ema(int period) {
        return ema(period, bars.size() - 1);
    }

    protected double ema(int period, int endIndex) {
        if (endIndex < period - 1) return 0;
        double multiplier = 2.0 / (period + 1);
        double emaVal = sma(period, period - 1);  // seed with first SMA
        for (int i = period; i <= endIndex; i++) {
            emaVal = (bars.get(i).getClose() - emaVal) * multiplier + emaVal;
        }
        return emaVal;
    }

    /** RSI (Relative Strength Index). */
    protected double rsi(int period) {
        int end = bars.size() - 1;
        if (end < period) return 50;

        double gainSum = 0, lossSum = 0;
        for (int i = end - period + 1; i <= end; i++) {
            double change = bars.get(i).getClose() - bars.get(i - 1).getClose();
            if (change > 0) gainSum += change;
            else            lossSum += Math.abs(change);
        }
        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;
        if (avgLoss == 0) return 100;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    /** Average True Range (ATR). */
    protected double atr(int period) {
        int end = bars.size() - 1;
        if (end < 1) return 0;

        double sum = 0;
        int count = Math.min(period, end);
        for (int i = end - count + 1; i <= end; i++) {
            double high  = bars.get(i).getHigh();
            double low   = bars.get(i).getLow();
            double prevC = bars.get(i - 1).getClose();
            double tr = Math.max(high - low,
                         Math.max(Math.abs(high - prevC), Math.abs(low - prevC)));
            sum += tr;
        }
        return sum / count;
    }

    /** VWAP (Volume Weighted Average Price) for the current day. */
    protected double vwap() {
        double totalPV = 0, totalVol = 0;
        for (OHLCV bar : bars) {
            double typicalPrice = (bar.getHigh() + bar.getLow() + bar.getClose()) / 3.0;
            totalPV  += typicalPrice * bar.getVolume();
            totalVol += bar.getVolume();
        }
        return totalVol == 0 ? 0 : totalPV / totalVol;
    }

    /** Highest high in the last `lookback` bars. */
    protected double highestHigh(int lookback) {
        int end = bars.size() - 1;
        int start = Math.max(0, end - lookback + 1);
        double max = Double.MIN_VALUE;
        for (int i = start; i <= end; i++) {
            max = Math.max(max, bars.get(i).getHigh());
        }
        return max;
    }

    /** Lowest low in the last `lookback` bars. */
    protected double lowestLow(int lookback) {
        int end = bars.size() - 1;
        int start = Math.max(0, end - lookback + 1);
        double min = Double.MAX_VALUE;
        for (int i = start; i <= end; i++) {
            min = Math.min(min, bars.get(i).getLow());
        }
        return min;
    }

    /** Compute stop-loss price based on ATR multiplier. */
    protected double atrStopLoss(double entryPrice, boolean isLong, double atrMultiplier) {
        double atrVal = atr(14);
        return isLong
                ? entryPrice - atrVal * atrMultiplier
                : entryPrice + atrVal * atrMultiplier;
    }

    protected Signal buildSignal(Signal.Action action, double price,
                                  double stopLoss, double target) {
        Signal signal = new Signal(action, symbol, price, getName());
        signal.setStopLoss(stopLoss);
        signal.setTarget(target);
        return signal;
    }

    protected Signal holdSignal() {
        return new Signal(Signal.Action.HOLD, symbol,
                latestTick != null ? latestTick.getLastTradedPrice() : 0,
                getName());
    }
}
