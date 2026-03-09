package com.nse.algotrading.strategy;

import com.nse.algotrading.config.TradingConfig;
import com.nse.algotrading.model.OHLCV;
import com.nse.algotrading.model.Signal;
import com.nse.algotrading.model.Tick;

import java.time.LocalTime;

/**
 * Opening Range Breakout (ORB) Strategy
 *
 * One of the most popular intraday strategies for NSE.
 *
 * Logic:
 *   1. Record the High and Low of the first N minutes (default: 15 min)
 *      → This defines the "Opening Range"
 *   2. BUY  when price breaks ABOVE the opening range high (with volume confirmation)
 *   3. SELL when price breaks BELOW the opening range low
 *   4. Stop Loss = opposite side of opening range
 *   5. Target   = 2x the opening range size
 *
 * Best for: Volatile indices (Nifty, Bank Nifty), 1-min charts
 * Trade timing: 09:15 - 15:00 IST (avoid last 30 min)
 */
public class OpeningRangeBreakoutStrategy extends BaseStrategy {

    private static final LocalTime MARKET_OPEN  = LocalTime.of(9, 15);
    private static final LocalTime TRADE_CUTOFF = LocalTime.of(15, 0);

    private final int orbMinutes;

    private double orbHigh = 0;           // Opening range high
    private double orbLow  = Double.MAX_VALUE; // Opening range low
    private boolean orbEstablished = false;
    private boolean buySignalFired  = false;
    private boolean sellSignalFired = false;
    private long orbVolumeAvg = 0;

    public OpeningRangeBreakoutStrategy(String symbol, TradingConfig config) {
        super(symbol, config);
        this.orbMinutes = config.getOrbMinutes();   // default 15 minutes
        this.minBarsRequired = orbMinutes + 1;
    }

    @Override
    public String getName() { return "ORB"; }

    @Override
    public String getDescription() {
        return String.format("Opening Range Breakout (%d min)", orbMinutes);
    }

    @Override
    public void onBar(OHLCV bar) {
        super.onBar(bar);

        LocalTime barTime = bar.getTimestamp().toLocalTime();

        // Build opening range from first N minutes after 09:15
        if (!orbEstablished) {
            LocalTime orbEndTime = MARKET_OPEN.plusMinutes(orbMinutes);

            if (!barTime.isBefore(MARKET_OPEN) && barTime.isBefore(orbEndTime)) {
                // Expand the opening range
                orbHigh = Math.max(orbHigh, bar.getHigh());
                orbLow  = Math.min(orbLow,  bar.getLow());
                orbVolumeAvg += bar.getVolume();
            } else if (!barTime.isBefore(orbEndTime)) {
                // ORB window closed — finalize range
                if (orbHigh > 0 && orbLow < Double.MAX_VALUE) {
                    orbVolumeAvg = orbVolumeAvg / orbMinutes;
                    orbEstablished = true;
                    log.info("{} ORB established: High={} Low={} Range={} VolumeAvg={}",
                            symbol, orbHigh, orbLow, orbHigh - orbLow, orbVolumeAvg);
                }
            }
        }
    }

    @Override
    public Signal generateSignal() {
        if (!orbEstablished || latestTick == null) {
            return holdSignal();
        }

        LocalTime now = latestTick.getTimestamp().toLocalTime();

        // Only trade within allowed hours
        if (now.isBefore(MARKET_OPEN.plusMinutes(orbMinutes)) || now.isAfter(TRADE_CUTOFF)) {
            return holdSignal();
        }

        double ltp    = latestTick.getLastTradedPrice();
        double range  = orbHigh - orbLow;
        double volume = latestTick.getVolume();

        // Volume confirmation: current bar volume > 1.5x ORB average
        boolean volumeConfirmed = volume > orbVolumeAvg * 1.5;

        // BUY breakout: price closes above ORB high
        if (!buySignalFired && ltp > orbHigh && volumeConfirmed) {
            buySignalFired = true;
            double sl  = orbLow;                // SL at ORB low
            double tgt = orbHigh + range * 2;  // Target = 2x range above ORB high
            Signal signal = buildSignal(Signal.Action.BUY, ltp, sl, tgt);
            signal.setReason(String.format(
                    "ORB breakout above %.2f (range=%.2f, vol=%.0f > avg=%.0f)",
                    orbHigh, range, volume, (double) orbVolumeAvg));
            log.info("ORB BUY: {}", signal);
            return signal;
        }

        // SELL breakdown: price closes below ORB low
        if (!sellSignalFired && ltp < orbLow && volumeConfirmed) {
            sellSignalFired = true;
            double sl  = orbHigh;               // SL at ORB high
            double tgt = orbLow - range * 2;   // Target = 2x range below ORB low
            Signal signal = buildSignal(Signal.Action.SELL, ltp, sl, tgt);
            signal.setReason(String.format(
                    "ORB breakdown below %.2f (range=%.2f, vol=%.0f > avg=%.0f)",
                    orbLow, range, volume, (double) orbVolumeAvg));
            log.info("ORB SELL: {}", signal);
            return signal;
        }

        return holdSignal();
    }

    @Override
    public void onTick(Tick tick) {
        super.onTick(tick);
    }

    /** Reset ORB state at the start of each new trading day. */
    public void resetForNewDay() {
        orbHigh          = 0;
        orbLow           = Double.MAX_VALUE;
        orbEstablished   = false;
        buySignalFired   = false;
        sellSignalFired  = false;
        orbVolumeAvg     = 0;
        bars.clear();
        log.info("{} ORB reset for new trading day", symbol);
    }

    public double getOrbHigh() { return orbHigh; }
    public double getOrbLow()  { return orbLow; }
    public boolean isOrbEstablished() { return orbEstablished; }
}
