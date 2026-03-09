# NSE Algo Trading Application

Java-based algorithmic trading system for India's National Stock Exchange (NSE).

## Architecture

```
algo-trading/
├── src/main/java/com/nse/algotrading/
│   ├── AlgoTradingApp.java          ← Main entry point
│   ├── config/
│   │   └── TradingConfig.java       ← Central configuration
│   ├── data/
│   │   ├── BrokerClient.java        ← Zerodha Kite REST API client
│   │   └── MarketDataFeed.java      ← WebSocket real-time tick feed
│   ├── model/
│   │   ├── Tick.java                ← Real-time market tick
│   │   ├── OHLCV.java               ← Candlestick bar
│   │   ├── Order.java               ← Trading order
│   │   ├── Position.java            ← Open/closed position
│   │   └── Signal.java              ← Strategy signal (BUY/SELL/HOLD)
│   ├── strategy/
│   │   ├── TradingStrategy.java     ← Strategy interface
│   │   ├── BaseStrategy.java        ← Common indicators (EMA, RSI, ATR, VWAP)
│   │   ├── EMACrossoverStrategy.java← EMA 9/21 crossover
│   │   ├── OpeningRangeBreakoutStrategy.java ← ORB (15-min)
│   │   ├── VWAPStrategy.java        ← VWAP mean reversion
│   │   └── RSIDivergenceStrategy.java ← RSI divergence + EMA filter
│   ├── orders/
│   │   └── OrderManager.java        ← OMS: signal → order lifecycle
│   ├── risk/
│   │   └── RiskManager.java         ← Pre-trade risk checks
│   ├── backtest/
│   │   ├── Backtester.java          ← Historical strategy simulation
│   │   ├── BacktestResult.java      ← Performance metrics
│   │   └── BacktestTrade.java       ← Individual trade record
│   └── util/
│       └── MarketUtils.java         ← NSE market hours, holidays, lot sizes
└── src/test/
    └── ... (JUnit 5 tests)
```

## Strategies Implemented

| Strategy | Type | Timeframe | Best For |
|----------|------|-----------|----------|
| EMA Crossover (9/21) | Trend Following | 15-min, 1-hour | Trending stocks |
| Opening Range Breakout | Momentum | 1-min, 5-min | Nifty, Bank Nifty |
| VWAP Mean Reversion | Mean Reversion | 5-min | Large caps |
| RSI Divergence | Reversal | 1-hour, Daily | Swing trades |

## Quick Start

### Prerequisites
- Java 11+
- Maven 3.6+
- Zerodha Kite Connect API account (for live/paper trading)

### Build
```bash
cd algo-trading
mvn clean package -DskipTests
```

### Run Backtest (no API key needed)
```bash
java -jar target/algo-trading-1.0.0.jar backtest RELIANCE EMA_CROSSOVER
java -jar target/algo-trading-1.0.0.jar backtest NIFTY50 ORB
```

### Run Paper Trading (requires Kite API)
```bash
export KITE_API_KEY=your_api_key
export KITE_ACCESS_TOKEN=your_access_token

java -jar target/algo-trading-1.0.0.jar paper RELIANCE TCS EMA_CROSSOVER
```

### Run Tests
```bash
mvn test
```

## Configuration

Set these environment variables:

```bash
KITE_API_KEY=xxxx          # Zerodha API key
KITE_API_SECRET=xxxx       # Zerodha API secret
KITE_ACCESS_TOKEN=xxxx     # Daily access token (regenerate each day)
TELEGRAM_BOT_TOKEN=xxxx    # Optional: trade alerts
TELEGRAM_CHAT_ID=xxxx      # Optional: trade alerts
```

Or modify `TradingConfig.java` directly for hardcoded values (not recommended for production).

## Risk Management Rules

The RiskManager enforces these rules before every order:

1. **Max Daily Loss**: 2% of capital (default ₹2,000 on ₹1L capital)
2. **Max Drawdown**: 5% from peak — halts trading
3. **Max Position Size**: 10% of capital per trade
4. **Max Open Positions**: 5 simultaneous trades
5. **Position Sizing**: Fixed fractional (1% risk per trade)
6. **Auto Square-Off**: All MIS positions closed at 15:15 IST

## Backtest Metrics

The backtester calculates:
- Total PnL and % return
- Win rate, Avg win/loss, Profit factor
- Max drawdown, Sharpe ratio, Calmar ratio
- Win/Loss streaks
- Transaction costs (brokerage, STT, exchange charges, GST)

## Adding a New Strategy

```java
public class MyStrategy extends BaseStrategy {

    public MyStrategy(String symbol, TradingConfig config) {
        super(symbol, config);
        this.minBarsRequired = 30;
    }

    @Override
    public String getName() { return "MY_STRATEGY"; }

    @Override
    public Signal generateSignal() {
        if (!isReady()) return holdSignal();

        double ltp  = latestTick.getLastTradedPrice();
        double rsi  = rsi(14);
        double ema  = ema(20);

        if (rsi < 30 && ltp > ema) {
            return buildSignal(Signal.Action.BUY, ltp,
                    atrStopLoss(ltp, true, 1.5),
                    ltp * 1.02);
        }
        return holdSignal();
    }
}
```

Then register it in `AlgoTradingApp.createStrategy()`.

## Important Disclaimers

- **Always start with paper trading** before using real money
- Past performance does not guarantee future results
- Algo trading carries significant financial risk
- Ensure compliance with SEBI regulations
- F&O trading requires understanding of derivatives and margin requirements
- Never risk more than you can afford to lose

## NSE Market Details

| Parameter | Value |
|-----------|-------|
| Trading Hours | 09:15 - 15:30 IST |
| Pre-open | 09:00 - 09:15 IST |
| MIS Square-off | 15:15 IST |
| Tick Size (Equity) | ₹0.05 |
| Circuit Limits | 5%, 10%, 20% |
| Nifty 50 Lot Size | 25 |
| Bank Nifty Lot Size | 15 |
