package com.nse.algotrading.model;

import java.time.LocalDateTime;

/**
 * Represents a single market tick (real-time price update) from NSE.
 */
public class Tick {
    private long instrumentToken;
    private String symbol;
    private String exchange;          // NSE, NFO, BSE
    private double lastTradedPrice;
    private double open;
    private double high;
    private double low;
    private double close;             // previous day close
    private long volume;
    private long lastTradedQuantity;
    private double averageTradePrice;
    private double buyQuantity;
    private double sellQuantity;
    private double openInterest;      // for F&O
    private double bidPrice;
    private double askPrice;
    private long bidQuantity;
    private long askQuantity;
    private LocalDateTime timestamp;
    private double changePercent;     // % change from prev close

    public Tick() {}

    public Tick(String symbol, double ltp, long volume, LocalDateTime timestamp) {
        this.symbol = symbol;
        this.lastTradedPrice = ltp;
        this.volume = volume;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public long getInstrumentToken() { return instrumentToken; }
    public void setInstrumentToken(long instrumentToken) { this.instrumentToken = instrumentToken; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public double getLastTradedPrice() { return lastTradedPrice; }
    public void setLastTradedPrice(double lastTradedPrice) {
        this.lastTradedPrice = lastTradedPrice;
    }

    public double getOpen() { return open; }
    public void setOpen(double open) { this.open = open; }

    public double getHigh() { return high; }
    public void setHigh(double high) { this.high = high; }

    public double getLow() { return low; }
    public void setLow(double low) { this.low = low; }

    public double getClose() { return close; }
    public void setClose(double close) { this.close = close; }

    public long getVolume() { return volume; }
    public void setVolume(long volume) { this.volume = volume; }

    public long getLastTradedQuantity() { return lastTradedQuantity; }
    public void setLastTradedQuantity(long lastTradedQuantity) {
        this.lastTradedQuantity = lastTradedQuantity;
    }

    public double getAverageTradePrice() { return averageTradePrice; }
    public void setAverageTradePrice(double averageTradePrice) {
        this.averageTradePrice = averageTradePrice;
    }

    public double getBuyQuantity() { return buyQuantity; }
    public void setBuyQuantity(double buyQuantity) { this.buyQuantity = buyQuantity; }

    public double getSellQuantity() { return sellQuantity; }
    public void setSellQuantity(double sellQuantity) { this.sellQuantity = sellQuantity; }

    public double getOpenInterest() { return openInterest; }
    public void setOpenInterest(double openInterest) { this.openInterest = openInterest; }

    public double getBidPrice() { return bidPrice; }
    public void setBidPrice(double bidPrice) { this.bidPrice = bidPrice; }

    public double getAskPrice() { return askPrice; }
    public void setAskPrice(double askPrice) { this.askPrice = askPrice; }

    public long getBidQuantity() { return bidQuantity; }
    public void setBidQuantity(long bidQuantity) { this.bidQuantity = bidQuantity; }

    public long getAskQuantity() { return askQuantity; }
    public void setAskQuantity(long askQuantity) { this.askQuantity = askQuantity; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public double getChangePercent() { return changePercent; }
    public void setChangePercent(double changePercent) { this.changePercent = changePercent; }

    @Override
    public String toString() {
        return String.format("[%s] %s LTP=%.2f Vol=%d Change=%.2f%%",
                timestamp, symbol, lastTradedPrice, volume, changePercent);
    }
}
