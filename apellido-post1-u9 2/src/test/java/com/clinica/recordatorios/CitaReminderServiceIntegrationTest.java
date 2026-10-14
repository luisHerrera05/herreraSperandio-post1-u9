package com.clinica.recordatorios;

import com.clinica.recordatorios.channel.gateway.NotificadorSmsGateway;
import com.clinica.recordatorios.domain.CanalNotificacion;
import com.clinica.recordatorios.domain.Cita;
import com.clinica.recordatorios.repository.CitaRepository;
import com.clinica.recordatorios.service.CitaReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class CitaReminderServiceIntegrationTest {

    @TestConfiguration
    static class ClockDeterministaConfig {
        // El método NO se llama "clock": ese nombre ya lo usa ClockConfig y Spring Boot
        // prohíbe por defecto sobrescribir beans con el mismo nombre.
        @Bean
        @Primary
        public Clock clockFijo() {
            return Clock.fixed(Instant.parse("2026-08-10T09:00:00Z"), ZoneId.of("UTC"));
        }
    }

    @Autowired
    private CitaReminderService citaReminderService;

    @Autowired
    private CitaRepository citaRepository;

    @MockBean
    private NotificadorSmsGateway notificadorSmsGateway;

    @BeforeEach
    void limpiarBaseDeDatos() {
        citaRepository.deleteAll();
    }

    @Test
    void enviarRecordatoriosPendientes_contextoCompletoDeSpring_persisteYNotificaCitasEnVentana() {
        citaRepository.save(new Cita(null, "Carla Ríos", "3011112222",
            LocalDateTime.of(2026, 8, 10, 18, 0), CanalNotificacion.SMS, false));

        int enviados = citaReminderService.enviarRecordatoriosPendientes();

        assertEquals(1, enviados);
        verify(notificadorSmsGateway, times(1)).enviarSms(eq("3011112222"), anyString());
        assertTrue(citaRepository.findAll().get(0).isRecordatorioEnviado());
    }

    @Test
    void enviarRecordatorioUrgente_contextoCompletoDeSpring_usaElBeanCanalUrgenteYMarcaEnviado() {
        Cita guardada = citaRepository.save(new Cita(null, "Pedro Díaz", "3022223333",
            LocalDateTime.of(2026, 8, 10, 10, 0), CanalNotificacion.SMS, false));

        citaReminderService.enviarRecordatorioUrgente(guardada.getId());

        verify(notificadorSmsGateway).enviarSms(eq("3022223333"), contains("URGENTE"));
        assertTrue(citaRepository.findById(guardada.getId()).orElseThrow().isRecordatorioEnviado());
    }

    @Test
    void enviarRecordatoriosPendientes_citaFueraDeVentana_noSeNotifica() {
        citaRepository.save(new Cita(null, "Fuera de ventana", "3033334444",
            LocalDateTime.of(2026, 8, 20, 9, 0), CanalNotificacion.SMS, false));

        int enviados = citaReminderService.enviarRecordatoriosPendientes();

        assertEquals(0, enviados);
        verifyNoInteractions(notificadorSmsGateway);
    }
}
