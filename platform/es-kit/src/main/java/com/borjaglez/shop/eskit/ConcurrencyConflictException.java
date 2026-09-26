package com.borjaglez.shop.eskit;

import com.borjaglez.shop.support.error.ConflictException;

/** Another writer appended to the stream since it was read. The caller may reload and retry. */
public class ConcurrencyConflictException extends ConflictException {

  public ConcurrencyConflictException(String streamType, String streamId, long expectedVersion) {
    super(
        "concurrent-modification",
        streamType
            + " "
            + streamId
            + " was modified by another request (expected version "
            + expectedVersion
            + "). Reload it and try again.");
  }
}
