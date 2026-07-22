package com.mehmetserin.banking.account;

import com.mehmetserin.banking.account.dto.AccountResponse;
import com.mehmetserin.banking.account.dto.CreateAccountRequest;
import com.mehmetserin.banking.common.exception.AccountAccessDeniedException;
import com.mehmetserin.banking.common.exception.AccountNotFoundException;
import com.mehmetserin.banking.user.AppUser;
import com.mehmetserin.banking.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class AccountService {

    private static final int MAX_ACCOUNT_NUMBER_ATTEMPTS = 10;

    private final AccountRepository accountRepository;
    private final AppUserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    public AccountService(AccountRepository accountRepository, AppUserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AccountResponse createAccount(String username, CreateAccountRequest request) {
        AppUser owner = findUser(username);
        Account account = new Account(owner.getId(), generateAccountNumber(), request.currency(), request.openingBalance());
        accountRepository.save(account);
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listMyAccounts(String username) {
        AppUser owner = findUser(username);
        return accountRepository.findByOwnerId(owner.getId()).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getMyAccount(String username, UUID accountId) {
        AppUser owner = findUser(username);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        if (!account.getOwnerId().equals(owner.getId())) {
            throw new AccountAccessDeniedException();
        }
        return AccountResponse.from(account);
    }

    private AppUser findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Authenticated user not found: " + username));
    }

    private String generateAccountNumber() {
        for (int attempt = 0; attempt < MAX_ACCOUNT_NUMBER_ATTEMPTS; attempt++) {
            String candidate = "TR" + String.format("%010d", Math.abs(random.nextLong() % 10_000_000_000L));
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique account number");
    }
}
