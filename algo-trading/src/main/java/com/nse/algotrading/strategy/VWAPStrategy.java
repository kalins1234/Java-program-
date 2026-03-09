package com.nse.algotrading.strategy;

import com.nse.algotrading.config.TradingConfig;
import com.nse.algotrading.model.OHLCV;
import com.nse.algotrading.model.Signal;
import com.nse.algotrading.model.Tick;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * VWAP Mean Reversion Strategy
 *
 * VWAP (Volume Weighted Average Price) is the benchmark price
 * institutions use for intraday trading. Price tends to revert to VWAP.
 *
 * Logic:
 *   BUY  when:
 *     - Price is below VWAP - 1 std deviation (oversold)
 *     - RSI < 40 (confirming oversold)
 *     - Price starts moving back toward VWAP
 *
 *   SELL when:
 *     - Price is above VWAP + 1 std deviation (overbought)
 *     - RSI > 60 (confirming overbought)
 *     - Price starts moving back toward VWAP
 *
 *   Exit: When price reaches VWAP (take profit)
 *
 * Best for: Large-cap stocks, indices on 5-min charts
 * Note: VWAP resets every trading day at 09:15
 */
public class VWAPStrategy extends BaseStrategy {

    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime AVOID_AFTER  = LocalTime.of(14, 45); // avoid late entries

    private final List<OHLCV> todayBars = new ArrayList<>();
    private double currentVwap = 0;
    private double vwapUpperBand = 0;
    private double vwapLowerBand = 0;

    // Standard deviation multiplier for bands (1.0 = 1 sigma)
    private static final double BAND_MULTIPLIER = 1.0;

    private boolean hasLongPosition  = false;
    private boolean hasShortPosition = false;

    public VWAPStrategy(String symbol, TradingConfig config) {
        super(symbol, config);
        this.minBarsRequired = 10;
    }

    @Override
    public String getName() { return "VWAP_MEAN_REVERSION"; }

    @Override
    public String getDescription() {
        return "VWAP Mean Reversion with Standard Deviation Bands";
    }

    @Override
    public void onBar(OHLCV bar) {
        super.onBar(bar);

        // Only use today's bars for VWAP
        LocalTime barTime = bar.getTimestamp().toLocalTime();
        if (!barTime.isBefore(MARKET_OPEN)) {
            todayBars.add(bar);
            recalculateVwapBands();
        }
    }

    @Override
    public void onTick(Tick tick) {
        super.onTick(tick);
    }

    private void recalculateVwapBands() {
        if (todayBars.isEmpty()) return;

        // Calculate VWAP
        double totalPV = 0, totalVol = 0;
        for (OHLCV bar : todayBars) {
            double tp = (bar.getHigh() + bar.getLow() + bar.getClose()) / 3.0;
            totalPV  += tp * bar.getVolume();
            totalVol += bar.getVolume();
        }
        currentVwap = totalVol == 0 ? 0 : totalPV / totalVol;

        // Calculate standard deviation of typical prices around VWAP
        double variance = 0;
        for (OHLCV bar : todayBars) {
            double tp   = (bar.getHigh() + bar.getLow() + bar.getClose()) / 3.0;
            double diff = tp - currentVwap;
            variance += diff * diff;
        }
        double stdDev    = Math.sqrt(variance / todayBars.size());
        vwapUpperBand    = currentVwap + BAND_MULTIPLIER * stdDev;
        vwapLowerBand    = currentVwap - BAND_MULTIPLIER * stdDev;
    }

    @Override
    public Signal generateSignal() {
        if (!isReady() || latestTick == null || currentVwap == 0) {
            return holdSignal();
        }

        LocalTime now = latestTick.getTimestamp().toLocalTime();
        if (now.isBefore(MARKET_OPEN) || now.isAfter(AVOID_AFTER)) {
            return holdSignal();
        }

        double ltp   = latestTick.getLastTradedPrice();
        double rsi14 = rsi(14);

        // ── Exit positions when price returns to VWAP ─────────────────────
        if (hasLongPosition && ltp >= currentVwap) {
            hasLongPosition = false;
            Signal exit = buildSignal(Signal.Action.EXIT_LONG, ltp,
                    vwapLowerBand, currentVwap);
            exit.setReason(String.format("Exit long: price %.2f reached VWAP %.2f", ltp, currentVwap));
            log.info("EXIT LONG: {}", exit);
            return exit;
        }

        if (hasShortPosition && ltp <= currentVwap) {
            hasShortPosition = false;
            Signal exit = buildSignal(Signal.Action.EXIT_SHORT, ltp,
                    vwapUpperBand, currentVwap);
            exit.setReason(String.format("Exit short: price %.2f reached VWAP %.2f", ltp, currentVwap));
            log.info("EXIT SHORT: {}", exit);
            return exit;
        }

        // ── New entry signals ──────────────────────────────────────────────
        if (!hasLongPosition && !hasShortPosition) {

            // BUY: price below lower band + RSI oversold
            if (ltp < vwapLowerBand && rsi14 < 40) {
                hasLongPosition = true;
                double sl  = ltp - atr(14) * 1.5;
                double tgt = currentVwap;                // target = VWAP
                Signal signal = buildSignal(Signal.Action.BUY, ltp, sl, tgt);
                signal.setReason(String.format(
                        "Below VWAP lower band (%.2f), RSI=%.1f, VWAP=%.2f",
                        vwapLowerBand, rsi14, currentVwap));
                signal.setConfidence(calculateConfidence(ltp, rsi14, true));
                log.info("VWAP BUY: {}", signal);
                return signal;
            }

            // SELL: price above upper band + RSI overbought
            if (ltp > vwapUpperBand && rsi14 > 60) {
                hasShortPosition = true;
                double sl  = ltp + atr(14) * 1.5;
                double tgt = currentVwap;               // target = VWAP
                Signal signal = buildSignal(Signal.Action.SELL, ltp, sl, tgt);
                signal.setReason(String.format(
                        "Above VWAP upper band (%.2f), RSI=%.1f, VWAP=%.2f",
                        vwapUpperBand, rsi14, currentVwap));
                signal.setConfidence(calculateConfidence(ltp, rsi14, false));
                log.info("VWAP SELL: {}", signal);
                return signal;
            }
        }

        return holdSignal();
    }

    private double calculateConfidence(double ltp, double rsi, boolean isLong) {
        // Higher deviation from VWAP + more extreme RSI = higher confidence
        double vwapDeviation = Math.abs(ltp - currentVwap) / currentVwap;
        double rsiExtreme    = isLong ? (40 - rsi) / 40.0 : (rsi - 60) / 40.0;
        return Math.min(1.0, (vwapDeviation * 10) * 0.5 + rsiExtreme * 0.5);
    }

    /** Must be called at market open to reset daily VWAP. */
    public void resetForNewDay() {
        todayBars.clear();
        currentVwap   = 0;
        vwapUpperBand = 0;
        vwapLowerBand = 0;
        hasLongPosition  = false;
        hasShortPosition = false;
        log.info("{} VWAP reset for new trading day", symbol);
    }

    public double getVwap()           { return currentVwap; }
    public double getVwapUpperBand()  { return vwapUpperBand; }
    public double getVwapLowerBand()  { return vwapLowerBand; }
}
