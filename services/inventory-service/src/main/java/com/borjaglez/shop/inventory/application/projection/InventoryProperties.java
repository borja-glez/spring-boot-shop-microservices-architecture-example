package com.borjaglez.shop.inventory.application.projection;

import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Inventory settings.
 *
 * @param initialStock units a product starts with when the catalog publishes it
 */
@Validated
@ConfigurationProperties("shop.inventory")
public record InventoryProperties(@DefaultValue("25") @PositiveOrZero int initialStock) {}
