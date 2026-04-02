package com.heritage.marketplace.inventory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class DocumentRefGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public String next(InventoryDocumentType type) {
        String prefix = switch (type) {
            case INBOUND -> "INB";
            case OUTBOUND -> "OUT";
            case STOCKTAKE -> "STK";
            case RESERVATION -> "RSV";
            case RESERVATION_RELEASE -> "REL";
            case ORDER_DEDUCTION -> "DED";
            case CANCELLATION_ROLLBACK -> "RBK";
        };

        String datePart = LocalDate.now().format(DATE_FORMAT);
        String key = prefix + "-" + datePart;
        int sequence = counters.computeIfAbsent(key, unused -> new AtomicInteger(0)).incrementAndGet();
        return "%s-%s-%03d".formatted(prefix, datePart, sequence);
    }
}
