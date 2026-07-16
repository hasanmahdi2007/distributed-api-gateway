package com.hasan.gateway.dtos;
import java.util.UUID;

// This is strictly for data going OUT.
public record NewClientResponse(UUID clientId, String apiKey) {
}