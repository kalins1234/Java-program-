package com.nse.algotrading.backtest;

import java.util.List;

/**
 * Backtest performance metrics and trade log.
 */
public class BacktestResult {

    private final String strategyName;
    private final String symbol;
    private final List<BacktestTrade> trades;

    private double initialCapital;
    private double finalCapital;
    private double totalPnL;
    private double totalPnLPercent;

    // Win/Loss metrics
    private int    totalTrades;
    private int    winningTrades;
    private int    losingTrades;
    private double winRate;
    private double avgWin;
    private double avgLoss;
    private double profitFactor;      // gross profit / gross loss

    // Risk metrics
    private double maxDrawdown;
    private double maxDrawdownPercent;
    private double sharpeRatio;
    private double calmarRatio;

    // Streaks
    private int maxWinStreak;
    private int maxLossStreak;

    public BacktestResult(String strategyName, String symbol, List<BacktestTrade> trades) {
        this.strategyName = strategyName;
        this.symbol = symbol;
        this.trades = trades;
    }

    public void printReport() {
        System.out.println("\n" + "=".repeat(60));
        System.out.printf("BACKTEST REPORT: %s on %s%n", strategyName, symbol);
        System.out.println("=".repeat(60));
        System.out.printf("%-30s %,.2f%n", "Initial Capital (INR):", initialCapital);
        System.out.printf("%-30s %,.2f%n", "Final Capital (INR):",   finalCapital);
        System.out.printf("%-30s %+,.2f (%+.2f%%)%n", "Total PnL:",
                totalPnL, totalPnLPercent);
        System.out.println("-".repeat(60));
        System.out.printf("%-30s %d%n",    "Total Trades:",    totalTrades);
        System.out.printf("%-30s %d%n",    "Winning Trades:",  winningTrades);
        System.out.printf("%-30s %d%n",    "Losing Trades:",   losingTrades);
        System.out.printf("%-30s %.1f%%%n","Win Rate:",         winRate * 100);
        System.out.printf("%-30s %,.2f%n", "Avg Win (INR):",   avgWin);
        System.out.printf("%-30s %,.2f%n", "Avg Loss (INR):",  avgLoss);
        System.out.printf("%-30s %.2f%n",  "Profit Factor:",   profitFactor);
        System.out.println("-".repeat(60));
        System.out.printf("%-30s %,.2f (%,.2f%%)%n", "Max Drawdown:",
                maxDrawdown, maxDrawdownPercent);
        System.out.printf("%-30s %.3f%n",  "Sharpe Ratio:",    sharpeRatio);
        System.out.printf("%-30s %.3f%n",  "Calmar Ratio:",    calmarRatio);
        System.out.printf("%-30s %d%n",    "Max Win Streak:",  maxWinStreak);
        System.out.printf("%-30s %d%n",    "Max Loss Streak:", maxLossStreak);
        System.out.println("=".repeat(60));
    }

    // Getters and Setters
    public String getStrategyName()         { return strategyName; }
    public String getSymbol()               { return symbol; }
    public String getDescription()          { return strategyName + " on " + symbol; }
    public List<BacktestTrade> getTrades()  { return trades; }

    public double getInitialCapital()       { return initialCapital; }
    public void setInitialCapital(double v) { this.initialCapital = v; }

    public double getFinalCapital()         { return finalCapital; }
    public void setFinalCapital(double v)   { this.finalCapital = v; }

    public double getTotalPnL()             { return totalPnL; }
    public void setTotalPnL(double v)       { this.totalPnL = v; }

    public double getTotalPnLPercent()      { return totalPnLPercent; }
    public void setTotalPnLPercent(double v){ this.totalPnLPercent = v; }

    public int getTotalTrades()             { return totalTrades; }
    public void setTotalTrades(int v)       { this.totalTrades = v; }

    public int getWinningTrades()           { return winningTrades; }
    public void setWinningTrades(int v)     { this.winningTrades = v; }

    public int getLosingTrades()            { return losingTrades; }
    public void setLosingTrades(int v)      { this.losingTrades = v; }

    public double getWinRate()              { return winRate; }
    public void setWinRate(double v)        { this.winRate = v; }

    public double getAvgWin()              { return avgWin; }
    public void setAvgWin(double v)        { this.avgWin = v; }

    public double getAvgLoss()             { return avgLoss; }
    public void setAvgLoss(double v)       { this.avgLoss = v; }

    public double getProfitFactor()        { return profitFactor; }
    public void setProfitFactor(double v)  { this.profitFactor = v; }

    public double getMaxDrawdown()         { return maxDrawdown; }
    public void setMaxDrawdown(double v)   { this.maxDrawdown = v; }

    public double getMaxDrawdownPercent()  { return maxDrawdownPercent; }
    public void setMaxDrawdownPercent(double v) { this.maxDrawdownPercent = v; }

    public double getSharpeRatio()         { return sharpeRatio; }
    public void setSharpeRatio(double v)   { this.sharpeRatio = v; }

    public double getCalmarRatio()         { return calmarRatio; }
    public void setCalmarRatio(double v)   { this.calmarRatio = v; }

    public int getMaxWinStreak()           { return maxWinStreak; }
    public void setMaxWinStreak(int v)     { this.maxWinStreak = v; }

    public int getMaxLossStreak()          { return maxLossStreak; }
    public void setMaxLossStreak(int v)    { this.maxLossStreak = v; }
}
