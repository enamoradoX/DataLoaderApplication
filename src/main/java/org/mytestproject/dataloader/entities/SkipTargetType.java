package org.mytestproject.dataloader.entities;

/** Which table a skipped record was being loaded into — so the digest/review/reprocess flow can tell them apart. */
public enum SkipTargetType {
    EMPLOYEE,
    DEPARTMENT
}
