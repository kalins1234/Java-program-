package com.nse.algotrading.risk;

import com.nse.algotrading.config.TradingConfig;
import com.nse.algotrading.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiskManagerTest {

    private RiskManager riskManager;
    private TradingConfig config;

    @BeforeEach
    void setUp() {
        config      = TradingConfig.getInstance();
        riskManager = new RiskManager(config);
    }

    @Test
    void testOrderApprovedWithinLimits() {
        Order order = new Order("RELIANCE", Order.TransactionType.BUY,
                Order.OrderType.MARKET, Order.ProductType.MIS, 5, 1000.0);

        RiskManager.RiskCheckResult result = riskManager.checkOrder(order, 990.0);
        assertTrue(result.isApproved(), "Small order should be approved");
    }

    @Test
    void testOrderRejectedWhenDailyLossHit() {
        // Simulate hitting daily loss limit
        double maxLoss = config.getMaxDailyLoss();
        riskManager.onTradeExecuted("RELIANCE", -(maxLoss + 100));

        Order order = new Order("TCS", Order.TransactionType.BUY,
                Order.OrderType.MARKET, Order.ProductType.MIS, 1, 3000.0);

        RiskManager.RiskCheckResult result = riskManager.checkOrder(order, 2980.0);
        assertFalse(result.isApproved(), "Order should be rejected after daily loss limit");
        assertTrue(riskManager.isTradingHalted(), "Trading should be halted");
    }

    @Test
    void testOrderRejectedWhenMaxPositionsReached() {
        // Fill up max positions
        for (int i = 0; i < config.getMaxOpenPositions(); i++) {
            riskManager.onPositionOpened("SYM" + i, 10);
        }

        Order order = new Order("RELIANCE", Order.TransactionType.BUY,
                Order.OrderType.MARKET, Order.ProductType.MIS, 5, 1000.0);

        RiskManager.RiskCheckResult result = riskManager.checkOrder(order, 990.0);
        assertFalse(result.isApproved(), "Should reject when max positions reached");
    }

    @Test
    void testPositionSizeCalculation() {
        // Entry at 1000, SL at 990 → risk per share = 10
        // 1% of 100000 = 1000 → qty = 1000/10 = 100
        int qty = riskManager.calculatePositionSize(1000.0, 990.0);
        assertEquals(100, qty, "Position size should be 100 shares for 1% risk");
    }

    @Test
    void testStopLossCalculationLong() {
        double sl = riskManager.calculateStopLoss(1000.0, 15.0, true);
        assertEquals(1000 - 15 * 1.5, sl, 0.01, "Long SL should be entry - 1.5*ATR");
    }

    @Test
    void testStopLossCalculationShort() {
        double sl = riskManager.calculateStopLoss(1000.0, 15.0, false);
        assertEquals(1000 + 15 * 1.5, sl, 0.01, "Short SL should be entry + 1.5*ATR");
    }

    @Test
    void testResetForNewDay() {
        riskManager.onTradeExecuted("RELIANCE", -500.0);
        riskManager.onPositionOpened("TCS", 10);
        riskManager.resetForNewDay();

        assertEquals(0.0, riskManager.getDailyPnL(), "Daily PnL should reset to 0");
        assertEquals(0, riskManager.getOpenPositionCount(), "Open positions should reset");
        assertFalse(riskManager.isTradingHalted(), "Trading halt should reset");
    }

    @Test
    void testDrawdownCalculation() {
        // Simulate 3% drawdown
        riskManager.onTradeExecuted("RELIANCE", -3000.0);
        double drawdown = riskManager.getDrawdownPercent();
        assertTrue(drawdown > 0, "Drawdown should be positive after loss");
        assertTrue(drawdown < 5, "Drawdown should be less than 5% for 3000 loss on 100000 capital");
    }
}
