package com.nse.algotrading.strategy;

import com.nse.algotrading.model.OHLCV;
import com.nse.algotrading.model.Signal;
import com.nse.algotrading.model.Tick;

import java.util.List;

/**
 * Base interface for all trading strategies.
 *
 * Lifecycle:
 *   1. initialize()          - called once at market open
 *   2. onBar(OHLCV)          - called for each completed candle
 *   3. onTick(Tick)          - called for each real-time tick
 *   4. generateSignal()      - returns current signal (BUY/SELL/HOLD)
 *   5. shutdown()            - called at market close / EOD
 */
public interface TradingStrategy {

    String getName();
    String getDescription();

    /** Called once with historical data to warm up indicators. */
    void initialize(List<OHLCV> historicalData);

    /** Called every time a new OHLCV candle completes. */
    void onBar(OHLCV bar);

    /** Called on every real-time tick. */
    void onTick(Tick tick);

    /** Returns the latest signal for the strategy's symbol. */
    Signal generateSignal();

    /** Whether the strategy is ready to generate signals (enough data). */
    boolean isReady();

    /** Clean up resources at end of day. */
    void shutdown();
}
