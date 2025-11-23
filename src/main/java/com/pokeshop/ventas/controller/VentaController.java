package com.pokeshop.ventas.controller;

import com.pokeshop.ventas.dto.SolicitudVentaDto;
import com.pokeshop.ventas.model.Venta;
import com.pokeshop.ventas.service.VentaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ventas")
public class VentaController {

    @Autowired
    private VentaService ventaService;

    @PostMapping
    public ResponseEntity<?> generarVenta(@RequestBody SolicitudVentaDto solicitud) {
        try {
            Venta ventaRealizada = ventaService.crearVenta(solicitud);
            return ResponseEntity.ok(ventaRealizada);
        } catch (RuntimeException e) {
            // Devolvemos un Bad Request (400) con el mensaje de error (ej: falta stock)
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
