package com.texos.texos_backend.service;

import com.texos.texos_backend.entity.*;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class OrderCalculationService {

    private static final BigDecimal HUNDRED =
            new BigDecimal("100");

    private static final int SCALE = 2;

    public Bill calculateBill(RestaurantOrder order) {

        Restaurant restaurant = order.getRestaurant();

        BigDecimal subtotal = BigDecimal.ZERO;

        for (OrderItem item : order.getItems()) {
            subtotal = subtotal.add(item.getLineTotal());
        }

        subtotal = round(subtotal);

        BigDecimal taxRate = restaurant.getTaxRate();

        BigDecimal serviceRate =
                restaurant.isServiceChargeEnabled()
                        ? restaurant.getServiceChargeRate()
                        : BigDecimal.ZERO;

        BigDecimal serviceCharge =
                round(subtotal.multiply(serviceRate)
                        .divide(HUNDRED, 4, RoundingMode.HALF_UP));

        BigDecimal taxAmount;

        if (restaurant.getMenuPriceTaxMode()
                == MenuPriceTaxMode.INCLUSIVE) {

            // Tax is already included in menu prices.
            taxAmount = subtotal.multiply(taxRate)
                    .divide(
                        HUNDRED.add(taxRate),
                        2,
                        RoundingMode.HALF_UP
                    );

        } else {

            // Tax is added separately.
            taxAmount = subtotal.multiply(taxRate)
                    .divide(
                        HUNDRED,
                        2,
                        RoundingMode.HALF_UP
                    );
        }

        taxAmount = round(taxAmount);

        // Service charge is added separately.
        // Tax is calculated on the menu subtotal only.
        BigDecimal totalAmount;

        if (restaurant.getMenuPriceTaxMode()
                == MenuPriceTaxMode.INCLUSIVE) {

            totalAmount = subtotal.add(serviceCharge);

        } else {

            totalAmount = subtotal
                    .add(taxAmount)
                    .add(serviceCharge);
        }

        totalAmount = round(totalAmount);
        BigDecimal rewardDiscount = order.getRewardDiscountAmount() == null
                ? BigDecimal.ZERO : order.getRewardDiscountAmount();
        totalAmount = round(totalAmount.subtract(rewardDiscount).max(BigDecimal.ZERO));

        Bill bill = new Bill();

        bill.setOrder(order);
        bill.setBillVersion(1);

        bill.setSubtotal(subtotal);

        bill.setTaxMode(restaurant.getMenuPriceTaxMode());
        bill.setTaxRate(taxRate);
        bill.setTaxAmount(taxAmount);

        bill.setServiceChargeEnabled(
                restaurant.isServiceChargeEnabled()
        );

        bill.setServiceChargeRate(serviceRate);
        bill.setServiceChargeAmount(serviceCharge);
        bill.setRewardDiscountAmount(rewardDiscount);

        bill.setTotalAmount(totalAmount);

        return bill;
    }

    public BigDecimal round(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.HALF_UP);
    }
}