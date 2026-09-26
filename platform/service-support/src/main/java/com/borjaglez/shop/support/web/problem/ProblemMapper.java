package com.borjaglez.shop.support.web.problem;

import java.util.Optional;

/**
 * Translates one family of exceptions into an HTTP problem.
 *
 * <p>Mappers are consulted for every exception in the cause chain, outermost first, so wrappers
 * such as the command bus execution exception or Spring's data access exceptions do not hide the
 * real reason. Exceptions that no mapper claims become a 500 {@code internal-error}.
 */
@FunctionalInterface
public interface ProblemMapper {

  Optional<MappedProblem> map(Throwable exception);
}
