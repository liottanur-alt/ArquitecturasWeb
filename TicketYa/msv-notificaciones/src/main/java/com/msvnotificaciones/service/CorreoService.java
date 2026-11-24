package com.msvnotificaciones.service;

import com.msvnotificaciones.entity.Notificacion;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
public class CorreoService {
    private final JavaMailSender mailSender;
    private final SpringTemplateEngine plantillas;
    private final String remitente;

    public CorreoService(JavaMailSender mailSender, SpringTemplateEngine plantillas,
                         @Value("${notificaciones.mail.remitente}") String remitente) {
        this.mailSender = mailSender;
        this.plantillas = plantillas;
        this.remitente = remitente;
    }

    public void enviar(Notificacion notificacion) throws MessagingException {
        Context contexto = new Context();
        contexto.setVariable("asunto", notificacion.getAsunto());
        contexto.setVariable("mensaje", notificacion.getMensaje());
        String html = plantillas.process("mail/notificacion", contexto);

        var correo = mailSender.createMimeMessage();
        var mensaje = new MimeMessageHelper(correo, true, "UTF-8");
        mensaje.setFrom(remitente);
        mensaje.setTo(notificacion.getDestinatario());
        mensaje.setSubject(notificacion.getAsunto());
        mensaje.setText(notificacion.getMensaje(), html);
        mailSender.send(correo);
    }
}
