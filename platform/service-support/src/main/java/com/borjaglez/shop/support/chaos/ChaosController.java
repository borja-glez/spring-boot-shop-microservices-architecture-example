package com.borjaglez.shop.support.chaos;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.shop.support.error.NotFoundException;

/**
 * Demo-only endpoint to inject faults, at {@code /api/<shop.chaos.service>/chaos}. It answers only
 * when the service started with {@code shop.chaos.enabled=true}; otherwise every request gets a 404
 * and the faults cannot be turned on. The switch is read at startup rather than used as a bean
 * condition: a native image fixes its conditions at build time.
 */
@RestController
@RequestMapping("/api/${shop.chaos.service}/chaos")
public class ChaosController {

  public record FaultView(String key, String description, ChaosFault.Kind kind, long value) {

    static FaultView of(ChaosFault fault) {
      return new FaultView(fault.key(), fault.description(), fault.kind(), fault.value());
    }
  }

  public record FaultChange(long value) {}

  private final Chaos chaos;
  private final boolean enabled;

  public ChaosController(Chaos chaos, boolean enabled) {
    this.chaos = chaos;
    this.enabled = enabled;
  }

  private void requireEnabled() {
    if (!enabled) {
      throw new NotFoundException(
          "chaos-disabled",
          "Fault injection is off; start the service with SHOP_CHAOS_ENABLED=true");
    }
  }

  @GetMapping
  public List<FaultView> faults() {
    requireEnabled();
    return chaos.faults().stream().map(FaultView::of).toList();
  }

  @PutMapping("/{key}")
  public FaultView set(@PathVariable String key, @RequestBody FaultChange change) {
    requireEnabled();
    return FaultView.of(chaos.set(key, change.value()));
  }

  @DeleteMapping
  public ResponseEntity<Void> reset() {
    requireEnabled();
    chaos.reset();
    return ResponseEntity.noContent().build();
  }
}
