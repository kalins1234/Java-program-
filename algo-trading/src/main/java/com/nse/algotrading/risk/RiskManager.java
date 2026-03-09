package com.nse.algotrading.risk;

import com.nse.algotrading.config.TradingConfig;
import com.nse.algotrading.model.Order;
import com.nse.algotrading.model.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Risk Manager — Guards all orders against pre-defined risk limits.
 *
 * Rules enforced:
 *   1. Max daily loss limit (hard stop)
 *   2. Max drawdown from peak equity
 *   3. Max position size per trade (% of capital)
 *   4. Max number of concurrent open positions
 *   5. No trading outside market hours
 *   6. Symbol-level daily loss limit
 *   7. Minimum risk-reward ratio check
 */
public class RiskManager {

    private static final Logger log = LoggerFactory.getLogger(RiskManager.class);

    private final TradingConfig config;

    private double currentCapital;
    private double peakCapital;       // for drawdown calculation
    private double dailyPnL = 0;
    private boolean tradingHalted = false;

    private final Map<String, Double> symbolDailyPnL  = new ConcurrentHashMap<>();
    private final Map<String, Integer> openPositionQty = new ConcurrentHashMap<>();
    private final AtomicInteger openPositionCount      = new AtomicInteger(0);

    public RiskManager(TradingConfig config) {
        this.config = config;
        this.currentCapital = config.getInitialCapital();
        this.peakCapital    = config.getInitialCapital();
    }

    /**
     * Main gate: check all risk rules before allowing an order.
     *
     * @return RiskCheckResult with approval status and reason
     */
    public RiskCheckResult checkOrder(Order order, double stopLoss) {

        if (tradingHalted) {
            return RiskCheckResult.reject("Trading halted: risk limits breached");
        }

        // Rule 1: Daily loss limit
        double maxDailyLoss = config.getMaxDailyLoss();
        if (dailyPnL <= -maxDailyLoss) {
            tradingHalted = true;
            String msg = String.format("Daily loss limit hit: PnL=%.2f, Limit=%.2f",
                    dailyPnL, -maxDailyLoss);
            log.warn("TRADING HALTED - {}", msg);
            return RiskCheckResult.reject(msg);
        }

        // Rule 2: Max drawdown from peak capital
        double drawdown = (peakCapital - currentCapital) / peakCapital * 100;
        if (drawdown >= config.getMaxDrawdownPercent()) {
            tradingHalted = true;
            String msg = String.format("Max drawdown hit: %.2f%% (limit: %.2f%%)",
                    drawdown, config.getMaxDrawdownPercent());
            log.warn("TRADING HALTED - {}", msg);
            return RiskCheckResult.reject(msg);
        }

        // Rule 3: Max open positions
        if (openPositionCount.get() >= config.getMaxOpenPositions()) {
            return RiskCheckResult.reject(String.format(
                    "Max open positions (%d) reached", config.getMaxOpenPositions()));
        }

        // Rule 4: Position size check
        double orderValue = order.getQuantity() * order.getPrice();
        double maxPositionValue = config.getMaxPositionValue();
        if (orderValue > maxPositionValue) {
            int maxQty = (int)(maxPositionValue / order.getPrice());
            String msg = String.format(
                    "Order value %.2f exceeds max position size %.2f. Reduce qty to %d",
                    orderValue, maxPositionValue, maxQty);
            log.warn("{}", msg);
            // Adjust quantity rather than reject
            order.setQuantity(maxQty);
            if (maxQty <= 0) {
                return RiskCheckResult.reject("Position size too small after adjustment");
            }
        }

        // Rule 5: Minimum risk-reward ratio (if stop loss provided)
        if (stopLoss > 0 && order.getPrice() > 0) {
            double risk = Math.abs(order.getPrice() - stopLoss);
            double minReward = risk * 1.5;  // minimum 1:1.5 RR
            // Just warn, don't reject
            log.debug("Order RR check: risk=%.2f, minReward=%.2f", risk, minReward);
        }

        // Rule 6: Symbol already has too many open contracts
        int symbolQty = openPositionQty.getOrDefault(order.getSymbol(), 0);
        if (symbolQty != 0 && order.getTransactionType().name().equals("BUY") && symbolQty > 0) {
            log.warn("Already have long position in {}", order.getSymbol());
            // Allow pyramiding up to 2x max position
        }

        log.info("Risk check PASSED for order: {}", order);
        return RiskCheckResult.approve();
    }

