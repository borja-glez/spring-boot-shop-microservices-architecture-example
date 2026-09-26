package com.borjaglez.shop.eskit;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One row of the event store: an event of a stream, or an outbox message when unversioned. */
@Entity
@Table(name = "event_store")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredEvent {

  private static final int MAX_ERROR = 1000;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "global_position")
  private Long globalPosition;

  @Column(name = "event_id", nullable = false, unique = true)
  private UUID eventId;

  @Column(name = "stream_type", nullable = false, length = 60)
  private String streamType;

  @Column(name = "stream_id", nullable = false, length = 80)
  private String streamId;

  /** Position in the stream, from 1. {@code null} for outbox messages. */
  private Long version;

  @Column(name = "event_type", nullable = false, length = 200)
  private String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String payload;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String metadata;

  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt;

  @Column(name = "published_at")
  private OffsetDateTime publishedAt;

  @Column(name = "publish_attempts", nullable = false)
  private int publishAttempts;

  @Column(name = "last_error", length = MAX_ERROR)
  private String lastError;

  StoredEvent(
      UUID eventId,
      String streamType,
      String streamId,
      Long version,
      String eventType,
      String payload,
      String metadata,
      OffsetDateTime occurredAt) {
    this.eventId = eventId;
    this.streamType = streamType;
    this.streamId = streamId;
    this.version = version;
    this.eventType = eventType;
    this.payload = payload;
    this.metadata = metadata;
    this.occurredAt = occurredAt;
  }

  void markPublished(OffsetDateTime when) {
    publishedAt = when;
    lastError = null;
  }

  void markFailed(String error) {
    publishAttempts++;
    lastError =
        error == null || error.length() <= MAX_ERROR ? error : error.substring(0, MAX_ERROR);
  }
}
