package com.nse.algotrading.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nse.algotrading.config.TradingConfig;
import com.nse.algotrading.model.OHLCV;
import com.nse.algotrading.model.Order;
import com.nse.algotrading.model.Position;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for Zerodha Kite Connect REST API.
 *
 * Kite Connect API docs: https://kite.trade/docs/connect/v3/
 *
 * Endpoints used:
 *   GET  /instruments                   - fetch instrument list
 *   GET  /quote                         - real-time quotes
 *   GET  /historical/{token}/{interval} - OHLCV historical data
 *   POST /orders/{variety}              - place order
 *   PUT  /orders/{variety}/{id}         - modify order
 *   DELETE /orders/{variety}/{id}       - cancel order
 *   GET  /orders                        - order book
 *   GET  /positions                     - open positions
 *   GET  /portfolio/holdings            - holdings
 */
public class BrokerClient {

    private static final Logger log = LoggerFactory.getLogger(BrokerClient.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TradingConfig config;
    private final OkHttpClient  httpClient;
    private final ObjectMapper  mapper;

    public BrokerClient(TradingConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30,    java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.mapper = new ObjectMapper();
    }

    // ── Authentication ────────────────────────────────────────────────────────

    /**
     * Exchange request token for access token after OAuth login.
     * User must visit: https://kite.trade/connect/login?api_key=YOUR_KEY
     */
    public String generateAccessToken(String requestToken) throws IOException {
        String checksum = sha256(config.getApiKey() + requestToken + config.getApiSecret());

        RequestBody body = new FormBody.Builder()
                .add("api_key",       config.getApiKey())
                .add("request_token", requestToken)
                .add("checksum",      checksum)
                .build();

        Request req = new Request.Builder()
                .url(config.getBrokerBaseUrl() + "/session/token")
                .post(body)
                .addHeader("X-Kite-Version", "3")
                .build();

        try (Response res = httpClient.newCall(req).execute()) {
            JsonNode json = mapper.readTree(res.body().string());
            String token = json.path("data").path("access_token").asText();
            config.setAccessToken(token);
            log.info("Access token generated successfully");
            return token;
        }
    }

    // ── Market Data ────────────────────────────────────────────────────────────

    /**
     * Fetch real-time quote for one or more instruments.
     * symbols format: "NSE:RELIANCE", "NSE:TCS"
     */
    public JsonNode getQuote(String... symbols) throws IOException {
        HttpUrl url = HttpUrl.parse(config.getBrokerBaseUrl() + "/quote")
                .newBuilder()
                .addQueryParameter("i", String.join("&i=", symbols))
                .build();

        Request req = buildGetRequest(url.toString());
        try (Response res = httpClient.newCall(req).execute()) {
            return mapper.readTree(res.body().string()).path("data");
        }
    }

    /**
     * Fetch OHLCV historical data.
     *
     * @param instrumentToken  NSE instrument token (e.g. 738561 for RELIANCE)
     * @param from             start date-time
     * @param to               end date-time
     * @param interval         minute, 3minute, 5minute, 10minute, 15minute,
     *                         30minute, 60minute, day, week, month
     */
    public List<OHLCV> getHistoricalData(long instrumentToken,
                                          LocalDateTime from,
                                          LocalDateTime to,
                                          String interval,
                                          String symbol) throws IOException {
        String url = config.getBrokerBaseUrl()
                + "/instruments/historical/" + instrumentToken + "/" + interval
                + "?from=" + from.format(DT_FMT)
                + "&to="   + to.format(DT_FMT);

        Request req = buildGetRequest(url);
        List<OHLCV> candles = new ArrayList<>();

        try (Response res = httpClient.newCall(req).execute()) {
            JsonNode data = mapper.readTree(res.body().string())
                    .path("data").path("candles");

            for (JsonNode c : data) {
                // Kite returns: [timestamp, open, high, low, close, volume, oi]
                OHLCV bar = new OHLCV(
                        symbol,
                        LocalDateTime.parse(c.get(0).asText(), DT_FMT),
                        c.get(1).asDouble(),  // open
                        c.get(2).asDouble(),  // high
                        c.get(3).asDouble(),  // low
                        c.get(4).asDouble(),  // close
                        c.get(5).asLong()     // volume
                );
                if (c.size() > 6) {
                    bar.setOpenInterest(c.get(6).asDouble());
                }
                candles.add(bar);
            }
        }
        log.info("Fetched {} candles for {} ({})", candles.size(), symbol, interval);
        return candles;
    }

    // ── Order Management ──────────────────────────────────────────────────────

    /**
     * Place an order via Kite Connect API.
     * variety: regular, amo (after-market), co (cover order), iceberg
     */
    public String placeOrder(Order order) throws IOException {
        RequestBody body = new FormBody.Builder()
                .add("exchange",         order.getExchange().name())
                .add("tradingsymbol",    order.getSymbol())
                .add("transaction_type", order.getTransactionType().name())
                .add("quantity",         String.valueOf(order.getQuantity()))
                .add("product",          order.getProductType().name())
                .add("order_type",       order.getOrderType().name())
                .add("validity",         order.getValidity().name())
                .add("price",            String.valueOf(order.getPrice()))
                .add("trigger_price",    String.valueOf(order.getTriggerPrice()))
                .add("tag",              "ALGO_" + order.getStrategyName())
                .build();

        Request req = new Request.Builder()
                .url(config.getBrokerBaseUrl() + "/orders/regular")
                .post(body)
                .addHeader("X-Kite-Version", "3")
                .addHeader("Authorization", "token " + config.getApiKey()
                        + ":" + config.getAccessToken())
                .build();

        try (Response res = httpClient.newCall(req).execute()) {
            String responseBody = res.body().string();
            JsonNode json = mapper.readTree(responseBody);

            if (res.isSuccessful()) {
                String orderId = json.path("data").path("order_id").asText();
                log.info("Order placed: {} orderId={}", order, orderId);
                return orderId;
            } else {
                String errMsg = json.path("message").asText("Order placement failed");
                log.error("Order failed: {} - {}", order, errMsg);
                throw new IOException("Order rejected: " + errMsg);
            }
        }
    }

    /**
     * Cancel an open order.
     */
    public boolean cancelOrder(String orderId) throws IOException {
        Request req = new Request.Builder()
                .url(config.getBrokerBaseUrl() + "/orders/regular/" + orderId)
                .delete()
                .addHeader("X-Kite-Version", "3")
                .addHeader("Authorization", "token " + config.getApiKey()
                        + ":" + config.getAccessToken())
                .build();

        try (Response res = httpClient.newCall(req).execute()) {
            boolean ok = res.isSuccessful();
            log.info("Cancel order {} - {}", orderId, ok ? "success" : "failed");
            return ok;
        }
    }

    /**
     * Fetch current open positions.
     */
    public List<Position> getPositions() throws IOException {
        Request req = buildGetRequest(config.getBrokerBaseUrl() + "/positions");
        List<Position> positions = new ArrayList<>();

        try (Response res = httpClient.newCall(req).execute()) {
            JsonNode data = mapper.readTree(res.body().string()).path("data");

            // Parse net positions (intraday + overnight combined)
            for (JsonNode p : data.path("net")) {
                Position pos = new Position();
                pos.setSymbol(p.path("tradingsymbol").asText());
                pos.setExchange(p.path("exchange").asText());
                pos.setQuantity(p.path("quantity").asInt());
                pos.setAverageBuyPrice(p.path("average_price").asDouble());
                pos.setUnrealisedPnL(p.path("unrealised").asDouble());
                pos.setRealisedPnL(p.path("realised").asDouble());
                pos.setLastTradedPrice(p.path("last_price").asDouble());
                positions.add(pos);
            }
        }
        return positions;
    }

    /**
     * Fetch today's order book.
     */
    public JsonNode getOrders() throws IOException {
        Request req = buildGetRequest(config.getBrokerBaseUrl() + "/orders");
        try (Response res = httpClient.newCall(req).execute()) {
            return mapper.readTree(res.body().string()).path("data");
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Request buildGetRequest(String url) {
        return new Request.Builder()
                .url(url)
                .get()
                .addHeader("X-Kite-Version", "3")
                .addHeader("Authorization", "token " + config.getApiKey()
                        + ":" + config.getAccessToken())
                .build();
    }

    private String sha256(String input) {
        try {
            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 error", e);
        }
    }
}
