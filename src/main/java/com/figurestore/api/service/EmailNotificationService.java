package com.figurestore.api.service;

import com.figurestore.api.model.Order;
import com.figurestore.api.model.Payment;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {
    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;

    public EmailNotificationService(ObjectProvider<JavaMailSender> mailSender,
                                    @Value("${app.notification.email-enabled:false}") boolean enabled,
                                    @Value("${app.notification.from:no-reply@figurevault.local}") String from) {
        this.mailSender = mailSender.getIfAvailable(JavaMailSenderImpl::new);
        this.enabled = enabled;
        this.from = from;
    }

    public boolean sendPelunasan(Order order, Payment payment) {
        if (!enabled) {
            return false;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(order.getUser().getEmail());
        message.setSubject("Tagihan pelunasan " + order.getOrderNumber());
        message.setText("Halo " + order.getUser().getFullName() + ",\n\n"
                + "Barang untuk order " + order.getOrderNumber() + " sudah siap dilunasi.\n"
                + "Sisa pembayaran: Rp " + payment.getAmount().toPlainString() + "\n"
                + "Batas pembayaran: " + payment.getExpiredAt() + "\n\n"
                + "Terima kasih.");
        mailSender.send(message);
        return true;
    }
}
