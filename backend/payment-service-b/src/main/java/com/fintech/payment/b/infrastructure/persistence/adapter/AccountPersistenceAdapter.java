package com.fintech.payment.b.infrastructure.persistence.adapter;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.fintech.payment.b.domain.model.Account;
import com.fintech.payment.b.domain.port.out.AccountRepository;
import com.fintech.payment.b.infrastructure.persistence.entity.AccountJpaEntity;
import com.fintech.payment.b.infrastructure.persistence.mapper.AccountPersistenceMapper;
import com.fintech.payment.b.infrastructure.persistence.repository.AccountPanacheRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AccountPersistenceAdapter implements AccountRepository {

    private final AccountPanacheRepository repository;
    private final AccountPersistenceMapper mapper;

    public AccountPersistenceAdapter(
            AccountPanacheRepository repository,
            AccountPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Account> findById(UUID accountId) {
        Objects.requireNonNull(
                accountId,
                "accountId must not be null");

        // findById yerine findByIdOptional kullanıyoruz
        return repository.findByIdOptional(accountId)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public void update(Account account) {
        Objects.requireNonNull(
                account,
                "account must not be null");

        // findById yerine findByIdOptional kullanıyoruz
        AccountJpaEntity entity = repository.findByIdOptional(account.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Account disappeared during transaction: "
                                + account.getId()));

        if (entity.getVersion() == null
                || entity.getVersion().longValue() != account.getVersion()) {
            throw new OptimisticLockException(
                    "Account version changed before update: "
                            + account.getId());
        }

        entity.setBalance(account.getBalance());
        entity.setUpdatedAt(account.getUpdatedAt());
    }
}