package com.texos.texos_backend.service;

import com.google.zxing.WriterException;

import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantTable;

import com.texos.texos_backend.exception.DuplicateResourceException;
import com.texos.texos_backend.exception.InvalidRequestException;
import com.texos.texos_backend.exception.ResourceNotFoundException;

import com.texos.texos_backend.repository.RestaurantRepository;
import com.texos.texos_backend.repository.RestaurantTableRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantTableService {

    private final RestaurantTableRepository tableRepository;
    private final RestaurantRepository restaurantRepository;
    private final QrCodeService qrCodeService;

    @Value("${app.customer-base-url}")
    private String customerBaseUrl;

    @Transactional
    public RestaurantTable createTable(Long restaurantId, String tableNumber) {

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() ->
                    new ResourceNotFoundException("Restaurant not found")
                );

        if (!restaurant.isActive()) {
            throw new InvalidRequestException(
                "Cannot add table to an inactive restaurant"
            );
        }

        if (tableNumber == null || tableNumber.trim().isBlank()) {
            throw new InvalidRequestException("Table number is required");
        }

        String normalizedTableNumber = tableNumber.trim();

        if (tableRepository.existsByRestaurantIdAndTableNumber(
                restaurantId, normalizedTableNumber)) {

            throw new DuplicateResourceException(
                "Table number already exists in this restaurant"
            );
        }

        RestaurantTable table = new RestaurantTable();
        table.setTableNumber(normalizedTableNumber);
        table.setRestaurant(restaurant);

        String qrToken = UUID.randomUUID().toString();
        table.setQrToken(qrToken);

        String customerUrl = customerBaseUrl.replaceAll("/+$", "")
                + "/menu/"
                + qrToken;

        try {
            byte[] qrImage = qrCodeService.generateQrCode(customerUrl);
            table.setQrImage(qrImage);

        } catch (WriterException | IOException e) {
            throw new IllegalStateException(
                "Failed to generate table QR code", e
            );
        }

        return tableRepository.save(table);
    }

    @Transactional
    public List<RestaurantTable> getAllTables(Long restaurantId) {

        if (!restaurantRepository.existsById(restaurantId)) {
            throw new ResourceNotFoundException("Restaurant not found");
        }

        List<RestaurantTable> tables = tableRepository.findAllByRestaurantId(restaurantId);
        tables.forEach(this::ensureQrImage);
        return tables;
    }

    public RestaurantTable getTableById(Long restaurantId, Long tableId) {

        return tableRepository.findByIdAndRestaurantId(tableId, restaurantId)
                .orElseThrow(() ->
                    new ResourceNotFoundException("Table not found")
                );
    }


@Transactional
public byte[] getTableQrImage(Long restaurantId, Long tableId) {

    RestaurantTable table = getTableById(restaurantId, tableId);

    if (!table.isActive()) {
        throw new InvalidRequestException(
            "Cannot download QR code for an inactive table"
        );
    }

    return ensureQrImage(table);
}

private byte[] ensureQrImage(RestaurantTable table) {
    if (table.getQrImage() != null && table.getQrImage().length > 0) {
        return table.getQrImage();
    }

    String customerUrl = customerBaseUrl.replaceAll("/+$", "")
            + "/menu/"
            + table.getQrToken();

    try {
        byte[] qrImage = qrCodeService.generateQrCode(customerUrl);

        table.setQrImage(qrImage);

        tableRepository.save(table);

        return qrImage;

    } catch (WriterException | IOException e) {
        throw new IllegalStateException(
            "Failed to regenerate table QR code", e
        );
    }
}


    @Transactional
    public RestaurantTable updateTableStatus(
            Long restaurantId,
            Long tableId,
            boolean active
    ) {

        RestaurantTable table = getTableById(restaurantId, tableId);

        table.setActive(active);

        return tableRepository.save(table);
    }
}
