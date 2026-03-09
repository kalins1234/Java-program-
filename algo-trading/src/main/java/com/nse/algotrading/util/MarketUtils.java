package com.nse.algotrading.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

/**
 * Utility methods for NSE market timing and calendar checks.
 */
public final class MarketUtils {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final LocalTime MARKET_OPEN      = LocalTime.of(9,  15);
    private static final LocalTime MARKET_CLOSE     = LocalTime.of(15, 30);
    private static final LocalTime PRE_OPEN_START   = LocalTime.of(9,  0);
    private static final LocalTime SAFE_ENTRY_START = LocalTime.of(9,  30); // avoid opening volatility
    private static final LocalTime SQUAREOFF_TIME   = LocalTime.of(15, 15); // MIS square-off

    // NSE Holidays 2024 (add new year's list annually)
    private static final Set<LocalDate> NSE_HOLIDAYS_2024 = Set.of(
        LocalDate.of(2024, 1, 22),   // Ram Mandir Inauguration
        LocalDate.of(2024, 3, 25),   // Holi
        LocalDate.of(2024, 4, 14),   // Dr. Ambedkar Jayanti / Ram Navami
        LocalDate.of(2024, 4, 17),   // Ram Navami
        LocalDate.of(2024, 4, 21),   // Good Sunday (Mahavir Jayanti)
        LocalDate.of(2024, 5, 23),   // Buddha Pournima
        LocalDate.of(2024, 6, 17),   // Eid ul-Adha
        LocalDate.of(2024, 7, 17),   // Muharram
        LocalDate.of(2024, 8, 15),   // Independence Day
        LocalDate.of(2024, 10, 2),   // Gandhi Jayanti / Dussehra
        LocalDate.of(2024, 11, 1),   // Diwali Laxmi Puja
        LocalDate.of(2024, 11, 15),  // Gurunanak Jayanti
        LocalDate.of(2024, 12, 25)   // Christmas
    );

    private MarketUtils() {}

    public static boolean isMarketOpen() {
        return isMarketOpen(LocalDateTime.now(IST));
    }

    public static boolean isMarketOpen(LocalDateTime dt) {
        LocalDate date = dt.toLocalDate();
        LocalTime time = dt.toLocalTime();
        return isMarketDay(date)
                && !time.isBefore(MARKET_OPEN)
                && !time.isAfter(MARKET_CLOSE);
    }

    public static boolean isMarketDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY
                && dow != DayOfWeek.SUNDAY
                && !NSE_HOLIDAYS_2024.contains(date);
    }

    public static boolean isSafeToTrade() {
        LocalDateTime now = LocalDateTime.now(IST);
        LocalTime time    = now.toLocalTime();
        return isMarketDay(now.toLocalDate())
                && !time.isBefore(SAFE_ENTRY_START)
                && !time.isAfter(SQUAREOFF_TIME);
    }

    public static boolean isSquareOffTime() {
        LocalTime now = LocalTime.now(IST);
        return !now.isBefore(SQUAREOFF_TIME) && !now.isAfter(MARKET_CLOSE);
    }

    public static LocalDateTime getNextMarketOpen() {
        LocalDate date = LocalDate.now(IST).plusDays(1);
        while (!isMarketDay(date)) {
            date = date.plusDays(1);
        }
        return LocalDateTime.of(date, MARKET_OPEN);
    }

    public static long millisUntilMarketOpen() {
        LocalDateTime nextOpen = getNextMarketOpen();
        LocalDateTime now      = LocalDateTime.now(IST);
        return java.time.Duration.between(now, nextOpen).toMillis();
    }

    /** NSE lot sizes for major F&O instruments (as of 2024). */
    public static int getLotSize(String symbol) {
        return switch (symbol.toUpperCase()) {
            case "NIFTY", "NIFTY50" -> 25;
            case "BANKNIFTY"         -> 15;
            case "FINNIFTY"          -> 40;
            case "MIDCPNIFTY"        -> 75;
            case "SENSEX"            -> 10;
            case "BANKEX"            -> 15;
            default                  -> 1;  // equity
        };
    }

    /** Round price to nearest NSE tick size (0.05 for equities). */
    public static double roundToTick(double price) {
        return Math.round(price / 0.05) * 0.05;
    }

    /** Calculate brokerage for Zerodha (flat ₹20 or 0.03% whichever is lower). */
    public static double zerodahaBrokerage(double qty, double price) {
        double turnover = qty * price;
        return Math.min(20.0, turnover * 0.0003);
    }
}
