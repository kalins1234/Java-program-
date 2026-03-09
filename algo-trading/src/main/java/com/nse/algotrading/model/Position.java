package com.nse.algotrading.model;

import java.time.LocalDate;

/**
 * Represents an open or closed trading position.
 */
public class Position {
    private String symbol;
    private String exchange;
    private int quantity;          // positive=long, negative=short
    private double averageBuyPrice;
    private double averageSellPrice;
    private double lastTradedPrice;
    private double unrealisedPnL;
    private double realisedPnL;
    private Order.ProductType productType;
    private LocalDate date;
    private String strategyName;

    public Position() {}

    public Position(String symbol, int quantity, double avgPrice,
                    Order.ProductType productType, String strategyName) {
        this.symbol = symbol;
        this.quantity = quantity;
        this.averageBuyPrice = avgPrice;
        this.productType = productType;
        this.strategyName = strategyName;
        this.date = LocalDate.now();
    }

    public void updateUnrealisedPnL(double ltp) {
        this.lastTradedPrice = ltp;
        if (quantity > 0) {
            this.unrealisedPnL = (ltp - averageBuyPrice) * quantity;
        } else {
            this.unrealisedPnL = (averageSellPrice - ltp) * Math.abs(quantity);
        }
    }

    public double getTotalPnL() {
        return realisedPnL + unrealisedPnL;
    }

    public boolean isLong()  { return quantity > 0; }
    public boolean isShort() { return quantity < 0; }
    public boolean isClosed(){ return quantity == 0; }

    // Getters and Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getAverageBuyPrice() { return averageBuyPrice; }
    public void setAverageBuyPrice(double averageBuyPrice) {
        this.averageBuyPrice = averageBuyPrice;
    }

    public double getAverageSellPrice() { return averageSellPrice; }
    public void setAverageSellPrice(double averageSellPrice) {
        this.averageSellPrice = averageSellPrice;
    }

    public double getLastTradedPrice() { return lastTradedPrice; }
    public void setLastTradedPrice(double lastTradedPrice) {
        this.lastTradedPrice = lastTradedPrice;
    }

    public double getUnrealisedPnL() { return unrealisedPnL; }
    public void setUnrealisedPnL(double unrealisedPnL) { this.unrealisedPnL = unrealisedPnL; }

    public double getRealisedPnL() { return realisedPnL; }
    public void setRealisedPnL(double realisedPnL) { this.realisedPnL = realisedPnL; }

    public Order.ProductType getProductType() { return productType; }
    public void setProductType(Order.ProductType productType) { this.productType = productType; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getStrategyName() { return strategyName; }
    public void setStrategyName(String strategyName) { this.strategyName = strategyName; }

    @Override
    public String toString() {
        return String.format("Position[%s] qty=%d avgBuy=%.2f LTP=%.2f PnL=%.2f",
                symbol, quantity, averageBuyPrice, lastTradedPrice, getTotalPnL());
    }
}
