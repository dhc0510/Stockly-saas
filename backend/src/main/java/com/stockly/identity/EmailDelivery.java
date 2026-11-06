package com.stockly.identity;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailDelivery {
    private final ObjectProvider<JavaMailSender> senders;
    private final String from;

    public EmailDelivery(ObjectProvider<JavaMailSender> senders,
                         @Value("${stockly.mail-from:no-reply@stockly.local}") String from) {
        this.senders = senders;
        this.from = from;
    }

    public void send(String recipient, String subject, String body) {
        var sender = senders.getIfAvailable();
        if (sender == null) throw new IllegalStateException("Servicio de correo no configurado");
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}
