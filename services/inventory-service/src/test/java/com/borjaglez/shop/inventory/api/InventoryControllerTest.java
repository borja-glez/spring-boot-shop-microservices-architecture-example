package com.borjaglez.shop.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.borjaglez.shop.inventory.application.command.AdjustStockCommand;
import com.borjaglez.shop.inventory.application.query.InventoryQueries.LineView;
import com.borjaglez.shop.inventory.application.query.InventoryQueries.ReservationView;
import com.borjaglez.shop.inventory.application.query.InventoryQueries.SearchReservationsQuery;
import com.borjaglez.shop.inventory.application.query.InventoryQueries.SearchStockQuery;
import com.borjaglez.shop.inventory.application.query.InventoryQueries.StockView;
import com.borjaglez.shop.inventory.domain.ReservationStatus;
import com.borjaglez.specrepository.http.spring.HttpFilterAutoConfiguration;

@WebMvcTest(InventoryController.class)
@ImportAutoConfiguration(HttpFilterAutoConfiguration.class)
class InventoryControllerTest {

  private static final UUID PRODUCT = UUID.fromString("8a9c2c1e-6f59-4a44-9d4b-0f6f4e3f1c11");
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-25T10:00:00Z");

  @Autowired MockMvc mvc;
  @MockitoBean QueryBus queries;
  @MockitoBean CommandBus commands;

  @Test
  void stockIsSearchedWithHttpFilters() throws Exception {
    when(queries.ask(any(Query.class)))
        .thenReturn(
            new PageImpl<>(List.of(new StockView(PRODUCT, "CAF-001", "Café", 10, 4, 6, NOW))));

    mvc.perform(
            get("/api/inventory/stock")
                .param("filter", "onHand:lt:20")
                .param("sort", "onHand,asc")
                .param("page", "1")
                .param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].sku").value("CAF-001"))
        .andExpect(jsonPath("$.content[0].available").value(6));

    ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
    verify(queries).ask(captor.capture());
    SearchStockQuery query = (SearchStockQuery) captor.getValue();
    assertThat(query.getPlan().sort()).isEqualTo(Sort.by(Sort.Direction.ASC, "onHand"));
    assertThat(query.getPageable()).isEqualTo(PageRequest.of(1, 5));
  }

  @Test
  void reservationsAreListedWithTheirLines() throws Exception {
    UUID orderId = UUID.randomUUID();
    when(queries.ask(any(Query.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(
                    new ReservationView(
                        orderId,
                        ReservationStatus.RESERVED,
                        NOW,
                        null,
                        List.of(new LineView(PRODUCT, "CAF-001", 2))))));

    mvc.perform(get("/api/inventory/reservations").param("filter", "status:eq:RESERVED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].orderId").value(orderId.toString()))
        .andExpect(jsonPath("$.content[0].lines[0].quantity").value(2));

    ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
    verify(queries).ask(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(SearchReservationsQuery.class);
  }

  @Test
  void countingStockRecordsWhoDidIt() throws Exception {
    mvc.perform(
            put("/api/inventory/stock/{id}", PRODUCT)
                .header("X-Shop-User", "seller-ana")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"onHand\": 12}"))
        .andExpect(status().isNoContent());

    ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
    verify(commands).dispatchAndWait(captor.capture());
    AdjustStockCommand command = (AdjustStockCommand) captor.getValue();
    assertThat(command.getProductId()).isEqualTo(PRODUCT);
    assertThat(command.getOnHand()).isEqualTo(12);
    assertThat(command.getAdjustedBy()).isEqualTo("seller-ana");
  }

  @Test
  void aMissingCountIsRejected() throws Exception {
    mvc.perform(
            put("/api/inventory/stock/{id}", PRODUCT)
                .header("X-Shop-User", "seller-ana")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"));
    verifyNoInteractions(commands);
  }

  @Test
  void negativeStockIsRejected() throws Exception {
    mvc.perform(
            put("/api/inventory/stock/{id}", PRODUCT)
                .header("X-Shop-User", "seller-ana")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"onHand\": -1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-failed"));
    verifyNoInteractions(commands);
  }
}
