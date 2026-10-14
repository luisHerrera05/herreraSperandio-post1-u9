package com.clinica.recordatorios.channel;

import com.clinica.recordatorios.domain.CanalNotificacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertSame;

class ReminderChannelFactoryImplTest {

    private final ReminderChannel email = Mockito.mock(ReminderChannel.class);
    private final ReminderChannel sms = Mockito.mock(ReminderChannel.class);
    private ReminderChannelFactoryImpl factory;

    @BeforeEach
    void setUp() {
        factory = new ReminderChannelFactoryImpl(email, sms);
    }

    @Test
    void obtenerCanal_canalEmail_retornaLaEstrategiaDeEmail() {
        assertSame(email, factory.obtenerCanal(CanalNotificacion.EMAIL));
    }

    @Test
    void obtenerCanal_canalSms_retornaLaEstrategiaDeSms() {
        assertSame(sms, factory.obtenerCanal(CanalNotificacion.SMS));
    }
}
