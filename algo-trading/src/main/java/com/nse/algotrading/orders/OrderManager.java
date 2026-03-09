package com.nse.algotrading.orders;

import com.nse.algotrading.config.TradingConfig;
import com.nse.algotrading.data.BrokerClient;
import com.nse.algotrading.model.Order;
import com.nse.algotrading.model.Position;
import com.nse.algotrading.model.Signal;
import com.nse.algotrading.risk.RiskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Order Management System (OMS)
 *
 * Responsibilities:
 *   - Convert strategy signals into orders
 *   - Apply risk checks before order placement
 *   - Track order lifecycle (placed → filled → cancelled)
 *   - Manage stop-loss and target orders
 *   - Support paper trading mode (no real orders)
 *   - Provide order history and position tracking
 */
public class OrderManager {

    private static final Logger log = LoggerFactory.getLogger(OrderManager.class);

    private final TradingConfig config;
    private final BrokerClient  brokerClient;
    private final RiskManager   riskManager;

    // Active orders and positions
    private final Map<String, Order>    activeOrders   = new ConcurrentHashMap<>();
    private final Map<String, Order>    orderHistory   = new ConcurrentHashMap<>();
    private final Map<String, Position> openPositions  = new ConcurrentHashMap<>();
    private final List<Order>           slOrders       = new ArrayList<>();  // stop-loss orders

    public OrderManager(TradingConfig config,
                        BrokerClient brokerClient,
                        RiskManager riskManager) {
        this.config       = config;
        this.brokerClient = brokerClient;
        this.riskManager  = riskManager;
    }

    /**
     * Process a trading signal — the main entry point for strategy signals.
     * Handles BUY, SELL, EXIT_LONG, EXIT_SHORT actions.
     */
    public Order processSignal(Signal signal) {
        log.info("Processing signal: {}", signal);

        switch (signal.getAction()) {
            case BUY:        return executeBuy(signal);
            case SELL:       return executeSell(signal);
            case EXIT_LONG:  return exitLong(signal.getSymbol(), signal.getPrice());
            case EXIT_SHORT: return exitShort(signal.getSymbol(), signal.getPrice());
            case HOLD:
            default:         return null;
        }
    }

    /**
     * Place a BUY market order with automatic stop-loss bracket.
     */
    public Order executeBuy(Signal signal) {
        String symbol = signal.getSymbol();
        double ltp    = signal.getPrice();

        // Calculate quantity from risk manager
        int qty = signal.getSuggestedQuantity() > 0
                ? signal.getSuggestedQuantity()
                : riskManager.calculatePositionSize(ltp, signal.getStopLoss());

        if (qty <= 0) {
            log.warn("Skipping BUY order: quantity=0 for {}", symbol);
            return null;
        }

        Order order = new Order(symbol,
                Order.TransactionType.BUY,
                Order.OrderType.MARKET,
                Order.ProductType.MIS,  // intraday
                qty, 0);
        order.setStrategyName(signal.getStrategyName());

        // Risk check
        RiskManager.RiskCheckResult check = riskManager.checkOrder(order, signal.getStopLoss());
        if (!check.isApproved()) {
            log.warn("BUY order rejected by risk manager: {}", check.getReason());
            return null;
        }

        String orderId = placeOrder(order);
        if (orderId == null) return null;

        order.setOrderId(orderId);
        order.setStatus(Order.Status.OPEN);
        activeOrders.put(orderId, order);
        orderHistory.put(orderId, order);

        // Track position
        Position position = new Position(symbol, qty, ltp,
                Order.ProductType.MIS, signal.getStrategyName());
        openPositions.put(symbol, position);
        riskManager.onPositionOpened(symbol, qty);

        // Place stop-loss order
        if (signal.getStopLoss() > 0) {
            placeStopLossOrder(symbol, qty, signal.getStopLoss(),
                    Order.TransactionType.SELL, signal.getStrategyName());
        }

        log.info("BUY order placed: {} qty={} @ market, SL={:.2f}",
                symbol, qty, signal.getStopLoss());
        return order;
    }

    /**
     * Place a SELL market order (short) with stop-loss.
     */
    public Order executeSell(Signal signal) {
        String symbol = signal.getSymbol();
        double ltp    = signal.getPrice();

        int qty = signal.getSuggestedQuantity() > 0
                ? signal.getSuggestedQuantity()
                : riskManager.calculatePositionSize(ltp, signal.getStopLoss());

        if (qty <= 0) {
            log.warn("Skipping SELL order: quantity=0 for {}", symbol);
            return null;
        }

        Order order = new Order(symbol,
                Order.TransactionType.SELL,
                Order.OrderType.MARKET,
                Order.ProductType.MIS,
                qty, 0);
        order.setStrategyName(signal.getStrategyName());

        RiskManager.RiskCheckResult check = riskManager.checkOrder(order, signal.getStopLoss());
        if (!check.isApproved()) {
            log.warn("SELL order rejected by risk manager: {}", check.getReason());
            return null;
        }

        String orderId = placeOrder(order);
        if (orderId == null) return null;

        order.setOrderId(orderId);
        order.setStatus(Order.Status.OPEN);
        activeOrders.put(orderId, order);
        orderHistory.put(orderId, order);

        Position position = new Position(symbol, -qty, ltp,
                Order.ProductType.MIS, signal.getStrategyName());
        position.setAverageSellPrice(ltp);
        openPositions.put(symbol, position);
        riskManager.onPositionOpened(symbol, -qty);

        if (signal.getStopLoss() > 0) {
            placeStopLossOrder(symbol, qty, signal.getStopLoss(),
                    Order.TransactionType.BUY, signal.getStrategyName());
        }

        log.info("SELL order placed: {} qty={} @ market, SL={:.2f}",
                symbol, qty, signal.getStopLoss());
        return order;
    }

