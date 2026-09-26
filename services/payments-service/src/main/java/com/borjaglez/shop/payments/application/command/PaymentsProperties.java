package com.borjaglez.shop.payments.application.command;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Payment settings.
 *
 * @param cardLimit highest amount a card can pay; above it the payment is declined, which lets the
 *     demo show a rejected checkout on purpose
 */
@Validated
@ConfigurationProperties("shop.payments")
public record PaymentsProperties(@DefaultValue("300") @NotNull @Positive BigDecimal cardLimit) {}
