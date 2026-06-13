package org.mytestproject.dataloader.services;

import lombok.extern.slf4j.Slf4j;
import org.mytestproject.dataloader.models.EmployeeRecordData;
import org.mytestproject.dataloader.models.SkipEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${app.notifications.email.enabled:true}")
    private boolean enabled;

    @Value("${app.notifications.email.from}")
    private String from;

    @Value("${app.notifications.email.to}")
    private String to;

    @Value("${app.notifications.email.reprocess-base-url:http://localhost:8081/reprocess.html}")
    private String reprocessBaseUrl;

    public EmailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends a plain-text alert email describing a single skipped record. When the original row
     * was captured, the email also shows its values and a link to the edit-and-reprocess page.
     * Failures are logged but never rethrown, so a flaky mail server can't crash the consumer.
     */
    public void sendSkipAlert(SkipEvent event) {
        if (!enabled) {
            log.debug("Email notifications disabled; skipping alert for record {}", event.recordId());
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(String.format("[DataLoader] Record skipped during %s (ID: %s)",
                event.phase(), event.recordId()));
        message.setText(buildBody(event));

        try {
            mailSender.send(message);
            log.info("Sent skip-alert email for record {} (phase {})", event.recordId(), event.phase());
        } catch (MailException e) {
            log.error("Failed to send skip-alert email for record {}: {}", event.recordId(), e.getMessage());
        }
    }

    private String buildBody(SkipEvent event) {
        StringBuilder body = new StringBuilder(String.format(
                "A record was skipped during the data load.%n%n" +
                "Phase     : %s%n" +
                "Record ID : %s%n" +
                "Error     : %s%n" +
                "Timestamp : %s%n",
                event.phase(), event.recordId(), event.errorMessage(), event.timestamp()));

        EmployeeRecordData data = event.data();
        if (data != null) {
            body.append(String.format(
                    "%nRecord values:%n" +
                    "  id     : %s%n" +
                    "  name   : %s%n" +
                    "  email  : %s%n" +
                    "  role   : %s%n" +
                    "  salary : %s%n",
                    data.id(), data.name(), data.email(), data.role(), data.salary()));
            body.append(String.format("%nFix and reprocess this record:%n%s%n", buildReprocessLink(data)));
        } else {
            body.append(String.format(
                    "%nThe original row could not be captured for this skip, so it cannot be " +
                    "reprocessed from this email. Check %s and the source file.%n", "logs/skipped_records.log"));
        }
        return body.toString();
    }

    private String buildReprocessLink(EmployeeRecordData data) {
        return reprocessBaseUrl
                + "?id=" + enc(data.id())
                + "&name=" + enc(data.name())
                + "&email=" + enc(data.email())
                + "&role=" + enc(data.role())
                + "&salary=" + enc(data.salary());
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
