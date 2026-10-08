package com.fintech.transfer.infrastructure.persistence.mapper;

import com.fintech.transfer.domain.model.SagaState;
import com.fintech.transfer.infrastructure.persistence.entity.SagaStateJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SagaStatePersistenceMapper {

    SagaStateJpaEntity toEntity(SagaState sagaState);

    default SagaState toDomain(
            SagaStateJpaEntity entity
    ) {
        return SagaState.rehydrate(
                entity.getId(),
                entity.getTransferId(),
                entity.getCurrentStep(),
                entity.getPayload(),
                entity.getUpdatedAt()
        );
    }
}