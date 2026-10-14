package com.clinica.recordatorios.channel;

import com.clinica.recordatorios.domain.CanalNotificacion;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class ReminderChannelFactoryImpl implements ReminderChannelFactory {

    private final ReminderChannel emailReminderChannel;
    private final ReminderChannel smsReminderChannel;

    // @Qualifier por nombre de bean: en la Parte 2 aparece un segundo bean
    // ReminderChannel ("canalUrgente", construido como SmsReminderChannel); sin
    // el qualifier la inyección por tipo podría volverse ambigua.
    public ReminderChannelFactoryImpl(@Qualifier("emailReminderChannel") ReminderChannel emailReminderChannel,
                                      @Qualifier("smsReminderChannel") ReminderChannel smsReminderChannel) {
        this.emailReminderChannel = emailReminderChannel;
        this.smsReminderChannel = smsReminderChannel;
    }

    @Override
    public ReminderChannel obtenerCanal(CanalNotificacion tipo) {
        return switch (tipo) {
            case EMAIL -> emailReminderChannel;
            case SMS -> smsReminderChannel;
        };
    }
}
