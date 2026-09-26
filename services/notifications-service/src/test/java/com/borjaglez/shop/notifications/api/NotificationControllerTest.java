package com.borjaglez.shop.notifications.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.notifications.application.NotificationNotFoundException;
import com.borjaglez.shop.notifications.application.NotificationQueries.NotificationView;
import com.borjaglez.shop.notifications.domain.NotificationKind;

@WebMvcTest(NotificationController.class)
@Import(SseNotificationHub.class)
class NotificationControllerTest {

  @Autowired MockMvc mvc;
  @MockitoBean QueryBus queries;
  @MockitoBean CommandBus commands;

  @Test
  void theCustomerSeesTheirNotices() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(
                    new NotificationView(
                        "e-1",
                        UUID.randomUUID(),
                        NotificationKind.ORDER_CONFIRMED,
                        "Order confirmed",
                        "Your order of €10.00 is confirmed.",
                        OffsetDateTime.parse("2026-09-26T10:00:00Z"),
                        false))));

    mvc.perform(get("/api/notifications").header("X-Shop-User", "cliente-lucia"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].title").value("Order confirmed"))
        .andExpect(jsonPath("$.content[0].occurredAt").value("2026-09-26T10:00:00Z"))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void noticesNeedAUser() throws Exception {
    mvc.perform(get("/api/notifications"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("missing-user"));
  }

  @Test
  void anUnknownNoticeIsNotFound() throws Exception {
    doThrow(new NotificationNotFoundException("e-9"))
        .when(commands)
        .dispatchAndWait(any(Command.class));

    mvc.perform(post("/api/notifications/e-9/read").header("X-Shop-User", "cliente-lucia"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("notification-not-found"));
  }

  @Test
  void theUnreadCountIsAnObject() throws Exception {
    when(queries.ask(any(Query.class))).thenReturn(3L);

    mvc.perform(get("/api/notifications/unread-count").header("X-Shop-User", "cliente-lucia"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unread").value(3));
  }

  @Test
  void theStreamOpensAsServerSentEvents() throws Exception {
    mvc.perform(get("/api/notifications/stream").param("user", "cliente-lucia"))
        .andExpect(request().asyncStarted());
    // The stream only speaks text/event-stream, so the problem has no JSON body there.
    mvc.perform(get("/api/notifications/stream")).andExpect(status().isBadRequest());
  }
}
