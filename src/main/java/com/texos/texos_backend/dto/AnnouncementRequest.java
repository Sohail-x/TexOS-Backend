package com.texos.texos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnnouncementRequest {

    @NotBlank
    @Size(max = 160)
    private String title;

    @NotBlank
    @Size(max = 4000)
    private String message;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;
}
