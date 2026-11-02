package com.invoicedesk;

import io.micronaut.mcp.annotations.Tool;
import jakarta.inject.Singleton;
import java.util.stream.Collectors;

@Singleton
class InvoiceTools {

    private final InvoiceRepository invoices;

    InvoiceTools(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    @Tool(description = "List recent invoices with their status and amount.",
            annotations = @Tool.ToolAnnotations(readOnlyHint = true))
    String listInvoices() {
        return invoices.findRecent().stream()
                .map(i -> "%s  %s  %s  %s".formatted(i.id(), i.customer(), i.status(), i.amount()))
                .collect(Collectors.joining("\n"));
    }
}