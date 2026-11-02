package com.invoicedesk;

import io.micronaut.serde.annotation.Serdeable;
import java.math.BigDecimal;

@Serdeable
public record Invoice(String id, String customer, BigDecimal amount, String status) {}