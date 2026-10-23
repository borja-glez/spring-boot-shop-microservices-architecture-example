package com.borjaglez.shop.support.web;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.shop.support.web.SampleController.CreateThing;

/**
 * Implements {@link SampleUserApi} with no annotation of its own, as a contract-first controller.
 */
@RestController
class SampleUserApiController implements SampleUserApi {

  @Override
  public ResponseEntity<Map<String, Object>> renameThing(
      String xShopUser, UUID id, CreateThing body) {
    return ResponseEntity.ok(Map.of("user", xShopUser, "id", id, "name", body.name()));
  }
}
