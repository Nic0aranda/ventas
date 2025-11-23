package com.pokeshop.ventas.service;

import com.pokeshop.ventas.dto.*;
import com.pokeshop.ventas.model.*;
import com.pokeshop.ventas.repository.VentaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

@Service
public class VentaService {

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${api.usuarios.url}")
    private String usuariosUrl;

    @Value("${api.productos.url}")
    private String productosUrl;

    @Transactional
    public Venta crearVenta(SolicitudVentaDto solicitud) {
        
        // 1. Validar que el usuario exista en el microservicio de Usuarios
        // URL Ejemplo: http://localhost:8080/api/v1/users/1
        String urlUser = usuariosUrl + "/" + solicitud.getIdUsuario();  
        System.out.println("Validando usuario en URL: " + urlUser); // <- Imprime esto en consola

        try {
        // Usamos String.class para recibir el JSON crudo y evitar errores de mapeo por ahora
        restTemplate.getForObject(urlUser, String.class);
    
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
        // ESTE es el verdadero "Usuario no existe" (Error 404)
        throw new RuntimeException("El usuario con ID " + solicitud.getIdUsuario() + " no existe en la base de datos.");
    
        } catch (org.springframework.web.client.ResourceAccessException e) {
        // Esto pasa si el microservicio de Usuarios está APAGADO o el puerto es incorrecto
        e.printStackTrace(); // Imprime el error completo en la consola
        throw new RuntimeException("Error de conexión: No se pudo contactar con la API de Usuarios en " + urlUser);
    
        } catch (Exception e) {
        // Cualquier otro error (ej: error interno 500 del otro servicio)
        e.printStackTrace();
        throw new RuntimeException("Error inesperado al validar usuario: " + e.getMessage());
        }

        // 2. Crear objeto Venta
        Venta venta = new Venta();
        venta.setIdUsuario(solicitud.getIdUsuario());
        venta.setFecha(LocalDateTime.now());
        venta.setEstado("COMPLETADA");

        double subtotal = 0.0;

        // 3. Iterar sobre los productos solicitados
        for (SolicitudVentaDto.ItemProductoDto item : solicitud.getProductos()) {
            
            // A. Obtener info del producto del microservicio de Productos
            String urlProd = productosUrl + "/" + item.getIdProducto();
            ProductoExternoDto prodExterno;
            try {
                 prodExterno = restTemplate.getForObject(urlProd, ProductoExternoDto.class);
            } catch (HttpClientErrorException e) {
                throw new RuntimeException("Error: Producto ID " + item.getIdProducto() + " no encontrado.");
            }

            // B. Validar Stock
            if (prodExterno.getStock() < item.getCantidad()) {
                throw new RuntimeException("Stock insuficiente para: " + prodExterno.getNombre());
            }

            // C. Descontar Stock (Llamada PUT al microservicio de Productos)
            // URL: http://localhost:8081/api/v1/productos/{id}/stock?cantidad={n}
            String urlDescuento = productosUrl + "/" + item.getIdProducto() + "/stock?cantidad=" + item.getCantidad();
            restTemplate.put(urlDescuento, null);

            // D. Crear Detalle
            DetalleVenta detalle = new DetalleVenta();
            detalle.setIdProducto(item.getIdProducto());
            detalle.setNombreProducto(prodExterno.getNombre());
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitario(prodExterno.getPrecio());
            detalle.setVenta(venta);

            // Agregar a la lista y sumar
            venta.getDetalles().add(detalle);
            subtotal += (prodExterno.getPrecio() * item.getCantidad());
        }

        // 4. Calcular Totales
        venta.setSubtotal(subtotal);
        venta.setIva(subtotal * 0.19); // IVA 19%
        venta.setTotal(subtotal * 1.19);

        // 5. Guardar en Base de Datos (Cascada guarda los detalles también)
        return ventaRepository.save(venta);
    }
}
