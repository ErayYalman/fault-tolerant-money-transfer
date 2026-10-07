package com.fintech.transfer.presentation.mapper;

import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.port.in.InitiateTransferUseCase;
import com.fintech.transfer.presentation.dto.Request.CreateTransferRequest;
import com.fintech.transfer.presentation.dto.Response.TransferResponse;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;  

@Mapper(componentModel = "spring")
public interface TransferApiMapper {

    InitiateTransferUseCase.InitiateTransferCommand toCommand(
            CreateTransferRequest request
    );

    @Mapping(target = "amount", source = "money.amount")
    @Mapping(target = "currency", source = "money.currency")
    TransferResponse toResponse(Transfer transfer);
}