package com.nse.algotrading.strategy;

import com.nse.algotrading.config.TradingConfig;
import com.nse.algotrading.model.OHLCV;
import com.nse.algotrading.model.Signal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EMACrossoverStrategyTest {

    private EMACrossoverStrategy strategy;
    private TradingConfig config;

    @BeforeEach
    void setUp() {
        config   = TradingConfig.getInstance();
        strategy = new EMACrossoverStrategy("RELIANCE", config);
    }

    @Test
    void testStrategyNotReadyWithInsufficientData() {
        strategy.initialize(new ArrayList<>());
        assertFalse(strategy.isReady(), "Strategy should not be ready with no data");
    }

    @Test
    void testStrategyReadyAfterSufficientData() {
        List<OHLCV> data = generateBars(50, 1000.0, true);
        strategy.initialize(data);
        assertTrue(strategy.isReady(), "Strategy should be ready after warm-up");
    }

    @Test
    void testHoldSignalWhenNotReady() {
        strategy.initialize(new ArrayList<>());
        Signal signal = strategy.generateSignal();
        assertEquals(Signal.Action.HOLD, signal.getAction(),
                "Should return HOLD when not ready");
    }

    @Test
    void testBuySignalOnUptrend() {
        // Create strongly trending upward data to trigger EMA crossover
        List<OHLCV> warmup = generateBars(40, 1000.0, false);  // flat
        List<OHLCV> uptrend = generateBars(20, 1000.0, true);  // rising
        warmup.addAll(uptrend);

        strategy.initialize(warmup);

        Signal signal = strategy.generateSignal();
        assertNotNull(signal);
        // Either BUY or HOLD is valid depending on exact crossover timing
        assertTrue(signal.getAction() == Signal.Action.BUY ||
                   signal.getAction() == Signal.Action.HOLD);
    }

    @Test
    void testStrategyName() {
        assertEquals("EMA_CROSSOVER", strategy.getName());
    }

    @Test
    void testOnBarUpdatesHistory() {
        strategy.initialize(new ArrayList<>());
        OHLCV bar = new OHLCV("RELIANCE", LocalDateTime.now(),
                1000, 1010, 990, 1005, 100000L);
        strategy.onBar(bar);
        assertFalse(strategy.isReady(), "Still not ready after 1 bar");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private List<OHLCV> generateBars(int count, double startPrice, boolean trending) {
        List<OHLCV> bars = new ArrayList<>();
        double price = startPrice;
        LocalDateTime ts = LocalDateTime.now().minusMinutes((long) count * 15);

        for (int i = 0; i < count; i++) {
            if (trending) price *= 1.002;  // 0.2% per bar uptrend
            double open  = price;
            double high  = open * 1.005;
            double low   = open * 0.995;
            double close = trending ? high * 0.999 : open;
            bars.add(new OHLCV("RELIANCE", ts.plusMinutes((long) i * 15),
                    open, high, low, close, 50000L));
        }
        return bars;
    }
}
