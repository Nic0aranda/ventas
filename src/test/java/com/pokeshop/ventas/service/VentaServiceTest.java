package com.pokeshop.ventas.service;

import com.pokeshop.ventas.dto.ProductoExternoDto;
import com.pokeshop.ventas.dto.SolicitudVentaDto;
import com.pokeshop.ventas.model.DetalleVenta;
import com.pokeshop.ventas.model.Venta;
import com.pokeshop.ventas.repository.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VentaServiceTest {

    @Mock
    private VentaRepository ventaRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private VentaService ventaService;

    private final String usuariosUrl = "http://localhost:8080/api/v1/users";
    private final String productosUrl = "http://localhost:8081/api/v1/productos";

    @BeforeEach
    void setUp() throws Exception {
        // Inyectar las propiedades @Value usando reflexión
        setPrivateField(ventaService, "usuariosUrl", usuariosUrl);
        setPrivateField(ventaService, "productosUrl", productosUrl);
    }

    @Test
    void crearVenta_CuandoUsuarioExisteYProductosValidos_DeberiaCrearVentaCorrectamente() {
        // Arrange
        SolicitudVentaDto solicitud = crearSolicitudVentaValida();
        
        // Mock para validación de usuario
        when(restTemplate.getForObject(usuariosUrl + "/1", String.class))
            .thenReturn("{\"id\":1,\"nombre\":\"Usuario Test\"}");
        
        // Mock para productos
        ProductoExternoDto producto1 = new ProductoExternoDto();
        producto1.setIdProducto(100L);
        producto1.setNombre("Pokemon Plush");
        producto1.setPrecio(19.99);
        producto1.setStock(10);
        
        ProductoExternoDto producto2 = new ProductoExternoDto();
        producto2.setIdProducto(200L);
        producto2.setNombre("Pokemon Card");
        producto2.setPrecio(5.99);
        producto2.setStock(20);
        
        when(restTemplate.getForObject(productosUrl + "/100", ProductoExternoDto.class))
            .thenReturn(producto1);
        when(restTemplate.getForObject(productosUrl + "/200", ProductoExternoDto.class))
            .thenReturn(producto2);
        
        // Mock para actualización de stock
        doNothing().when(restTemplate).put(anyString(), isNull());
        
        // Mock para guardar venta
        when(ventaRepository.save(any(Venta.class))).thenAnswer(invocation -> {
            Venta venta = invocation.getArgument(0);
            venta.setIdVenta(1L);
            return venta;
        });
        
        // Act
        Venta ventaCreada = ventaService.crearVenta(solicitud);
        
        // Assert
        assertNotNull(ventaCreada);
        assertEquals(1L, ventaCreada.getIdUsuario());
        assertEquals("COMPLETADA", ventaCreada.getEstado());
        assertNotNull(ventaCreada.getFecha());
        
        // Verificar cálculos
        double subtotalEsperado = (19.99 * 2) + (5.99 * 1); // 39.98 + 5.99 = 45.97
        double ivaEsperado = subtotalEsperado * 0.19; // 45.97 * 0.19 = 8.7343
        double totalEsperado = subtotalEsperado * 1.19; // 45.97 * 1.19 = 54.7043
        
        assertEquals(subtotalEsperado, ventaCreada.getSubtotal(), 0.001);
        assertEquals(ivaEsperado, ventaCreada.getIva(), 0.001);
        assertEquals(totalEsperado, ventaCreada.getTotal(), 0.001);
        
        // Verificar detalles
        assertEquals(2, ventaCreada.getDetalles().size());
        
        // Verificar llamadas a servicios externos
        verify(restTemplate, times(1)).getForObject(usuariosUrl + "/1", String.class);
        verify(restTemplate, times(2)).getForObject(anyString(), eq(ProductoExternoDto.class));
        verify(restTemplate, times(2)).put(anyString(), isNull());
        verify(ventaRepository, times(1)).save(any(Venta.class));
    }

    @Test
    void crearVenta_CuandoUsuarioNoExiste_DeberiaLanzarExcepcion() {
        // Arrange
        SolicitudVentaDto solicitud = new SolicitudVentaDto();
        solicitud.setIdUsuario(999L);
        solicitud.setProductos(Arrays.asList());
        
        when(restTemplate.getForObject(usuariosUrl + "/999", String.class))
            .thenThrow(HttpClientErrorException.NotFound.class);
        
        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ventaService.crearVenta(solicitud);
        });
        
        assertEquals("El usuario con ID 999 no existe en la base de datos.", exception.getMessage());
        verify(restTemplate, times(1)).getForObject(usuariosUrl + "/999", String.class);
        verifyNoInteractions(ventaRepository);
    }

    @Test
    void crearVenta_CuandoServicioUsuariosNoDisponible_DeberiaLanzarExcepcion() {
        // Arrange
        SolicitudVentaDto solicitud = new SolicitudVentaDto();
        solicitud.setIdUsuario(1L);
        solicitud.setProductos(Arrays.asList());
        
        when(restTemplate.getForObject(usuariosUrl + "/1", String.class))
            .thenThrow(new ResourceAccessException("Connection refused"));
        
        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ventaService.crearVenta(solicitud);
        });
        
        assertTrue(exception.getMessage().contains("Error de conexión: No se pudo contactar con la API de Usuarios"));
        verify(restTemplate, times(1)).getForObject(usuariosUrl + "/1", String.class);
        verifyNoInteractions(ventaRepository);
    }

    @Test
    void crearVenta_CuandoProductoNoExiste_DeberiaLanzarExcepcion() {
        // Arrange
        SolicitudVentaDto solicitud = crearSolicitudVentaValida();
        
        when(restTemplate.getForObject(usuariosUrl + "/1", String.class))
            .thenReturn("{\"id\":1,\"nombre\":\"Usuario Test\"}");
        
        when(restTemplate.getForObject(productosUrl + "/100", ProductoExternoDto.class))
            .thenThrow(HttpClientErrorException.NotFound.class);
        
        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ventaService.crearVenta(solicitud);
        });
        
        assertEquals("Error: Producto ID 100 no encontrado.", exception.getMessage());
        verify(restTemplate, times(1)).getForObject(usuariosUrl + "/1", String.class);
        verify(restTemplate, times(1)).getForObject(productosUrl + "/100", ProductoExternoDto.class);
        verifyNoInteractions(ventaRepository);
    }

    @Test
    void crearVenta_CuandoStockInsuficiente_DeberiaLanzarExcepcion() {
        // Arrange
        SolicitudVentaDto solicitud = new SolicitudVentaDto();
        solicitud.setIdUsuario(1L);
        
        SolicitudVentaDto.ItemProductoDto item = new SolicitudVentaDto.ItemProductoDto();
        item.setIdProducto(100L);
        item.setCantidad(10);
        solicitud.setProductos(Arrays.asList(item));
        
        when(restTemplate.getForObject(usuariosUrl + "/1", String.class))
            .thenReturn("{\"id\":1,\"nombre\":\"Usuario Test\"}");
        
        ProductoExternoDto producto = new ProductoExternoDto();
        producto.setIdProducto(100L);
        producto.setNombre("Pokemon Plush");
        producto.setPrecio(19.99);
        producto.setStock(5); // Solo hay 5 en stock
        
        when(restTemplate.getForObject(productosUrl + "/100", ProductoExternoDto.class))
            .thenReturn(producto);
        
        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ventaService.crearVenta(solicitud);
        });
        
        assertEquals("Stock insuficiente para: Pokemon Plush", exception.getMessage());
        verify(restTemplate, times(1)).getForObject(usuariosUrl + "/1", String.class);
        verify(restTemplate, times(1)).getForObject(productosUrl + "/100", ProductoExternoDto.class);
        verifyNoInteractions(ventaRepository);
    }

    @Test
    void crearVenta_CuandoErrorGeneralEnValidacionUsuario_DeberiaLanzarExcepcion() {
        // Arrange
        SolicitudVentaDto solicitud = new SolicitudVentaDto();
        solicitud.setIdUsuario(1L);
        solicitud.setProductos(Arrays.asList());
        
        when(restTemplate.getForObject(usuariosUrl + "/1", String.class))
            .thenThrow(new RuntimeException("Error interno del servidor"));
        
        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ventaService.crearVenta(solicitud);
        });
        
        assertTrue(exception.getMessage().contains("Error inesperado al validar usuario"));
        verify(restTemplate, times(1)).getForObject(usuariosUrl + "/1", String.class);
        verifyNoInteractions(ventaRepository);
    }

    @Test
    void crearVenta_CuandoListaProductosVacia_DeberiaCrearVentaSinDetalles() {
        // Arrange
        SolicitudVentaDto solicitud = new SolicitudVentaDto();
        solicitud.setIdUsuario(1L);
        solicitud.setProductos(Arrays.asList());
        
        when(restTemplate.getForObject(usuariosUrl + "/1", String.class))
            .thenReturn("{\"id\":1,\"nombre\":\"Usuario Test\"}");
        
        when(ventaRepository.save(any(Venta.class))).thenAnswer(invocation -> {
            Venta venta = invocation.getArgument(0);
            venta.setIdVenta(1L);
            return venta;
        });
        
        // Act
        Venta ventaCreada = ventaService.crearVenta(solicitud);
        
        // Assert
        assertNotNull(ventaCreada);
        assertEquals(1L, ventaCreada.getIdUsuario());
        assertEquals(0.0, ventaCreada.getSubtotal());
        assertEquals(0.0, ventaCreada.getIva());
        assertEquals(0.0, ventaCreada.getTotal());
        assertTrue(ventaCreada.getDetalles().isEmpty());
    }

    @Test
    void crearVenta_CuandoMultiplesProductos_DeberiaActualizarStockParaCadaUno() {
        // Arrange
        SolicitudVentaDto solicitud = crearSolicitudVentaValida();
        
        when(restTemplate.getForObject(usuariosUrl + "/1", String.class))
            .thenReturn("{\"id\":1,\"nombre\":\"Usuario Test\"}");
        
        ProductoExternoDto producto1 = new ProductoExternoDto();
        producto1.setIdProducto(100L);
        producto1.setNombre("Pokemon Plush");
        producto1.setPrecio(19.99);
        producto1.setStock(10);
        
        ProductoExternoDto producto2 = new ProductoExternoDto();
        producto2.setIdProducto(200L);
        producto2.setNombre("Pokemon Card");
        producto2.setPrecio(5.99);
        producto2.setStock(20);
        
        when(restTemplate.getForObject(productosUrl + "/100", ProductoExternoDto.class))
            .thenReturn(producto1);
        when(restTemplate.getForObject(productosUrl + "/200", ProductoExternoDto.class))
            .thenReturn(producto2);
        
        when(ventaRepository.save(any(Venta.class))).thenAnswer(invocation -> {
            Venta venta = invocation.getArgument(0);
            venta.setIdVenta(1L);
            return venta;
        });
        
        // Act
        ventaService.crearVenta(solicitud);
        
        // Assert - Verificar que se llamó a actualizar stock para ambos productos
        verify(restTemplate, times(1)).put(productosUrl + "/100/stock?cantidad=2", null);
        verify(restTemplate, times(1)).put(productosUrl + "/200/stock?cantidad=1", null);
    }

    @Test
    void crearVenta_DeberiaAsignarFechaActual() {
        // Arrange
        SolicitudVentaDto solicitud = new SolicitudVentaDto();
        solicitud.setIdUsuario(1L);
        solicitud.setProductos(Arrays.asList());
        
        when(restTemplate.getForObject(usuariosUrl + "/1", String.class))
            .thenReturn("{\"id\":1,\"nombre\":\"Usuario Test\"}");
        
        ArgumentCaptor<Venta> ventaCaptor = ArgumentCaptor.forClass(Venta.class);
        when(ventaRepository.save(ventaCaptor.capture())).thenAnswer(invocation -> {
            Venta venta = invocation.getArgument(0);
            venta.setIdVenta(1L);
            return venta;
        });
        
        LocalDateTime antesDeEjecucion = LocalDateTime.now().minusSeconds(1);
        
        // Act
        Venta ventaCreada = ventaService.crearVenta(solicitud);
        LocalDateTime despuesDeEjecucion = LocalDateTime.now().plusSeconds(1);
        
        // Assert
        Venta ventaCapturada = ventaCaptor.getValue();
        assertNotNull(ventaCapturada.getFecha());
        assertTrue(ventaCapturada.getFecha().isAfter(antesDeEjecucion) || 
                  ventaCapturada.getFecha().isEqual(antesDeEjecucion));
        assertTrue(ventaCapturada.getFecha().isBefore(despuesDeEjecucion) || 
                  ventaCapturada.getFecha().isEqual(despuesDeEjecucion));
    }

    @Test
    void crearVenta_DeberiaCalcularIVA19Porciento() {
        // Arrange
        SolicitudVentaDto solicitud = new SolicitudVentaDto();
        solicitud.setIdUsuario(1L);
        
        SolicitudVentaDto.ItemProductoDto item = new SolicitudVentaDto.ItemProductoDto();
        item.setIdProducto(100L);
        item.setCantidad(1);
        solicitud.setProductos(Arrays.asList(item));
        
        when(restTemplate.getForObject(usuariosUrl + "/1", String.class))
            .thenReturn("{\"id\":1,\"nombre\":\"Usuario Test\"}");
        
        ProductoExternoDto producto = new ProductoExternoDto();
        producto.setIdProducto(100L);
        producto.setNombre("Producto Test");
        producto.setPrecio(100.0);
        producto.setStock(10);
        
        when(restTemplate.getForObject(productosUrl + "/100", ProductoExternoDto.class))
            .thenReturn(producto);
        
        doNothing().when(restTemplate).put(anyString(), isNull());
        
        ArgumentCaptor<Venta> ventaCaptor = ArgumentCaptor.forClass(Venta.class);
        when(ventaRepository.save(ventaCaptor.capture())).thenAnswer(invocation -> {
            Venta venta = invocation.getArgument(0);
            venta.setIdVenta(1L);
            return venta;
        });
        
        // Act
        ventaService.crearVenta(solicitud);
        
        // Assert
        Venta ventaCapturada = ventaCaptor.getValue();
        assertEquals(100.0, ventaCapturada.getSubtotal(), 0.001);
        assertEquals(19.0, ventaCapturada.getIva(), 0.001); // 19% de 100
        assertEquals(119.0, ventaCapturada.getTotal(), 0.001); // 100 + 19
    }

    // Helper method para crear una solicitud de venta válida
    private SolicitudVentaDto crearSolicitudVentaValida() {
        SolicitudVentaDto solicitud = new SolicitudVentaDto();
        solicitud.setIdUsuario(1L);
        
        SolicitudVentaDto.ItemProductoDto item1 = new SolicitudVentaDto.ItemProductoDto();
        item1.setIdProducto(100L);
        item1.setCantidad(2);
        
        SolicitudVentaDto.ItemProductoDto item2 = new SolicitudVentaDto.ItemProductoDto();
        item2.setIdProducto(200L);
        item2.setCantidad(1);
        
        solicitud.setProductos(Arrays.asList(item1, item2));
        return solicitud;
    }
    
    // Helper method para setear campos privados usando reflexión
    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}