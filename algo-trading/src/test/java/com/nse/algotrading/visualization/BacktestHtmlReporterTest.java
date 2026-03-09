package com.nse.algotrading.visualization;

import com.nse.algotrading.backtest.BacktestResult;
import com.nse.algotrading.backtest.BacktestTrade;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BacktestHtmlReporterTest {

    @Test
    void testGeneratesHtmlFile() throws IOException {
        BacktestHtmlReporter reporter = new BacktestHtmlReporter();
        BacktestResult result = buildSampleResult();

        String path = reporter.generate(result);

        assertTrue(Files.exists(Paths.get(path)), "HTML file should be created");
        String content = Files.readString(Paths.get(path));
        assertTrue(content.contains("<!DOCTYPE html>"),   "Should be valid HTML");
        assertTrue(content.contains("Chart.js"),          "Should include Chart.js");
        assertTrue(content.contains("Equity Curve"),      "Should have equity curve chart");
        assertTrue(content.contains("EMA_CROSSOVER"),     "Should show strategy name");
        assertTrue(content.contains("RELIANCE"),          "Should show symbol");

        // Cleanup
        Files.deleteIfExists(Paths.get(path));
    }

    @Test
    void testHandlesEmptyTradesGracefully() throws IOException {
        BacktestHtmlReporter reporter = new BacktestHtmlReporter();
        BacktestResult result = new BacktestResult("ORB", "NIFTY50", new ArrayList<>());
        result.setInitialCapital(100000);
        result.setFinalCapital(100000);
        result.setTotalPnL(0);

        String path = reporter.generate(result);
        assertTrue(Files.exists(Paths.get(path)));
        Files.deleteIfExists(Paths.get(path));
    }

    private BacktestResult buildSampleResult() {
        List<BacktestTrade> trades = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            BacktestTrade t = new BacktestTrade("RELIANCE",
                    i % 2 == 0 ? BacktestTrade.Direction.LONG : BacktestTrade.Direction.SHORT,
                    LocalDateTime.now().minusDays(20 - i), 1000 + i * 5, 10);
            t.close(LocalDateTime.now().minusDays(20 - i).plusHours(2),
                    i % 3 == 0 ? 980 + i * 5 : 1020 + i * 5,
                    i % 3 == 0 ? "STOP_LOSS" : "TARGET");
            trades.add(t);
        }

        BacktestResult r = new BacktestResult("EMA_CROSSOVER", "RELIANCE", trades);
        r.setInitialCapital(100000);
        r.setFinalCapital(105000);
        r.setTotalPnL(5000);
        r.setTotalPnLPercent(5.0);
        r.setTotalTrades(20);
        r.setWinningTrades(13);
        r.setLosingTrades(7);
        r.setWinRate(0.65);
        r.setAvgWin(800);
        r.setAvgLoss(300);
        r.setProfitFactor(1.8);
        r.setMaxDrawdown(2500);
        r.setMaxDrawdownPercent(2.5);
        r.setSharpeRatio(1.2);
        r.setCalmarRatio(2.0);
        r.setMaxWinStreak(4);
        r.setMaxLossStreak(2);
        return r;
    }
}
