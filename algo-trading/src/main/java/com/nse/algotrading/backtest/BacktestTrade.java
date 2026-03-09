package com.nse.algotrading.backtest;

import java.time.LocalDateTime;

/**
 * A single completed trade recorded during backtesting.
 */
public class BacktestTrade {

    public enum Direction { LONG, SHORT }

    private String        symbol;
    private Direction     direction;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private double        entryPrice;
    private double        exitPrice;
    private int           quantity;
    private double        pnl;
    private double        pnlPercent;
    private String        exitReason;   // TARGET, STOP_LOSS, EOD_SQUAREOFF, SIGNAL

    public BacktestTrade() {}

    public BacktestTrade(String symbol, Direction direction, LocalDateTime entryTime,
                          double entryPrice, int quantity) {
        this.symbol     = symbol;
        this.direction  = direction;
        this.entryTime  = entryTime;
        this.entryPrice = entryPrice;
        this.quantity   = quantity;
    }

    public void close(LocalDateTime exitTime, double exitPrice, String reason) {
        this.exitTime   = exitTime;
        this.exitPrice  = exitPrice;
        this.exitReason = reason;

        if (direction == Direction.LONG) {
            this.pnl = (exitPrice - entryPrice) * quantity;
        } else {
            this.pnl = (entryPrice - exitPrice) * quantity;
        }
        this.pnlPercent = (pnl / (entryPrice * quantity)) * 100;
    }

    public boolean isWinner() { return pnl > 0; }
    public boolean isLoser()  { return pnl < 0; }

    // Getters and Setters
    public String getSymbol()            { return symbol; }
    public void setSymbol(String v)      { this.symbol = v; }

    public Direction getDirection()      { return direction; }
    public void setDirection(Direction v){ this.direction = v; }

    public LocalDateTime getEntryTime()  { return entryTime; }
    public void setEntryTime(LocalDateTime v) { this.entryTime = v; }

    public LocalDateTime getExitTime()   { return exitTime; }
    public void setExitTime(LocalDateTime v)  { this.exitTime = v; }

    public double getEntryPrice()        { return entryPrice; }
    public void setEntryPrice(double v)  { this.entryPrice = v; }

    public double getExitPrice()         { return exitPrice; }
    public void setExitPrice(double v)   { this.exitPrice = v; }

    public int getQuantity()             { return quantity; }
    public void setQuantity(int v)       { this.quantity = v; }

    public double getPnl()               { return pnl; }
    public double getPnlPercent()        { return pnlPercent; }

    public String getExitReason()        { return exitReason; }
    public void setExitReason(String v)  { this.exitReason = v; }

    @Override
    public String toString() {
        return String.format("[%s] %s %s qty=%d entry=%.2f exit=%.2f PnL=%.2f (%.2f%%) reason=%s",
                entryTime, direction, symbol, quantity,
                entryPrice, exitPrice, pnl, pnlPercent, exitReason);
    }
}
