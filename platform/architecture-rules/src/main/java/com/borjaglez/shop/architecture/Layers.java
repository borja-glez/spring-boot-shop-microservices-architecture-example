package com.borjaglez.shop.architecture;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Where a class lives in the shop: which service and which layer of it.
 *
 * <p>A class belongs to a service when it is in {@code com.borjaglez.shop.<service>..} and {@code
 * <service>} is not one of the platform packages. The layer is the sub-package of the service, such
 * as {@code domain} or {@code infrastructure.in.rest}. The architecture fixtures, laid out as
 * {@code com.borjaglez.shop.fixture.good.<service>..} and {@code
 * com.borjaglez.shop.fixture.bad.<service>..}, are services as well.
 *
 * <p>Framework types are always referred to by name, so this module needs no framework on its
 * classpath.
 */
final class Layers {

  static final String KERNEL = "com.borjaglez.shop.kernel";
  static final String CONTRACTS = "com.borjaglez.shop.contracts";

  private static final Pattern SHOP =
      Pattern.compile("com[.]borjaglez[.]shop[.](?:fixture[.](?:good|bad)[.])?([^.]+)(?:[.](.*))?");
  private static final Pattern ADAPTER =
      Pattern.compile("infrastructure[.](in|out)[.]([^.]+)(?:[.].*)?");
  private static final Set<String> PLATFORM =
      Set.of("kernel", "contracts", "support", "eskit", "testsupport", "architecture");

  private Layers() {}

  /** A service and the path of a class inside it ({@code ""} for its root package). */
  record Location(String service, String path) {}

  static Optional<Location> locate(JavaClass javaClass) {
    Matcher matcher = SHOP.matcher(javaClass.getPackageName());
    if (!matcher.matches() || PLATFORM.contains(matcher.group(1))) {
      return Optional.empty();
    }
    return Optional.of(
        new Location(matcher.group(1), Optional.ofNullable(matcher.group(2)).orElse("")));
  }

  static boolean inLayer(JavaClass javaClass, String layer) {
    return locate(javaClass).map(location -> isWithin(location.path(), layer)).orElse(false);
  }

  /** Whether both classes are in the same service and the second one is in the given layer. */
  static boolean inLayerOfSameService(JavaClass origin, JavaClass target, String layer) {
    return locate(target)
        .map(
            location ->
                isWithin(location.path(), layer)
                    && locate(origin)
                        .map(Location::service)
                        .equals(Optional.of(location.service())))
        .orElse(false);
  }

  /** The adapter of a class, such as {@code orders/in.rest}, when it is in one. */
  static Optional<String> adapterOf(JavaClass javaClass) {
    return locate(javaClass)
        .flatMap(
            location -> {
              Matcher matcher = ADAPTER.matcher(location.path());
              return matcher.matches()
                  ? Optional.of(
                      location.service() + "/" + matcher.group(1) + "." + matcher.group(2))
                  : Optional.empty();
            });
  }

  static boolean inKernel(JavaClass javaClass) {
    return isWithin(javaClass.getPackageName(), KERNEL);
  }

  static boolean inContracts(JavaClass javaClass) {
    return isWithin(javaClass.getPackageName(), CONTRACTS);
  }

  static boolean inJava(JavaClass javaClass) {
    return isWithin(javaClass.getPackageName(), "java");
  }

  static DescribedPredicate<JavaClass> reside(String layer) {
    return DescribedPredicate.describe(
        "reside in the " + layer + " layer of a service", javaClass -> inLayer(javaClass, layer));
  }

  static DescribedPredicate<JavaClass> areInAService() {
    return DescribedPredicate.describe(
        "belong to a service", javaClass -> locate(javaClass).isPresent());
  }

  static DescribedPredicate<JavaClass> areInAnAdapter() {
    return DescribedPredicate.describe(
        "belong to an inbound or outbound adapter", javaClass -> adapterOf(javaClass).isPresent());
  }

  private static boolean isWithin(String path, String prefix) {
    return path.equals(prefix) || path.startsWith(prefix + ".");
  }

  /** Every dependency of the class must go to a target the predicate allows. */
  static ArchCondition<JavaClass> onlyDependOn(
      String description, BiPredicate<JavaClass, JavaClass> allowed) {
    return new ArchCondition<>("only depend on " + description) {
      @Override
      public void check(JavaClass origin, ConditionEvents events) {
        List<Dependency> forbidden =
            origin.getDirectDependenciesFromSelf().stream()
                .filter(dependency -> !allowed.test(origin, dependency.getTargetClass()))
                .toList();
        forbidden.forEach(
            dependency ->
                events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription())));
        if (forbidden.isEmpty()) {
          events.add(SimpleConditionEvent.satisfied(origin, origin.getName() + " is fine"));
        }
      }
    };
  }

  /** No dependency of the class may go to a target the predicate forbids. */
  static ArchCondition<JavaClass> notDependOn(
      String description, BiPredicate<JavaClass, JavaClass> forbidden) {
    return onlyDependOn("anything but " + description, forbidden.negate());
  }

  /** The class itself must satisfy the predicate. */
  static ArchCondition<JavaClass> satisfy(String description, Predicate<JavaClass> predicate) {
    return new ArchCondition<>(description) {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        boolean satisfied = predicate.test(javaClass);
        events.add(
            new SimpleConditionEvent(
                javaClass,
                satisfied,
                javaClass.getName() + (satisfied ? " does " : " does not ") + description));
      }
    };
  }
}
