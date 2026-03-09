package com.nse.algotrading.config;

/**
 * Central configuration for the algo trading application.
 * Load from application.properties or environment variables in production.
 */
public class TradingConfig {

    // ── Broker API Credentials (Zerodha Kite) ──────────────────────────────
    private String apiKey        = System.getenv("KITE_API_KEY");
    private String apiSecret     = System.getenv("KITE_API_SECRET");
    private String accessToken   = System.getenv("KITE_ACCESS_TOKEN");
    private String brokerBaseUrl = "https://api.kite.trade";

    // ── Market Settings ─────────────────────────────────────────────────────
    private String marketOpenTime    = "09:15";
    private String marketCloseTime   = "15:30";
    private String preOpenStartTime  = "09:00";
    private String squareOffTime     = "15:15";    // auto square-off before close

    // ── Trading Mode ────────────────────────────────────────────────────────
    private boolean paperTrading     = true;       // IMPORTANT: start with paper trading!
    private boolean liveTrading      = false;
    private double  initialCapital   = 100000.0;   // 1 Lakh INR

    // ── Risk Management ─────────────────────────────────────────────────────
    private double maxDailyLossPercent   = 2.0;    // 2% of capital = 2000 INR
    private double maxPositionSizePercent= 10.0;   // 10% per trade
    private double maxDrawdownPercent    = 5.0;    // stop trading at 5% drawdown
    private int    maxOpenPositions      = 5;
    private double defaultStopLossPercent= 0.5;    // 0.5% SL
    private double defaultTargetPercent  = 1.0;    // 1% target (1:2 RR)

    // ── Strategy Settings ───────────────────────────────────────────────────
    private int    emaPeriodFast         = 9;
    private int    emaPeriodSlow         = 21;
    private int    rsiPeriod             = 14;
    private double rsiOversold           = 30.0;
    private double rsiOverbought         = 70.0;
    private int    orbMinutes            = 15;     // Opening Range Breakout window

    // ── Watchlist (NSE symbols) ─────────────────────────────────────────────
    private String[] watchlist = {
        "RELIANCE", "TCS", "INFY", "HDFCBANK", "ICICIBANK",
        "NIFTY50",  "BANKNIFTY", "SBIN", "WIPRO", "AXISBANK"
    };

    // ── Database ─────────────────────────────────────────────────────────────
    private String dbPath = "algo_trading.db";

    // ── Notifications ────────────────────────────────────────────────────────
    private String telegramBotToken  = System.getenv("TELEGRAM_BOT_TOKEN");
    private String telegramChatId    = System.getenv("TELEGRAM_CHAT_ID");

    // Singleton
    private static TradingConfig instance;

    private TradingConfig() {}

    public static TradingConfig getInstance() {
        if (instance == null) {
            instance = new TradingConfig();
        }
        return instance;
    }

    public double getMaxDailyLoss() {
        return initialCapital * (maxDailyLossPercent / 100.0);
    }

    public double getMaxPositionValue() {
        return initialCapital * (maxPositionSizePercent / 100.0);
    }

    // Getters and Setters
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getApiSecret() { return apiSecret; }
    public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getBrokerBaseUrl() { return brokerBaseUrl; }
    public void setBrokerBaseUrl(String brokerBaseUrl) { this.brokerBaseUrl = brokerBaseUrl; }

    public String getMarketOpenTime() { return marketOpenTime; }
    public String getMarketCloseTime() { return marketCloseTime; }
    public String getSquareOffTime()   { return squareOffTime; }

    public boolean isPaperTrading() { return paperTrading; }
    public void setPaperTrading(boolean paperTrading) { this.paperTrading = paperTrading; }

    public boolean isLiveTrading() { return liveTrading; }
    public void setLiveTrading(boolean liveTrading) { this.liveTrading = liveTrading; }

    public double getInitialCapital() { return initialCapital; }
    public void setInitialCapital(double initialCapital) { this.initialCapital = initialCapital; }

    public double getMaxDailyLossPercent() { return maxDailyLossPercent; }
    public double getMaxPositionSizePercent() { return maxPositionSizePercent; }
    public double getMaxDrawdownPercent() { return maxDrawdownPercent; }

    public int getMaxOpenPositions() { return maxOpenPositions; }
    public void setMaxOpenPositions(int maxOpenPositions) {
        this.maxOpenPositions = maxOpenPositions;
    }

    public double getDefaultStopLossPercent() { return defaultStopLossPercent; }
    public double getDefaultTargetPercent()   { return defaultTargetPercent; }

    public int getEmaPeriodFast()   { return emaPeriodFast; }
    public int getEmaPeriodSlow()   { return emaPeriodSlow; }
    public int getRsiPeriod()       { return rsiPeriod; }
    public double getRsiOversold()  { return rsiOversold; }
    public double getRsiOverbought(){ return rsiOverbought; }
    public int getOrbMinutes()      { return orbMinutes; }

    public String[] getWatchlist() { return watchlist; }
    public void setWatchlist(String[] watchlist) { this.watchlist = watchlist; }

    public String getDbPath() { return dbPath; }
    public void setDbPath(String dbPath) { this.dbPath = dbPath; }

    public String getTelegramBotToken() { return telegramBotToken; }
    public String getTelegramChatId()   { return telegramChatId; }
}
