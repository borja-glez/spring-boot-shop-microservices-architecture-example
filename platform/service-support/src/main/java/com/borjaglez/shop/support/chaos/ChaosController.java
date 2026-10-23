package com.borjaglez.shop.support.chaos;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.shop.kernel.error.NotFoundException;
import com.borjaglez.shop.support.chaos.rest.v1.api.ChaosApi;
import com.borjaglez.shop.support.chaos.rest.v1.model.FaultChangeDto;
import com.borjaglez.shop.support.chaos.rest.v1.model.FaultDto;
import com.borjaglez.shop.support.chaos.rest.v1.model.FaultKindDto;

/**
 * Demo-only endpoint to inject faults, at {@code /api/v1/chaos/<shop.chaos.service>}: under a
 * platform prefix, so no route of the service (such as {@code /api/v1/orders/{id}}) takes it. It
 * answers only when the service started with {@code shop.chaos.enabled=true}; otherwise every
 * request gets a 404 and the faults cannot be turned on, and so does a request for another service.
 * The switch is read at startup rather than used as a bean condition: a native image fixes its
 * conditions at build time.
 */
@RestController
public class ChaosController implements ChaosApi {

  private final Chaos chaos;
  private final String configuredService;
  private final boolean enabled;

  public ChaosController(Chaos chaos, String service, boolean enabled) {
    this.chaos = chaos;
    this.configuredService = service;
    this.enabled = enabled;
  }

  private void requireEnabled(String requestedService) {
    if (!enabled || !configuredService.equals(requestedService)) {
      throw new NotFoundException(
          "chaos-disabled",
          "Fault injection is off; start the service with SHOP_CHAOS_ENABLED=true");
    }
  }

  @Override
  public ResponseEntity<List<FaultDto>> listFaults(String service) {
    requireEnabled(service);
    return ResponseEntity.ok(chaos.faults().stream().map(ChaosController::toFault).toList());
  }

  @Override
  public ResponseEntity<FaultDto> setFault(String service, String key, FaultChangeDto faultChange) {
    requireEnabled(service);
    return ResponseEntity.ok(toFault(chaos.set(key, faultChange.getValue())));
  }

  @Override
  public ResponseEntity<Void> resetFaults(String service) {
    requireEnabled(service);
    chaos.reset();
    return ResponseEntity.noContent().build();
  }

  private static FaultDto toFault(ChaosFault fault) {
    return new FaultDto(
        fault.key(), fault.description(), FaultKindDto.valueOf(fault.kind().name()), fault.value());
  }
}
