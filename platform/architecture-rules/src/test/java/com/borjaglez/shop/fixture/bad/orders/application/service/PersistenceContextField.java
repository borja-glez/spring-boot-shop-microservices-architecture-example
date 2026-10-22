package com.borjaglez.shop.fixture.bad.orders.application.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public class PersistenceContextField {

  @PersistenceContext private EntityManager entityManager;
}
