package com.borjaglez.shop.notifications.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.notifications.application.NotificationQueries.MarkReadCommand;
import com.borjaglez.shop.notifications.application.NotificationQueries.MyNotificationsQuery;
import com.borjaglez.shop.notifications.application.NotificationQueries.NotificationView;
import com.borjaglez.shop.notifications.application.NotificationQueries.UnreadCountQuery;

/**
 * The customer's notices. The list and the actions take the user from the {@code X-Shop-User}
 * header like every other service; the stream takes it as a parameter because the browser's {@code
 * EventSource} cannot send headers. There is no real authentication in the example.
 */
@RestController
@RequestMapping("/api/notifications")
class NotificationController {

  static final String USER_HEADER = "X-Shop-User";

  private final CommandBus commands;
  private final QueryBus queries;
  private final SseNotificationHub hub;

  NotificationController(CommandBus commands, QueryBus queries, SseNotificationHub hub) {
    this.commands = commands;
    this.queries = queries;
    this.hub = hub;
  }

  @GetMapping
  NotificationPage<NotificationView> mine(
      @RequestHeader(USER_HEADER) String customer,
      @RequestParam(defaultValue = "false") boolean unread,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Page<NotificationView> result =
        queries.ask(
            new MyNotificationsQuery(
                customer, unread, PageRequest.of(Math.max(page, 0), clamp(size))));
    return NotificationPage.of(result);
  }

  @GetMapping("/unread-count")
  UnreadCount unreadCount(@RequestHeader(USER_HEADER) String customer) {
    Long count = queries.ask(new UnreadCountQuery(customer));
    return new UnreadCount(count);
  }

  @PostMapping("/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void markRead(@RequestHeader(USER_HEADER) String customer, @PathVariable String id) {
    commands.dispatchAndWait(new MarkReadCommand(id, customer));
  }

  @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter stream(@RequestParam String user) {
    return hub.connect(user);
  }

  private static int clamp(int size) {
    return Math.min(Math.max(size, 1), 100);
  }

  record UnreadCount(long unread) {}
}
