package com.clinica.recordatorios.channel;

import com.clinica.recordatorios.channel.gateway.NotificadorSmsGateway;
import com.clinica.recordatorios.domain.CanalNotificacion;
import com.clinica.recordatorios.domain.Cita;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SmsReminderChannelTest {

    @Mock
    private NotificadorSmsGateway gateway;

    @InjectMocks
    private SmsReminderChannel smsReminderChannel;

    @Test
    void enviar_citaConContacto_delegaEnElGatewayConNumeroYTexto() {
        Cita cita = new Cita(1L, "Ana", "3001234567",
            LocalDateTime.of(2026, 8, 10, 15, 0), CanalNotificacion.SMS, false);

        smsReminderChannel.enviar(cita, "hola");

        verify(gateway).enviarSms("3001234567", "hola");
    }
}
