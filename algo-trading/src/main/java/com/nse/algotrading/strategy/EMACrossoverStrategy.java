package com.nse.algotrading.strategy;

import com.nse.algotrading.config.TradingConfig;
import com.nse.algotrading.model.Signal;

/**
 * EMA Crossover Strategy
 *
 * Logic:
 *   BUY  when fast EMA (9) crosses ABOVE slow EMA (21)
 *   SELL when fast EMA (9) crosses BELOW slow EMA (21)
 *
 * Best for: Trending markets, 15-minute or hourly charts
 * Instruments: Nifty 50, Bank Nifty, large-cap stocks
 */
public class EMACrossoverStrategy extends BaseStrategy {

    private final int fastPeriod;
    private final int slowPeriod;

    // Track previous bar's EMA values for crossover detection
    private double prevFastEma = 0;
    private double prevSlowEma = 0;

    public EMACrossoverStrategy(String symbol, TradingConfig config) {
        super(symbol, config);
        this.fastPeriod   = config.getEmaPeriodFast();   // default 9
        this.slowPeriod   = config.getEmaPeriodSlow();   // default 21
        this.minBarsRequired = slowPeriod + 10;
    }

    @Override
    public String getName() { return "EMA_CROSSOVER"; }

    @Override
    public String getDescription() {
        return String.format("EMA(%d/%d) Crossover - Trend Following", fastPeriod, slowPeriod);
    }

    @Override
    public Signal generateSignal() {
        if (!isReady()) {
            log.debug("{} not ready, need {} bars, have {}", getName(), minBarsRequired, bars.size());
            return holdSignal();
        }

        double currFastEma = ema(fastPeriod);
        double currSlowEma = ema(slowPeriod);
        double ltp = latestTick != null ? latestTick.getLastTradedPrice()
                                        : bars.get(bars.size() - 1).getClose();

        Signal signal;

        // Golden Cross: fast EMA crosses above slow EMA → BUY
        if (prevFastEma <= prevSlowEma && currFastEma > currSlowEma) {
            double sl  = atrStopLoss(ltp, true, 1.5);
            double tgt = ltp + (ltp - sl) * 2.0;   // 1:2 risk-reward
            signal = buildSignal(Signal.Action.BUY, ltp, sl, tgt);
            signal.setReason(String.format(
                    "EMA(%d)=%.2f crossed above EMA(%d)=%.2f",
                    fastPeriod, currFastEma, slowPeriod, currSlowEma));
            log.info("BUY signal: {}", signal);

        // Death Cross: fast EMA crosses below slow EMA → SELL
        } else if (prevFastEma >= prevSlowEma && currFastEma < currSlowEma) {
            double sl  = atrStopLoss(ltp, false, 1.5);
            double tgt = ltp - (sl - ltp) * 2.0;
            signal = buildSignal(Signal.Action.SELL, ltp, sl, tgt);
            signal.setReason(String.format(
                    "EMA(%d)=%.2f crossed below EMA(%d)=%.2f",
                    fastPeriod, currFastEma, slowPeriod, currSlowEma));
            log.info("SELL signal: {}", signal);

        } else {
            signal = holdSignal();
        }

        // Update previous EMA values for next bar
        prevFastEma = currFastEma;
        prevSlowEma = currSlowEma;

        return signal;
    }

    @Override
    public void onBar(com.nse.algotrading.model.OHLCV bar) {
        super.onBar(bar);
        // Update EMA tracking after each new bar
        if (isReady()) {
            prevFastEma = ema(fastPeriod, bars.size() - 2);
            prevSlowEma = ema(slowPeriod, bars.size() - 2);
        }
    }
}
