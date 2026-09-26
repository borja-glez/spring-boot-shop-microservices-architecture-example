package com.borjaglez.shop.reporting.application.rebuild;

/** Control over the stream of integration events that feeds the projections. */
public interface EventReplay {

  /** Stops consuming. Returns once no event is being processed. */
  void pause();

  /** Makes the next consumption start from the first event still in the stream. */
  void rewind();

  /** Consumes again. */
  void resume();
}
