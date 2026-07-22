package com.mehmetserin.banking.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal openingBalance
) {
}
