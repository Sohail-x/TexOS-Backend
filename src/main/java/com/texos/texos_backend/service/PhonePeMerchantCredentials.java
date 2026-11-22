package com.texos.texos_backend.service;

public record PhonePeMerchantCredentials(
        String merchantId,
        String clientId,
        String clientSecret,
        int clientVersion,
        String environment) {
}
