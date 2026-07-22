package com.mehmetserin.banking.transfer;

import com.mehmetserin.banking.account.Account;
import com.mehmetserin.banking.account.AccountRepository;
import com.mehmetserin.banking.common.exception.InsufficientFundsException;
import com.mehmetserin.banking.common.exception.InvalidTransferException;
import com.mehmetserin.banking.transfer.dto.TransferRequest;
import com.mehmetserin.banking.transfer.dto.TransferResponse;
import com.mehmetserin.banking.user.AppUser;
import com.mehmetserin.banking.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AppUserRepository userRepository;
    @Mock
    private PlatformTransactionManager transactionManager;

    private TransferService transferService;
    private AppUser user;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(transferRepository, ledgerEntryRepository, accountRepository,
                userRepository, transactionManager);
        user = new AppUser("alice", "alice@example.com", "hashed");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
    }

    @Test
    void replaysExistingTransferInsteadOfMovingMoneyTwice() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        Transfer existing = new Transfer("key-1", user.getId(), from, to, new BigDecimal("25.00"), "USD");
        when(transferRepository.findByIdempotencyKeyAndInitiatedBy("key-1", user.getId()))
                .thenReturn(Optional.of(existing));

        TransferResponse response = transferService.transfer("alice", "key-1",
                new TransferRequest(from, to, new BigDecimal("25.00"), "USD"));

        assertThat(response.amount()).isEqualByComparingTo("25.00");
        verify(accountRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void rejectsTransferToTheSameAccount() {
        UUID accountId = UUID.randomUUID();
        when(transferRepository.findByIdempotencyKeyAndInitiatedBy(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.transfer("alice", "key-2",
                new TransferRequest(accountId, accountId, new BigDecimal("10.00"), "USD")))
                .isInstanceOf(InvalidTransferException.class);
        verify(accountRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void rejectsCurrencyMismatchBetweenAccounts() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        when(transferRepository.findByIdempotencyKeyAndInitiatedBy(any(), any())).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(from))
                .thenReturn(Optional.of(new Account(user.getId(), "TR0000000001", "USD", new BigDecimal("100.00"))));
        when(accountRepository.findByIdForUpdate(to))
                .thenReturn(Optional.of(new Account(user.getId(), "TR0000000002", "EUR", new BigDecimal("100.00"))));

        assertThatThrownBy(() -> transferService.transfer("alice", "key-3",
                new TransferRequest(from, to, new BigDecimal("10.00"), "USD")))
                .isInstanceOf(InvalidTransferException.class);
    }

    @Test
    void propagatesInsufficientFundsFromTheSourceAccount() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        when(transferRepository.findByIdempotencyKeyAndInitiatedBy(any(), any())).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(from))
                .thenReturn(Optional.of(new Account(user.getId(), "TR0000000001", "USD", new BigDecimal("10.00"))));
        when(accountRepository.findByIdForUpdate(to))
                .thenReturn(Optional.of(new Account(user.getId(), "TR0000000002", "USD", new BigDecimal("10.00"))));

        assertThatThrownBy(() -> transferService.transfer("alice", "key-4",
                new TransferRequest(from, to, new BigDecimal("50.00"), "USD")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void alwaysLocksTheLowerAccountIdFirstToAvoidDeadlocks() {
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID high = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(transferRepository.findByIdempotencyKeyAndInitiatedBy(any(), any())).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(low))
                .thenReturn(Optional.of(new Account(user.getId(), "TR0000000001", "USD", new BigDecimal("100.00"))));
        when(accountRepository.findByIdForUpdate(high))
                .thenReturn(Optional.of(new Account(user.getId(), "TR0000000002", "USD", new BigDecimal("100.00"))));

        // request names the higher-id account as the source, to prove ordering
        // does not follow from/to but a fixed id order.
        transferService.transfer("alice", "key-5", new TransferRequest(high, low, new BigDecimal("10.00"), "USD"));

        InOrder order = inOrder(accountRepository);
        order.verify(accountRepository).findByIdForUpdate(low);
        order.verify(accountRepository).findByIdForUpdate(high);
    }
}