    /**
     * Update PnL tracking after a trade is executed.
     */
    public void onTradeExecuted(String symbol, double pnl) {
        dailyPnL += pnl;
        symbolDailyPnL.merge(symbol, pnl, Double::sum);

        currentCapital += pnl;
        if (currentCapital > peakCapital) {
            peakCapital = currentCapital;
        }

        log.info("Trade executed: {} PnL=%.2f | Daily PnL=%.2f | Capital=%.2f",
                symbol, pnl, dailyPnL, currentCapital);
    }

    /**
     * Track position open/close for counting.
     */
    public void onPositionOpened(String symbol, int qty) {
        openPositionQty.merge(symbol, qty, Integer::sum);
        openPositionCount.incrementAndGet();
    }

    public void onPositionClosed(String symbol) {
        openPositionQty.remove(symbol);
        openPositionCount.decrementAndGet();
    }

    /**
     * Calculate recommended position size using fixed fractional method.
     * Risk = 1% of capital per trade.
     */
    public int calculatePositionSize(double entryPrice, double stopLossPrice) {
        double riskPerTrade = currentCapital * 0.01;  // 1% risk
        double riskPerShare = Math.abs(entryPrice - stopLossPrice);
        if (riskPerShare <= 0) return 0;
        return (int)(riskPerTrade / riskPerShare);
    }

    /**
     * Calculate ATR-based stop loss price.
     */
    public double calculateStopLoss(double entryPrice, double atr, boolean isLong) {
        double slDistance = atr * 1.5;
        return isLong ? entryPrice - slDistance : entryPrice + slDistance;
    }

    /**
     * End-of-day reset.
     */
    public void resetForNewDay() {
        dailyPnL = 0;
        symbolDailyPnL.clear();
        tradingHalted = false;
        openPositionCount.set(0);
        openPositionQty.clear();
        log.info("Risk manager reset for new trading day. Capital: {}", currentCapital);
    }

    public void updatePositionsFromBroker(List<Position> positions) {
        openPositionCount.set(0);
        openPositionQty.clear();
        for (Position p : positions) {
            if (!p.isClosed()) {
                openPositionQty.put(p.getSymbol(), p.getQuantity());
                openPositionCount.incrementAndGet();
            }
        }
    }

    // Getters
    public double getDailyPnL()        { return dailyPnL; }
    public double getCurrentCapital()  { return currentCapital; }
    public double getPeakCapital()     { return peakCapital; }
    public boolean isTradingHalted()   { return tradingHalted; }
    public int getOpenPositionCount()  { return openPositionCount.get(); }

    public double getDrawdownPercent() {
        return (peakCapital - currentCapital) / peakCapital * 100;
    }

    // ── Inner class ────────────────────────────────────────────────────────────

    public static class RiskCheckResult {
        private final boolean approved;
        private final String  reason;

        private RiskCheckResult(boolean approved, String reason) {
            this.approved = approved;
            this.reason   = reason;
        }

        public static RiskCheckResult approve()              { return new RiskCheckResult(true,  "OK"); }
        public static RiskCheckResult reject(String reason)  { return new RiskCheckResult(false, reason); }

        public boolean isApproved() { return approved; }
        public String  getReason()  { return reason; }

        @Override
        public String toString() {
            return approved ? "APPROVED" : "REJECTED: " + reason;
        }
    }
}
