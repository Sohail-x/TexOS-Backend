package com.texos.texos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestaurantPhonePeConfigRequest {
    @NotBlank
    @Size(max = 100)
    private String merchantId;

    @NotBlank
    @Size(max = 150)
    private String clientId;

    @Size(max = 200)
    private String clientSecret;

    @NotNull
    @Positive
    private Integer clientVersion;

    @NotBlank
    @Pattern(regexp = "^(SANDBOX|PRODUCTION)$")
    private String environment;

    @Size(max = 150)
    private String webhookKeyId;

    @Size(max = 200)
    private String webhookSecret;
}
