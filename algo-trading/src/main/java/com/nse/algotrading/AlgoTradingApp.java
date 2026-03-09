package com.nse.algotrading;

import com.nse.algotrading.backtest.BacktestResult;
import com.nse.algotrading.backtest.Backtester;
import com.nse.algotrading.config.TradingConfig;
import com.nse.algotrading.data.BrokerClient;
import com.nse.algotrading.data.MarketDataFeed;
import com.nse.algotrading.model.OHLCV;
import com.nse.algotrading.model.Signal;
import com.nse.algotrading.model.Tick;
import com.nse.algotrading.orders.OrderManager;
import com.nse.algotrading.risk.RiskManager;
import com.nse.algotrading.strategy.*;
import com.nse.algotrading.util.MarketUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║         NSE Algo Trading Application — Main Entry Point      ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Modes:                                                       ║
 * ║    1. BACKTEST  — Run strategy on historical data             ║
 * ║    2. PAPER     — Live market data, simulated orders          ║
 * ║    3. LIVE      — Live market data, real orders (use caution) ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * Usage:
 *   java -jar algo-trading.jar backtest RELIANCE EMA_CROSSOVER
 *   java -jar algo-trading.jar paper    NIFTY50  ORB
 *   java -jar algo-trading.jar live     BANKNIFTY VWAP
 *
 * Environment variables required for live/paper:
 *   KITE_API_KEY, KITE_API_SECRET, KITE_ACCESS_TOKEN
 */
public class AlgoTradingApp {

    private static final Logger log = LoggerFactory.getLogger(AlgoTradingApp.class);

    private final TradingConfig  config;
    private final BrokerClient   brokerClient;
    private final MarketDataFeed marketFeed;
    private final RiskManager    riskManager;
    private final OrderManager   orderManager;
    private final List<TradingStrategy> strategies = new ArrayList<>();

    private volatile boolean running = false;
    private Timer squareOffTimer;

    public AlgoTradingApp() {
        this.config       = TradingConfig.getInstance();
        this.brokerClient = new BrokerClient(config);
        this.marketFeed   = new MarketDataFeed(config);
        this.riskManager  = new RiskManager(config);
        this.orderManager = new OrderManager(config, brokerClient, riskManager);
    }

    // ── Mode 1: Backtesting ───────────────────────────────────────────────────

    public void runBacktest(String symbol, String strategyName) {
        log.info("Starting backtest: {} on {}", strategyName, symbol);

        // Generate synthetic historical data for demo (replace with real API call)
        List<OHLCV> historicalData = generateSyntheticData(symbol, 500);

        TradingStrategy strategy = createStrategy(strategyName, symbol);
        Backtester backtester    = new Backtester();

        BacktestResult result = backtester.run(strategy, historicalData,
                config.getInitialCapital());
        result.printReport();
    }

    // ── Mode 2 & 3: Paper / Live Trading ─────────────────────────────────────

    public void startTrading(String[] symbols, String strategyName) throws Exception {
        log.info("Starting {} trading mode for symbols: {}",
                config.isPaperTrading() ? "PAPER" : "LIVE",
                Arrays.toString(symbols));

        // Initialize strategies for each symbol
        for (String symbol : symbols) {
            TradingStrategy strategy = createStrategy(strategyName, symbol);
            strategies.add(strategy);

            // Warm up strategy with historical data (last 100 candles)
            warmupStrategy(strategy, symbol);
        }

        // Register market data handler
        marketFeed.addTickListener(this::onTick);

        // Connect WebSocket (use dummy tokens for demo)
        List<Long> tokens = getDemoTokens(symbols);
        if (tokens.isEmpty()) {
            log.warn("No instrument tokens configured. Using simulated ticks for demo.");
            runSimulatedLoop(symbols, strategyName);
            return;
        }

        marketFeed.connect(tokens);
        running = true;

        // Schedule automatic square-off at 15:15
        scheduleSquareOff();

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        log.info("Trading system live. Press Ctrl+C to stop.");
        while (running) {
            Thread.sleep(1000);
            if (!MarketUtils.isMarketOpen()) {
                log.info("Market closed. Stopping trading loop.");
                shutdown();
            }
        }
    }

