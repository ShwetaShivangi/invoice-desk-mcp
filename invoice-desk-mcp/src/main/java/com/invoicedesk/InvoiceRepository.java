package com.invoicedesk;

import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.util.List;

@Singleton
public class InvoiceRepository {

    //keeping it in-memory for now
    private final List<Invoice> invoices = List.of(
            new Invoice("INV-1001", "Acme Co", new BigDecimal("1200.00"), "PAID"),
            new Invoice("INV-1002", "Globex", new BigDecimal("560.50"), "OPEN"),
            new Invoice("INV-1003", "Initech", new BigDecimal("89.99"), "OVERDUE"));

    public List<Invoice> findRecent() {
        return invoices;
    }
}