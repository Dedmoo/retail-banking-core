package com.mehmetserin.banking.transfer;

import com.mehmetserin.banking.account.Account;
import com.mehmetserin.banking.common.exception.InvalidTransferException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Balance on {@link Account} must equal signed ledger sum: CREDIT minus DEBIT.
 * Opening funds are a single CREDIT ({@link LedgerPostingKind#OPENING}); transfers
 * post paired DEBIT/CREDIT rows ({@link LedgerPostingKind#TRANSFER}).
 */
@Service
public class LedgerReconciliationService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerReconciliationService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public BigDecimal ledgerBalance(UUID accountId) {
        BigDecimal sum = ledgerEntryRepository.sumSignedAmountByAccountId(accountId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    public void assertMatches(Account account) {
        BigDecimal fromLedger = ledgerBalance(account.getId());
        if (account.getBalance().compareTo(fromLedger) != 0) {
            throw new InvalidTransferException(
                    "Ledger out of balance for account " + account.getAccountNumber()
                            + ": stored=" + account.getBalance() + " ledger=" + fromLedger);
        }
    }
}
