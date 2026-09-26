package com.borjaglez.shop.eskit;

import java.io.Serializable;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Marks a message as applied by a consumer, so redeliveries are ignored. */
@Entity
@Table(name = "processed_message")
@IdClass(ProcessedMessage.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedMessage {

  @Id
  @Column(length = 100)
  private String consumer;

  @Id
  @Column(name = "message_id", length = 80)
  private String messageId;

  @Column(name = "processed_at", nullable = false)
  private OffsetDateTime processedAt;

  ProcessedMessage(String consumer, String messageId, OffsetDateTime processedAt) {
    this.consumer = consumer;
    this.messageId = messageId;
    this.processedAt = processedAt;
  }

  /** Composite identifier. */
  public record Key(String consumer, String messageId) implements Serializable {
    public Key() {
      this(null, null);
    }
  }
}
