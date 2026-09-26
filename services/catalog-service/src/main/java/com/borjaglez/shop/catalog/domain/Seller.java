package com.borjaglez.shop.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A merchant that sells products. Its {@link #getId() id} is the user id sent in the {@code
 * X-Shop-User} header. The email is private data: it must never be filterable or sortable from the
 * public API.
 */
@Entity
@Table(name = "seller")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seller {

  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "display_name", nullable = false, length = 120)
  private String displayName;

  @Column(nullable = false, length = 160)
  private String email;

  @Column(nullable = false, length = 80)
  private String city;

  public Seller(String id, String displayName, String email, String city) {
    this.id = id;
    this.displayName = displayName;
    this.email = email;
    this.city = city;
  }
}
