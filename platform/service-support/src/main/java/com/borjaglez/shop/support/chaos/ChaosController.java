package com.borjaglez.shop.support.chaos;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.shop.kernel.error.NotFoundException;
import com.borjaglez.shop.support.chaos.rest.v1.api.ChaosApi;
import com.borjaglez.shop.support.chaos.rest.v1.model.Fault;
import com.borjaglez.shop.support.chaos.rest.v1.model.FaultChange;
import com.borjaglez.shop.support.chaos.rest.v1.model.FaultKind;

/**
 * Demo-only endpoint to inject faults, at {@code /api/v1/<shop.chaos.service>/chaos}. It answers
 * only when the service started with {@code shop.chaos.enabled=true}; otherwise every request gets
 * a 404 and the faults cannot be turned on, and so does a request for another service. The switch
 * is read at startup rather than used as a bean condition: a native image fixes its conditions at
 * build time.
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
  public ResponseEntity<List<Fault>> listFaults(String service) {
    requireEnabled(service);
    return ResponseEntity.ok(chaos.faults().stream().map(ChaosController::toFault).toList());
  }

  @Override
  public ResponseEntity<Fault> setFault(String service, String key, FaultChange faultChange) {
    requireEnabled(service);
    return ResponseEntity.ok(toFault(chaos.set(key, faultChange.getValue())));
  }

  @Override
  public ResponseEntity<Void> resetFaults(String service) {
    requireEnabled(service);
    chaos.reset();
    return ResponseEntity.noContent().build();
  }

  private static Fault toFault(ChaosFault fault) {
    return new Fault(
        fault.key(), fault.description(), FaultKind.valueOf(fault.kind().name()), fault.value());
  }
}
