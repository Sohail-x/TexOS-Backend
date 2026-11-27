
package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.PublicMenuResponse;
import com.texos.texos_backend.dto.PublicMenuImage;
import com.texos.texos_backend.service.CustomerMenuService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/public/menu")
@RequiredArgsConstructor
public class CustomerMenuController {

    private final CustomerMenuService customerMenuService;

    @GetMapping("/{qrToken}")
    public ResponseEntity<PublicMenuResponse> getCustomerMenu(
            @PathVariable String qrToken
    ) {

        PublicMenuResponse response =
                customerMenuService.getCustomerMenu(qrToken);

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "private, max-age=15")
                .body(response);
    }

    @GetMapping("/{qrToken}/items/{itemId}/image")
    public ResponseEntity<byte[]> getCustomerMenuImage(
            @PathVariable String qrToken,
            @PathVariable Long itemId
    ) {
        PublicMenuImage image = customerMenuService.getCustomerMenuImage(qrToken, itemId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(15, TimeUnit.SECONDS).cachePrivate())
                .contentType(MediaType.parseMediaType(image.contentType()))
                .body(image.bytes());
    }
}
