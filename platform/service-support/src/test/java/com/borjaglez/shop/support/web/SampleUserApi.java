package com.borjaglez.shop.support.web;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.borjaglez.shop.support.web.SampleController.CreateThing;

/**
 * One operation declared as the interfaces generated from the contracts declare theirs: the
 * mapping, the media types and the constrained {@code X-Shop-User} header live on the interface,
 * and {@link SampleUserApiController} only implements it.
 */
interface SampleUserApi {

  @PostMapping(
      value = "/api-user/{id}",
      consumes = {"application/json"},
      produces = {"application/json"})
  ResponseEntity<Map<String, Object>> renameThing(
      @NotNull
          @Pattern(regexp = "^[A-Za-z0-9._@-]{1,64}$")
          @RequestHeader(value = "X-Shop-User", required = true)
          String xShopUser,
      @PathVariable("id") UUID id,
      @Valid @RequestBody CreateThing body);
}