    /**
     * Core tick handler — called for every market tick.
     */
    private void onTick(Tick tick) {
        if (!MarketUtils.isSafeToTrade()) return;
        if (riskManager.isTradingHalted())   return;

        for (TradingStrategy strategy : strategies) {
            if (!strategy.isReady()) continue;

            strategy.onTick(tick);
            Signal signal = strategy.generateSignal();

            if (signal != null && signal.getAction() != Signal.Action.HOLD) {
                log.info("Signal received: {}", signal);
                orderManager.processSignal(signal);
            }
        }
    }

    private void scheduleSquareOff() {
        squareOffTimer = new Timer("SquareOff", true);
        squareOffTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (MarketUtils.isSquareOffTime()) {
                    log.warn("Square-off time reached. Closing all positions.");
                    orderManager.squareOffAll();
                    orderManager.printSummary();
                    cancel();
                }
            }
        }, 0, 60_000);  // check every minute
    }

    public void shutdown() {
        running = false;
        if (squareOffTimer != null) squareOffTimer.cancel();
        orderManager.squareOffAll();
        marketFeed.disconnect();
        strategies.forEach(TradingStrategy::shutdown);
        orderManager.printSummary();
        log.info("Algo Trading App shutdown complete");
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    private TradingStrategy createStrategy(String name, String symbol) {
        return switch (name.toUpperCase()) {
            case "EMA_CROSSOVER", "EMA" -> new EMACrossoverStrategy(symbol, config);
            case "ORB"                  -> new OpeningRangeBreakoutStrategy(symbol, config);
            case "VWAP"                 -> new VWAPStrategy(symbol, config);
            case "RSI_DIVERGENCE","RSI" -> new RSIDivergenceStrategy(symbol, config);
            default -> throw new IllegalArgumentException("Unknown strategy: " + name
                    + ". Available: EMA_CROSSOVER, ORB, VWAP, RSI_DIVERGENCE");
        };
    }

    private void warmupStrategy(TradingStrategy strategy, String symbol) {
        try {
            LocalDateTime to   = LocalDateTime.now();
            LocalDateTime from = to.minusDays(30);
            // In production: fetch from broker API
            // List<OHLCV> data = brokerClient.getHistoricalData(token, from, to, "15minute", symbol);
            List<OHLCV> data = generateSyntheticData(symbol, 100);
            strategy.initialize(data);
            log.info("Strategy {} warmed up with {} bars for {}", strategy.getName(), data.size(), symbol);
        } catch (Exception e) {
            log.warn("Could not warm up strategy for {}: {}", symbol, e.getMessage());
        }
    }

    // ── Simulation / Demo ─────────────────────────────────────────────────────

    /**
     * Simulated trading loop for demo (no real WebSocket connection needed).
     */
    private void runSimulatedLoop(String[] symbols, String strategyName) throws InterruptedException {
        log.info("Running SIMULATED trading loop (demo mode — no broker connection)");

        // Generate and replay historical data as simulated ticks
        for (String symbol : symbols) {
            List<OHLCV> data = generateSyntheticData(symbol, 300);
            TradingStrategy strategy = createStrategy(strategyName, symbol);
            strategy.initialize(data.subList(0, 50));

            log.info("\n--- Simulating {} bars for {} ---", data.size(), symbol);
            for (int i = 50; i < data.size(); i++) {
                OHLCV bar = data.get(i);
                strategy.onBar(bar);

                Tick tick = new Tick(symbol, bar.getClose(),
                        bar.getVolume(), bar.getTimestamp());
                strategy.onTick(tick);

                Signal signal = strategy.generateSignal();
                if (signal != null && signal.getAction() != Signal.Action.HOLD) {
                    log.info("[SIMULATED] {}", signal);
                    orderManager.processSignal(signal);
                }

                Thread.sleep(10);  // 10ms between bars for demo
            }
        }

        orderManager.printSummary();
    }

    /**
     * Generate synthetic OHLCV data for demo/testing.
     * Uses geometric Brownian motion — realistic price simulation.
     */
    private List<OHLCV> generateSyntheticData(String symbol, int bars) {
        List<OHLCV> data    = new ArrayList<>();
        double price        = 1000.0;  // starting price
        double volatility   = 0.015;   // 1.5% daily volatility
        double drift        = 0.0002;  // slight upward drift
        java.util.Random rng = new java.util.Random(42);

        LocalDateTime start = LocalDateTime.now().minusDays(bars / 75 + 1)
                .withHour(9).withMinute(15).withSecond(0);

        for (int i = 0; i < bars; i++) {
            // GBM price simulation
            double change = drift + volatility * rng.nextGaussian();
            price = price * Math.exp(change / Math.sqrt(bars));
            price = Math.max(price, 1);  // price can't go negative

            double open  = price;
            double high  = open * (1 + Math.abs(rng.nextGaussian()) * 0.003);
            double low   = open * (1 - Math.abs(rng.nextGaussian()) * 0.003);
            double close = low + rng.nextDouble() * (high - low);
            long   vol   = (long)(10000 + rng.nextDouble() * 90000);

            // Advance time (skip weekends)
            LocalDateTime ts = start.plusMinutes((long) i * 5);

            data.add(new OHLCV(symbol, ts, open, high, low, close, vol));
        }
        return data;
    }

    private List<Long> getDemoTokens(String[] symbols) {
        // Return empty list for demo — real tokens from NSE instruments file
        return new ArrayList<>();
    }

    // ── Main ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        printBanner();

        AlgoTradingApp app = new AlgoTradingApp();

        try {
            if (args.length >= 1 && args[0].equalsIgnoreCase("backtest")) {
                String symbol   = args.length > 1 ? args[1] : "RELIANCE";
                String strategy = args.length > 2 ? args[2] : "EMA_CROSSOVER";
                app.runBacktest(symbol, strategy);

            } else if (args.length >= 1 && args[0].equalsIgnoreCase("live")) {
                app.config.setPaperTrading(false);
                app.config.setLiveTrading(true);
                String[] symbols = args.length > 1
                        ? Arrays.copyOfRange(args, 1, args.length - 1)
                        : new String[]{"NIFTY50"};
                String strategy  = args.length > 2 ? args[args.length - 1] : "ORB";
                log.warn("LIVE TRADING MODE - real money at risk!");
                app.startTrading(symbols, strategy);

            } else {
                // Default: paper trading simulation (safe to run!)
                log.info("Starting PAPER trading simulation with EMA Crossover strategy...");
                log.info("(Pass 'backtest', 'paper', or 'live' as first argument to change mode)");
                app.config.setPaperTrading(true);
                app.runSimulatedLoop(new String[]{"RELIANCE", "TCS"}, "EMA_CROSSOVER");

                // Also run a backtest demo
                log.info("\n=== Running Backtest Demo ===");
                app.runBacktest("NIFTY50", "ORB");
            }

        } catch (Exception e) {
            log.error("Application error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private static void printBanner() {
        System.out.println("""
                ╔══════════════════════════════════════════════════════╗
                ║       NSE Algo Trading Application v1.0.0            ║
                ║       India Stock Market — Zerodha Kite API          ║
                ╠══════════════════════════════════════════════════════╣
                ║  Strategies: EMA Crossover, ORB, VWAP, RSI Div       ║
                ║  IMPORTANT: Always start with PAPER trading!          ║
                ╚══════════════════════════════════════════════════════╝
                """);
    }
}
