package com.borjaglez.shop.reporting.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.borjaglez.shop.reporting.domain.ReportOrder;
import com.borjaglez.specrepository.http.spring.FilterableQuery;

/**
 * The filters every order report accepts: when the order was placed and its currency. Reports are
 * not sorted by the client; each one orders its rows itself.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@FilterableQuery(
    value = ReportOrder.class,
    filterableFields = {"placedAt", "placedDay", "currency"})
@interface OrderReportFilter {}
