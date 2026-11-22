package com.texos.texos_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MenuImportRequest {

    @Valid
    @Size(min = 1, max = 500)
    private List<MenuImportItem> items;

    private Long defaultCategoryId;

    @Size(max = 100)
    private String defaultCategoryName;
}
