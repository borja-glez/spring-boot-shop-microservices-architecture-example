package com.borjaglez.shop.catalog.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A browsing category. Products can belong to several. */
@Entity
@Table(name = "category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 80)
  private String slug;

  @Column(nullable = false, length = 120)
  private String name;

  public Category(UUID id, String slug, String name) {
    this.id = id;
    this.slug = slug;
    this.name = name;
  }
}
