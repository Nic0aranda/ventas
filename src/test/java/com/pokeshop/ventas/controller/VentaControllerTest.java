package com.pokeshop.ventas.controller;

import com.pokeshop.ventas.dto.SolicitudVentaDto;
import com.pokeshop.ventas.model.Venta;
import com.pokeshop.ventas.service.VentaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VentaController.class)
class VentaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VentaService ventaService;

    @Test
    void generarVenta_CuandoSolicitudValida_DeberiaRetornarVenta() throws Exception {
        // Arrange
        SolicitudVentaDto solicitud = new SolicitudVentaDto();
        solicitud.setIdUsuario(1L);
        
        SolicitudVentaDto.ItemProductoDto item = new SolicitudVentaDto.ItemProductoDto();
        item.setIdProducto(100L);
        item.setCantidad(2);
        solicitud.setProductos(Arrays.asList(item));
        
        Venta ventaMock = new Venta();
        ventaMock.setIdVenta(1L);
        ventaMock.setIdUsuario(1L);
        ventaMock.setEstado("COMPLETADA");
        
        when(ventaService.crearVenta(any(SolicitudVentaDto.class))).thenReturn(ventaMock);
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(solicitud)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idVenta").value(1L))
                .andExpect(jsonPath("$.idUsuario").value(1L))
                .andExpect(jsonPath("$.estado").value("COMPLETADA"));
    }

    @Test
    void generarVenta_CuandoServicioLanzaExcepcion_DeberiaRetornarBadRequest() throws Exception {
        // Arrange
        SolicitudVentaDto solicitud = new SolicitudVentaDto();
        solicitud.setIdUsuario(1L);
        solicitud.setProductos(Arrays.asList());
        
        when(ventaService.crearVenta(any(SolicitudVentaDto.class)))
            .thenThrow(new RuntimeException("Stock insuficiente"));
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(solicitud)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Stock insuficiente"));
    }

    @Test
    void generarVenta_CuandoSolicitudInvalida_DeberiaRetornarBadRequest() throws Exception {
        // Arrange - Solicitud sin usuario
        SolicitudVentaDto solicitud = new SolicitudVentaDto();
        // No se establece idUsuario
        
        // Act & Assert
        mockMvc.perform(post("/api/v1/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(solicitud)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generarVenta_CuandoSolicitudVacia_DeberiaRetornarBadRequest() throws Exception {
        // Act & Assert - Solicitud vacía
        mockMvc.perform(post("/api/v1/ventas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
