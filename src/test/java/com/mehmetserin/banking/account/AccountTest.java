package com.mehmetserin.banking.account;

import com.mehmetserin.banking.common.exception.InsufficientFundsException;
import com.mehmetserin.banking.common.exception.InvalidTransferException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private Account newAccount(String openingBalance) {
        return new Account(UUID.randomUUID(), "TR0000000001", "USD", new BigDecimal(openingBalance));
    }

    @Test
    void debitReducesBalanceWhenFundsAreSufficient() {
        Account account = newAccount("100.00");

        account.debit(new BigDecimal("40.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("60.00");
    }

    @Test
    void debitThrowsWhenFundsAreInsufficient() {
        Account account = newAccount("50.00");

        assertThatThrownBy(() -> account.debit(new BigDecimal("50.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void debitOfExactBalanceIsAllowed() {
        Account account = newAccount("50.00");

        account.debit(new BigDecimal("50.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void creditIncreasesBalance() {
        Account account = newAccount("10.00");

        account.credit(new BigDecimal("5.50"));

        assertThat(account.getBalance()).isEqualByComparingTo("15.50");
    }

    @Test
    void debitRejectsZeroOrNegativeAmount() {
        Account account = newAccount("10.00");

        assertThatThrownBy(() -> account.debit(BigDecimal.ZERO)).isInstanceOf(InvalidTransferException.class);
        assertThatThrownBy(() -> account.debit(new BigDecimal("-1.00"))).isInstanceOf(InvalidTransferException.class);
    }

    @Test
    void creditRejectsZeroOrNegativeAmount() {
        Account account = newAccount("10.00");

        assertThatThrownBy(() -> account.credit(BigDecimal.ZERO)).isInstanceOf(InvalidTransferException.class);
        assertThatThrownBy(() -> account.credit(new BigDecimal("-1.00"))).isInstanceOf(InvalidTransferException.class);
    }

    @Test
    void openingBalanceCannotBeNegative() {
        assertThatThrownBy(() -> newAccount("-0.01")).isInstanceOf(InvalidTransferException.class);
    }

    @Test
    void closedAccountRejectsDebitAndCredit() {
        Account account = newAccount("100.00");
        closeAccount(account);

        assertThatThrownBy(() -> account.debit(new BigDecimal("1.00"))).isInstanceOf(InvalidTransferException.class);
        assertThatThrownBy(() -> account.credit(new BigDecimal("1.00"))).isInstanceOf(InvalidTransferException.class);
    }

    private void closeAccount(Account account) {
        try {
            var field = Account.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(account, AccountStatus.CLOSED);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
