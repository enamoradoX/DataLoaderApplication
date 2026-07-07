package org.mytestproject.dataloader.exceptions;

/**
 * Thrown by the department processor when a name is already loaded (in the DB or earlier in the
 * same file). It's a process-phase signal, so the step skips and records it with a reason — no
 * duplicate insert is ever attempted (which is what avoids the JPA rollback-only failure).
 */
public class DuplicateDepartmentException extends RuntimeException {
    public DuplicateDepartmentException(String message) {
        super(message);
    }
}
