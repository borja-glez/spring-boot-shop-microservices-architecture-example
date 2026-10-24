package com.borjaglez.shop.fixture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/** Annotates a package-info of a fixture. */
@Target(ElementType.PACKAGE)
public @interface PackageMarker {}
