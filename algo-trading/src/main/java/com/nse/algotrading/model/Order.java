package com.nse.algotrading.model;

import java.time.LocalDateTime;

/**
 * Represents a trading order placed on NSE via broker API.
 */
public class Order {

    public enum OrderType   { MARKET, LIMIT, SL, SL_M }
    public enum ProductType { MIS, CNC, NRML }        // MIS=Intraday, CNC=Delivery, NRML=F&O
    public enum Validity    { DAY, IOC, TTL }
    public enum Status      { PENDING, OPEN, COMPLETE, CANCELLED, REJECTED }
    public enum TransactionType { BUY, SELL }
    public enum Exchange    { NSE, NFO, BSE, MCX, CDS }

    private String orderId;
    private String parentOrderId;
    private String symbol;
    private Exchange exchange;
    private TransactionType transactionType;
    private OrderType orderType;
    private ProductType productType;
    private Validity validity;
    private Status status;

    private int quantity;
    private int filledQuantity;
    private int pendingQuantity;

    private double price;           // for LIMIT orders
    private double triggerPrice;    // for SL orders
    private double averagePrice;    // execution average price

    private String strategyName;
    private LocalDateTime placedAt;
    private LocalDateTime updatedAt;
    private String rejectionReason;

    public Order() {}

    public Order(String symbol, TransactionType txType, OrderType orderType,
                 ProductType productType, int quantity, double price) {
        this.symbol = symbol;
        this.transactionType = txType;
        this.orderType = orderType;
        this.productType = productType;
        this.quantity = quantity;
        this.price = price;
        this.status = Status.PENDING;
        this.validity = Validity.DAY;
        this.exchange = Exchange.NSE;
        this.placedAt = LocalDateTime.now();
    }

    public boolean isComplete() { return status == Status.COMPLETE; }
    public boolean isPending()  { return status == Status.PENDING || status == Status.OPEN; }
    public boolean isRejected() { return status == Status.REJECTED; }

    // Getters and Setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getParentOrderId() { return parentOrderId; }
    public void setParentOrderId(String parentOrderId) { this.parentOrderId = parentOrderId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public Exchange getExchange() { return exchange; }
    public void setExchange(Exchange exchange) { this.exchange = exchange; }

    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public OrderType getOrderType() { return orderType; }
    public void setOrderType(OrderType orderType) { this.orderType = orderType; }

    public ProductType getProductType() { return productType; }
    public void setProductType(ProductType productType) { this.productType = productType; }

    public Validity getValidity() { return validity; }
    public void setValidity(Validity validity) { this.validity = validity; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public int getFilledQuantity() { return filledQuantity; }
    public void setFilledQuantity(int filledQuantity) { this.filledQuantity = filledQuantity; }

    public int getPendingQuantity() { return pendingQuantity; }
    public void setPendingQuantity(int pendingQuantity) { this.pendingQuantity = pendingQuantity; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public double getTriggerPrice() { return triggerPrice; }
    public void setTriggerPrice(double triggerPrice) { this.triggerPrice = triggerPrice; }

    public double getAveragePrice() { return averagePrice; }
    public void setAveragePrice(double averagePrice) { this.averagePrice = averagePrice; }

    public String getStrategyName() { return strategyName; }
    public void setStrategyName(String strategyName) { this.strategyName = strategyName; }

    public LocalDateTime getPlacedAt() { return placedAt; }
    public void setPlacedAt(LocalDateTime placedAt) { this.placedAt = placedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    @Override
    public String toString() {
        return String.format("Order[%s] %s %s %s qty=%d @ %.2f status=%s",
                orderId, transactionType, symbol, orderType, quantity, price, status);
    }
}