    /**
     * Exit a long position at market price.
     */
    public Order exitLong(String symbol, double ltp) {
        Position position = openPositions.get(symbol);
        if (position == null || !position.isLong()) {
            log.warn("No long position found for {}", symbol);
            return null;
        }

        Order exitOrder = new Order(symbol,
                Order.TransactionType.SELL,
                Order.OrderType.MARKET,
                Order.ProductType.MIS,
                position.getQuantity(), 0);
        exitOrder.setStrategyName(position.getStrategyName());

        String orderId = placeOrder(exitOrder);
        if (orderId != null) {
            double pnl = (ltp - position.getAverageBuyPrice()) * position.getQuantity();
            riskManager.onTradeExecuted(symbol, pnl);
            riskManager.onPositionClosed(symbol);
            openPositions.remove(symbol);
            cancelAllSLOrdersForSymbol(symbol);
            log.info("EXIT LONG {}: PnL=%.2f", symbol, pnl);
        }
        return exitOrder;
    }

    /**
     * Exit a short position at market price.
     */
    public Order exitShort(String symbol, double ltp) {
        Position position = openPositions.get(symbol);
        if (position == null || !position.isShort()) {
            log.warn("No short position found for {}", symbol);
            return null;
        }

        Order exitOrder = new Order(symbol,
                Order.TransactionType.BUY,
                Order.OrderType.MARKET,
                Order.ProductType.MIS,
                Math.abs(position.getQuantity()), 0);
        exitOrder.setStrategyName(position.getStrategyName());

        String orderId = placeOrder(exitOrder);
        if (orderId != null) {
            double pnl = (position.getAverageSellPrice() - ltp) * Math.abs(position.getQuantity());
            riskManager.onTradeExecuted(symbol, pnl);
            riskManager.onPositionClosed(symbol);
            openPositions.remove(symbol);
            cancelAllSLOrdersForSymbol(symbol);
            log.info("EXIT SHORT {}: PnL=%.2f", symbol, pnl);
        }
        return exitOrder;
    }

    /**
     * Square off ALL open positions (called at market close / risk limits hit).
     */
    public void squareOffAll() {
        log.warn("SQUARING OFF ALL POSITIONS ({})", openPositions.size());
        for (Map.Entry<String, Position> entry : openPositions.entrySet()) {
            String   sym = entry.getKey();
            Position pos = entry.getValue();
            double   ltp = pos.getLastTradedPrice();

            if (pos.isLong())  exitLong(sym, ltp);
            if (pos.isShort()) exitShort(sym, ltp);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private String placeOrder(Order order) {
        if (config.isPaperTrading()) {
            // Paper trading: simulate order
            String fakeId = "PAPER-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            order.setStatus(Order.Status.COMPLETE);
            order.setFilledQuantity(order.getQuantity());
            order.setAveragePrice(order.getPrice());
            log.info("[PAPER TRADE] {} orderId={}", order, fakeId);
            return fakeId;
        }

        // Live trading
        try {
            return brokerClient.placeOrder(order);
        } catch (Exception e) {
            log.error("Failed to place order {}: {}", order, e.getMessage());
            return null;
        }
    }

    private void placeStopLossOrder(String symbol, int qty, double slPrice,
                                     Order.TransactionType txType, String strategyName) {
        Order slOrder = new Order(symbol, txType,
                Order.OrderType.SL_M,
                Order.ProductType.MIS, qty, slPrice);
        slOrder.setTriggerPrice(slPrice);
        slOrder.setStrategyName(strategyName + "_SL");

        String slId = placeOrder(slOrder);
        if (slId != null) {
            slOrder.setOrderId(slId);
            slOrders.add(slOrder);
            log.info("Stop-loss order placed for {} @ {:.2f}", symbol, slPrice);
        }
    }

    private void cancelAllSLOrdersForSymbol(String symbol) {
        slOrders.removeIf(sl -> {
            if (sl.getSymbol().equals(symbol) && sl.isPending()) {
                try {
                    if (!config.isPaperTrading()) {
                        brokerClient.cancelOrder(sl.getOrderId());
                    }
                    log.info("Cancelled SL order {} for {}", sl.getOrderId(), symbol);
                    return true;
                } catch (Exception e) {
                    log.error("Failed to cancel SL order for {}: {}", symbol, e.getMessage());
                }
            }
            return false;
        });
    }

    // Getters
    public Map<String, Position> getOpenPositions()  { return openPositions; }
    public Map<String, Order>    getActiveOrders()   { return activeOrders; }
    public Map<String, Order>    getOrderHistory()   { return orderHistory; }

    public void printSummary() {
        log.info("=== OMS Summary ===");
        log.info("Open Positions: {}", openPositions.size());
        openPositions.values().forEach(p -> log.info("  {}", p));
        log.info("Daily PnL: {:.2f}", riskManager.getDailyPnL());
        log.info("Capital: {:.2f}", riskManager.getCurrentCapital());
    }
}
