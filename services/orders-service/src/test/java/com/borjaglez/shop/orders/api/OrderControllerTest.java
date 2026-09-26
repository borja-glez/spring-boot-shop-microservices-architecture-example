package com.borjaglez.shop.orders.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.shop.contracts.orders.OrderCancelled;
import com.borjaglez.shop.orders.application.command.CancelOrderCommand;
import com.borjaglez.shop.orders.application.command.PlaceOrderCommand;
import com.borjaglez.shop.orders.application.query.GetOrderHistoryQuery;
import com.borjaglez.shop.orders.application.query.ListMyOrdersQuery;
import com.borjaglez.shop.orders.application.query.OrderViews.HistoryEntry;
import com.borjaglez.shop.orders.application.query.OrderViews.OrderState;
import com.borjaglez.shop.orders.application.query.OrderViews.OrderSummary;
import com.borjaglez.shop.orders.application.query.OrderViews.StoredEventView;
import com.borjaglez.shop.orders.application.query.SearchEventStoreQuery;
import com.borjaglez.shop.orders.domain.OrderStatus;
import com.borjaglez.specrepository.core.PredicateCondition;
import com.borjaglez.specrepository.http.spring.HttpFilterAutoConfiguration;

@WebMvcTest(OrderController.class)
@ImportAutoConfiguration(HttpFilterAutoConfiguration.class)
class OrderControllerTest {

  private static final UUID ORDER = UUID.fromString("5b0f6c2e-1d3a-4c55-9e0b-7a1f2c3d4e5f");
  private static final UUID PRODUCT = UUID.fromString("8a9c2c1e-6f59-4a44-9d4b-0f6f4e3f1c11");
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-25T10:00:00Z");

  @Autowired MockMvc mvc;
  @MockitoBean QueryBus queries;
  @MockitoBean CommandBus commands;

  @Test
  void myOrdersAreFilteredForTheCurrentUser() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(
                    new OrderSummary(
                        ORDER, OrderStatus.PLACED, new BigDecimal("9.50"), "EUR", 2, NOW, NOW))));

    mvc.perform(
            get("/api/orders")
                .header("X-Shop-User", "cliente-lucia")
                .param("filter", "status:eq:PLACED")
                .param("sort", "total,desc")
                .param("page", "1")
                .param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].orderId").value(ORDER.toString()))
        .andExpect(jsonPath("$.content[0].status").value("PLACED"))
        .andExpect(jsonPath("$.totalElements").value(1));

    ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
    verify(queries).ask(captor.capture());
    ListMyOrdersQuery query = (ListMyOrdersQuery) captor.getValue();
    assertThat(query.getCustomerId()).isEqualTo("cliente-lucia");
    assertThat(query.getPlan().rootCondition().conditions())
        .singleElement()
        .isInstanceOfSatisfying(
            PredicateCondition.class, c -> assertThat(c.field()).isEqualTo("status"));
    assertThat(query.getPlan().sort()).isEqualTo(Sort.by(Sort.Direction.DESC, "total"));
    assertThat(query.getPageable()).isEqualTo(PageRequest.of(1, 5));
  }

  @Test
  void ordersNeedAUser() throws Exception {
    mvc.perform(get("/api/orders"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("missing-user"));
    verifyNoInteractions(queries);
  }

  @Test
  void placingAnOrderSendsTheCartItems() throws Exception {
    when(commands.dispatchAndReceive(any(Command.class))).thenReturn(ORDER);

    mvc.perform(
            post("/api/orders")
                .header("X-Shop-User", "cliente-lucia")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"items": [{"productId": "%s", "quantity": 3}]}
                    """
                        .formatted(PRODUCT)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(ORDER.toString()));

    ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
    verify(commands).dispatchAndReceive(captor.capture());
    PlaceOrderCommand command = (PlaceOrderCommand) captor.getValue();
    assertThat(command.getCustomerId()).isEqualTo("cliente-lucia");
    assertThat(command.getItems()).containsExactly(new PlaceOrderCommand.Item(PRODUCT, 3));
  }

  @Test
  void anInvalidCartIsRejectedBeforeReachingTheBus() throws Exception {
    mvc.perform(
            post("/api/orders")
                .header("X-Shop-User", "cliente-lucia")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"items": [{"productId": "%s", "quantity": 0}]}
                    """
                        .formatted(PRODUCT)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"));

    mvc.perform(
            post("/api/orders")
                .header("X-Shop-User", "cliente-lucia")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\": []}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(commands);
  }

  @Test
  void cancellingDoesNotNeedABody() throws Exception {
    mvc.perform(post("/api/orders/{id}/cancel", ORDER).header("X-Shop-User", "cliente-lucia"))
        .andExpect(status().isNoContent());

    ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
    verify(commands).dispatchAndWait(captor.capture());
    assertThat(captor.getValue())
        .isInstanceOfSatisfying(
            CancelOrderCommand.class,
            c -> {
              assertThat(c.getOrderId()).isEqualTo(ORDER);
              assertThat(c.getCustomerId()).isEqualTo("cliente-lucia");
              assertThat(c.getReason()).isNull();
            });
  }

  @Test
  void cancellingPassesTheReason() throws Exception {
    mvc.perform(
            post("/api/orders/{id}/cancel", ORDER)
                .header("X-Shop-User", "cliente-lucia")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Me equivoqué\"}"))
        .andExpect(status().isNoContent());

    ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
    verify(commands).dispatchAndWait(captor.capture());
    assertThat(((CancelOrderCommand) captor.getValue()).getReason()).isEqualTo("Me equivoqué");
  }

  @Test
  void theHistoryShowsEachEventWithItsPayload() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenReturn(
            List.of(
                new HistoryEntry(
                    2,
                    "shop.orders.1.event.order.order-cancelled",
                    NOW,
                    null,
                    new OrderCancelled(ORDER, "Me equivoqué", "cliente-lucia"),
                    new OrderState(OrderStatus.CANCELLED, new BigDecimal("9.50"), "EUR", 2))));

    mvc.perform(get("/api/orders/{id}/history", ORDER).header("X-Shop-User", "cliente-lucia"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].version").value(2))
        .andExpect(jsonPath("$[0].event.reason").value("Me equivoqué"))
        .andExpect(jsonPath("$[0].stateAfter.status").value("CANCELLED"));

    ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
    verify(queries).ask(captor.capture());
    assertThat(((GetOrderHistoryQuery) captor.getValue()).getCustomerId())
        .isEqualTo("cliente-lucia");
  }

  @Test
  void theExplorerReturnsPayloadsAsJson() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(
                    new StoredEventView(
                        7,
                        UUID.randomUUID(),
                        "order",
                        ORDER.toString(),
                        1L,
                        "shop.orders.1.event.order.order-placed",
                        NOW,
                        null,
                        0,
                        null,
                        "{\"orderId\": \"%s\"}".formatted(ORDER),
                        "{}"))));

    mvc.perform(
            get("/api/orders/events")
                .param("filter", "publishedAt:isnull")
                .param("filter", "streamType:eq:order"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].payload.orderId").value(ORDER.toString()))
        .andExpect(jsonPath("$.content[0].globalPosition").value(7));

    ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
    verify(queries).ask(captor.capture());
    assertThat(((SearchEventStoreQuery) captor.getValue()).getPlan().rootCondition().conditions())
        .hasSize(2);
  }
}
