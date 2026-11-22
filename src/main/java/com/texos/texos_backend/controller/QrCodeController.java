package com.texos.texos_backend.controller;

import com.texos.texos_backend.service.RestaurantTableService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/tables")
@RequiredArgsConstructor
public class QrCodeController {

    private final RestaurantTableService tableService;

    @GetMapping("/{tableId}/qr")
    public ResponseEntity<byte[]> downloadTableQrCode(
            @PathVariable Long restaurantId,
            @PathVariable Long tableId
    ) {

        byte[] qrImage = tableService.getTableQrImage(
                restaurantId, tableId
        );

        return ResponseEntity.ok()
                .header(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=table-"
                            + tableId
                            + "-qr.png"
                )
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(qrImage.length)
                .body(qrImage);
    }
}
