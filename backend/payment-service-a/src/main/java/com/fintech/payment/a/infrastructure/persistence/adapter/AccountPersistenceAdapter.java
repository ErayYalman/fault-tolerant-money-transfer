package com.fintech.payment.a.infrastructure.persistence.adapter;

import com.fintech.payment.a.domain.model.Account;
import com.fintech.payment.a.domain.port.out.AccountRepository;
import com.fintech.payment.a.infrastructure.persistence.entity.AccountJpaEntity;
import com.fintech.payment.a.infrastructure.persistence.mapper.AccountPersistenceMapper;
import com.fintech.payment.a.infrastructure.persistence.repository.AccountJpaRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public class AccountPersistenceAdapter implements AccountRepository {

    private final AccountJpaRepository repository;
    private final AccountPersistenceMapper mapper;

    public AccountPersistenceAdapter(
            AccountJpaRepository repository,
            AccountPersistenceMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Account> findById(UUID accountId) {
        return repository.findById(accountId)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(Account account) {
        AccountJpaEntity entity = repository.findById(account.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Account disappeared during transaction"
                ));

        if (entity.getVersion() == null
                || entity.getVersion().longValue() != account.getVersion()) {
            throw new OptimisticLockingFailureException(
                    "Account version changed before update"
            );
        }

        entity.setBalance(account.getBalance());
        entity.setUpdatedAt(account.getUpdatedAt());

        /*
         * Entity mevcut transaction içinde managed durumda.
         * Hibernate dirty checking ve @Version kontrolünü commit/flush
         * sırasında uygulayacak; ayrıca save() çağırmaya gerek yok.
         */
    }
}