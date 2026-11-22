package com.texos.texos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileUpdateRequest {

    @NotBlank
    @Size(max = 100)
    private String fullName;

    @NotBlank
    @Size(max = 20)
    private String phone;

    @Size(max = 12)
    private String avatarIcon;

    @Size(max = 2_800_000)
    private String avatarImage;
}
