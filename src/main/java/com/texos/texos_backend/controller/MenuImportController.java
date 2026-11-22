package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.MenuImportItem;
import com.texos.texos_backend.dto.MenuImportRequest;
import com.texos.texos_backend.service.MenuImportService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/menu/import")
public class MenuImportController {

    private final MenuImportService menuImportService;

    public MenuImportController(MenuImportService menuImportService) {
        this.menuImportService = menuImportService;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<MenuImportItem> preview(
            @PathVariable Long restaurantId,
            @RequestParam("file") MultipartFile file) {
        return menuImportService.parseFile(file);
    }

    @PostMapping
    public Map<String, Object> importItems(
            @PathVariable Long restaurantId,
            @Valid @RequestBody MenuImportRequest request) {
        int count = menuImportService.importItems(restaurantId, request);
        return Map.of("importedCount", count);
    }
}
