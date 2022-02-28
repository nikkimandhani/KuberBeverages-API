package com.kuber.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PaymentMetricsResponse {
    private int totalAmount;

    private String paymentMode;
}