package com.nse.algotrading.model;

import java.time.LocalDateTime;

/**
 * OHLCV (Open, High, Low, Close, Volume) candlestick bar.
 * Used for historical data and backtesting.
 */
public class OHLCV {
    private String symbol;
    private LocalDateTime timestamp;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
    private double openInterest;  // for F&O

    public OHLCV() {}

    public OHLCV(String symbol, LocalDateTime timestamp,
                 double open, double high, double low, double close, long volume) {
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public boolean isBullish() {
        return close > open;
    }

    public boolean isBearish() {
        return close < open;
    }

    public double getBodySize() {
        return Math.abs(close - open);
    }

    public double getRange() {
        return high - low;
    }

    // Getters and Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

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

    public double getOpenInterest() { return openInterest; }
    public void setOpenInterest(double openInterest) { this.openInterest = openInterest; }

    @Override
    public String toString() {
        return String.format("[%s] %s O=%.2f H=%.2f L=%.2f C=%.2f V=%d",
                timestamp, symbol, open, high, low, close, volume);
    }
}
