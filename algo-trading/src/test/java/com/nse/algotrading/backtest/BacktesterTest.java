package com.nse.algotrading.backtest;

import com.nse.algotrading.config.TradingConfig;
import com.nse.algotrading.model.OHLCV;
import com.nse.algotrading.strategy.EMACrossoverStrategy;
import com.nse.algotrading.strategy.TradingStrategy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BacktesterTest {

    @Test
    void testBacktestReturnsValidResult() {
        TradingConfig config   = TradingConfig.getInstance();
        TradingStrategy strategy = new EMACrossoverStrategy("RELIANCE", config);
        List<OHLCV> data        = generateSyntheticData("RELIANCE", 300);

        Backtester backtester   = new Backtester();
        BacktestResult result   = backtester.run(strategy, data, 100000.0);

        assertNotNull(result);
        assertEquals(100000.0, result.getInitialCapital(), 0.01);
        assertTrue(result.getTotalTrades() >= 0, "Total trades should be non-negative");
        assertTrue(result.getFinalCapital() > 0, "Final capital should be positive");
    }

    @Test
    void testBacktestWinRateRange() {
        TradingConfig config    = TradingConfig.getInstance();
        TradingStrategy strategy = new EMACrossoverStrategy("TCS", config);
        List<OHLCV> data        = generateSyntheticData("TCS", 400);

        Backtester backtester   = new Backtester();
        BacktestResult result   = backtester.run(strategy, data, 50000.0);

        if (result.getTotalTrades() > 0) {
            assertTrue(result.getWinRate() >= 0.0 && result.getWinRate() <= 1.0,
                    "Win rate must be between 0 and 1");
        }
    }

    @Test
    void testBacktestProfitFactorNonNegative() {
        TradingConfig config    = TradingConfig.getInstance();
        TradingStrategy strategy = new EMACrossoverStrategy("INFY", config);
        List<OHLCV> data        = generateSyntheticData("INFY", 300);

        Backtester backtester   = new Backtester();
        BacktestResult result   = backtester.run(strategy, data, 100000.0);

        assertTrue(result.getProfitFactor() >= 0,
                "Profit factor should be non-negative");
    }

    @Test
    void testBacktestWithEmptyDataReturnsEmptyResult() {
        TradingConfig config    = TradingConfig.getInstance();
        TradingStrategy strategy = new EMACrossoverStrategy("SBIN", config);

        Backtester backtester = new Backtester();
        BacktestResult result  = backtester.run(strategy, new ArrayList<>(), 100000.0);

        assertNotNull(result);
        assertEquals(0, result.getTotalTrades());
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private List<OHLCV> generateSyntheticData(String symbol, int bars) {
        List<OHLCV> data = new ArrayList<>();
        double price     = 1500.0;
        Random rng       = new Random(123);
        LocalDateTime ts = LocalDateTime.now().minusMinutes((long) bars * 15);

        for (int i = 0; i < bars; i++) {
            price = price * Math.exp(0.0001 + 0.012 * rng.nextGaussian());
            price = Math.max(price, 1);
            double open  = price;
            double high  = open * (1 + Math.abs(rng.nextGaussian()) * 0.004);
            double low   = open * (1 - Math.abs(rng.nextGaussian()) * 0.004);
            double close = low + rng.nextDouble() * (high - low);
            data.add(new OHLCV(symbol, ts.plusMinutes((long) i * 15),
                    open, high, low, close, 75000L));
        }
        return data;
    }
}
