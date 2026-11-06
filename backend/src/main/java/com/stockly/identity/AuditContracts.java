package com.stockly.identity;

import java.time.Instant;
import java.util.List;

public final class AuditContracts {
    private AuditContracts() {}
    public record AuditItem(String id,String actorId,String actorName,String action,String actionLabel,
                            String targetType,String targetId,String targetLabel,Instant occurredAt) {}
    public record AuditPage(List<AuditItem> items,long total,int page,int size) {}
}
