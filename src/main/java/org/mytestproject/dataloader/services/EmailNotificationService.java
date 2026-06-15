package org.mytestproject.dataloader.services;

import lombok.extern.slf4j.Slf4j;
import org.mytestproject.dataloader.entities.SkippedRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.util.List;

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

    @Value("${app.notifications.email.skips-base-url:http://localhost:4200/skips}")
    private String skipsBaseUrl;

    public EmailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends ONE digest email for a finished load that produced skips, listing each skipped record
     * and a single link to the review-and-reprocess page for that load. Called from the end-of-run
     * hooks (batch JobPerformanceListener.afterJob and the legacy DataLoaderService), not per skip.
     * Failures are logged but never rethrown, so a flaky mail server can't fail the load.
     */
    public void sendLoadDigest(String loadId, List<SkippedRecord> skips) {
        if (!enabled) {
            log.debug("Email notifications disabled; skipping digest for load {}", loadId);
            return;
        }
        if (skips == null || skips.isEmpty()) {
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(String.format("[DataLoader] %d record(s) skipped in load %s", skips.size(), loadId));
        message.setText(buildDigestBody(loadId, skips));

        try {
            mailSender.send(message);
            log.info("Sent skip digest email for load {} ({} record(s))", loadId, skips.size());
        } catch (MailException e) {
            log.error("Failed to send skip digest email for load {}: {}", loadId, e.getMessage());
        }
    }

    private String buildDigestBody(String loadId, List<SkippedRecord> skips) {
        StringBuilder body = new StringBuilder(String.format(
                "Load %s finished with %d skipped record(s):%n%n", loadId, skips.size()));

        for (SkippedRecord skip : skips) {
            body.append(String.format("  - [%s] %s: %s%n",
                    skip.getRecordId(), skip.getPhase(), skip.getErrorMessage()));
        }

        body.append(String.format("%nReview and reprocess them all here:%n%s/%s%n", skipsBaseUrl, loadId));
        return body.toString();
    }
}
