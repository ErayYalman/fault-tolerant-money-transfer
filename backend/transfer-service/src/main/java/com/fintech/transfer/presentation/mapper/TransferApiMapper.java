package com.fintech.transfer.presentation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.port.in.InitiateTransferUseCase;
import com.fintech.transfer.presentation.dto.Request.CreateTransferRequest;
import com.fintech.transfer.presentation.dto.Response.TransferResponse;

@Mapper(componentModel = "spring")
public interface TransferApiMapper {

    @Mapping(target = "ownerSubject", source = "ownerSubject")
    InitiateTransferUseCase.InitiateTransferCommand toCommand(
            CreateTransferRequest request,
            String ownerSubject);

    @Mapping(target = "transferId", source = "id")
    @Mapping(target = "amount", source = "money.amount")
    @Mapping(target = "currency", source = "money.currency")
    TransferResponse toResponse(Transfer transfer);
}