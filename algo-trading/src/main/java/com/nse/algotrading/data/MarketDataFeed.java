package com.nse.algotrading.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nse.algotrading.config.TradingConfig;
import com.nse.algotrading.model.Tick;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Real-time market data feed using Kite WebSocket API.
 *
 * Kite WebSocket connects to wss://ws.kite.trade
 * Subscribe to instrument tokens to receive live ticks.
 *
 * Tick modes:
 *   ltp      - only last traded price
 *   quote    - LTP + OHLC + volume
 *   full     - quote + depth (bid/ask 5 levels)
 */
public class MarketDataFeed {

    private static final Logger log = LoggerFactory.getLogger(MarketDataFeed.class);
    private static final String WS_URL = "wss://ws.kite.trade";

    private final TradingConfig config;
    private final ObjectMapper  mapper;
    private KiteWebSocketClient wsClient;

    // Registered tick listeners (strategy name → handler)
    private final List<Consumer<Tick>> tickListeners = new ArrayList<>();

    // Latest tick cache per symbol
    private final Map<String, Tick> tickCache = new ConcurrentHashMap<>();

    // Token → symbol mapping
    private final Map<Long, String> tokenToSymbol = new ConcurrentHashMap<>();

    public MarketDataFeed(TradingConfig config) {
        this.config = config;
        this.mapper = new ObjectMapper();
    }

    /**
     * Register a symbol-to-token mapping (get tokens from instruments file).
     */
    public void registerInstrument(long token, String symbol) {
        tokenToSymbol.put(token, symbol);
    }

    /**
     * Add a listener that receives every tick update.
     */
    public void addTickListener(Consumer<Tick> listener) {
        tickListeners.add(listener);
    }

    /**
     * Connect to Kite WebSocket and subscribe to instrument tokens.
     */
    public void connect(List<Long> instrumentTokens) throws Exception {
        String url = WS_URL + "?api_key=" + config.getApiKey()
                + "&access_token=" + config.getAccessToken();

        wsClient = new KiteWebSocketClient(new URI(url));
        wsClient.connectBlocking();

        // Subscribe to instruments in "full" mode
        subscribeTokens(instrumentTokens, "full");
        log.info("MarketDataFeed connected and subscribed to {} instruments",
                instrumentTokens.size());
    }

    public void disconnect() {
        if (wsClient != null && wsClient.isOpen()) {
            wsClient.close();
            log.info("MarketDataFeed disconnected");
        }
    }

    public boolean isConnected() {
        return wsClient != null && wsClient.isOpen();
    }

    /**
     * Get the latest cached tick for a symbol.
     */
    public Tick getLatestTick(String symbol) {
        return tickCache.get(symbol);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void subscribeTokens(List<Long> tokens, String mode) {
        try {
            // Kite subscribe message format
            String subscribeMsg = mapper.writeValueAsString(
                    Map.of("a", "subscribe", "v", tokens));
            wsClient.send(subscribeMsg);

            String modeMsg = mapper.writeValueAsString(
                    Map.of("a", "mode", "v", List.of(mode, tokens)));
            wsClient.send(modeMsg);
        } catch (Exception e) {
            log.error("Failed to subscribe to tokens", e);
        }
    }

    /**
     * Parse Kite binary tick packet (simplified JSON-mode parsing).
     * Kite can send binary (compact) or text (JSON) ticks.
     */
    private Tick parseTick(String message) {
        try {
            JsonNode node = mapper.readTree(message);
            if (node.has("data")) {
                JsonNode data = node.path("data");
                Tick tick = new Tick();
                long token = data.path("instrument_token").asLong();
                tick.setInstrumentToken(token);
                tick.setSymbol(tokenToSymbol.getOrDefault(token, "UNKNOWN"));
                tick.setLastTradedPrice(data.path("last_price").asDouble());
                tick.setOpen(data.path("ohlc").path("open").asDouble());
                tick.setHigh(data.path("ohlc").path("high").asDouble());
                tick.setLow(data.path("ohlc").path("low").asDouble());
                tick.setClose(data.path("ohlc").path("close").asDouble());
                tick.setVolume(data.path("volume").asLong());
                tick.setLastTradedQuantity(data.path("last_quantity").asLong());
                tick.setAverageTradePrice(data.path("average_price").asDouble());
                tick.setOpenInterest(data.path("oi").asDouble());
                tick.setBidPrice(data.path("depth").path("buy").path(0)
                        .path("price").asDouble());
                tick.setAskPrice(data.path("depth").path("sell").path(0)
                        .path("price").asDouble());
                tick.setTimestamp(LocalDateTime.now());

                // Calculate % change from previous close
                double prevClose = tick.getClose();
                if (prevClose > 0) {
                    tick.setChangePercent(
                            ((tick.getLastTradedPrice() - prevClose) / prevClose) * 100);
                }
                return tick;
            }
        } catch (Exception e) {
            log.warn("Failed to parse tick: {}", e.getMessage());
        }
        return null;
    }

    // ── WebSocket Client ───────────────────────────────────────────────────────

    private class KiteWebSocketClient extends WebSocketClient {

        public KiteWebSocketClient(URI uri) {
            super(uri);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            log.info("WebSocket connection opened");
        }

        @Override
        public void onMessage(String message) {
            Tick tick = parseTick(message);
            if (tick != null) {
                // Cache latest tick
                tickCache.put(tick.getSymbol(), tick);

                // Notify all listeners
                for (Consumer<Tick> listener : tickListeners) {
                    try {
                        listener.accept(tick);
                    } catch (Exception e) {
                        log.error("Tick listener error: {}", e.getMessage());
                    }
                }
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            log.warn("WebSocket closed: code={} reason={} remote={}", code, reason, remote);
            if (remote) {
                // Auto-reconnect after 5 seconds
                new Thread(() -> {
                    try {
                        Thread.sleep(5000);
                        log.info("Attempting WebSocket reconnect...");
                        reconnect();
                    } catch (Exception e) {
                        log.error("Reconnect failed", e);
                    }
                }).start();
            }
        }

        @Override
        public void onError(Exception ex) {
            log.error("WebSocket error: {}", ex.getMessage());
        }
    }
}
