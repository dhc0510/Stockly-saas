package com.stockly.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class InventoryContracts {
    private InventoryContracts() {}
    public record BranchInput(String name, String code, String address, String phone, String contactEmail, String managerName) {}
    public record Branch(String id, String name, String code, String address, String phone, String contactEmail,
                         String managerName, boolean active) {}
    public record BranchAccess(String userId, String fullName, String email, String role, boolean selected) {}
    public record ItemAssignmentInput(String itemId, BigDecimal minimumQuantity, BigDecimal lowThreshold,
                                      BigDecimal recoveryMargin, String exhaustedBasis) {}
    /** STK-016: targetQuantity null borra el nivel deseado; restockPaused null conserva «No reponer» como está. */
    public record ThresholdInput(BigDecimal minimumQuantity, BigDecimal lowThreshold,
                                 BigDecimal recoveryMargin, String exhaustedBasis,
                                 BigDecimal targetQuantity, Boolean restockPaused) {
        public ThresholdInput(BigDecimal minimumQuantity, BigDecimal lowThreshold, BigDecimal recoveryMargin, String exhaustedBasis) {
            this(minimumQuantity, lowThreshold, recoveryMargin, exhaustedBasis, null, null);
        }
    }
    public record StockItem(String productId, String productName, String category, String itemId,
                            String variantName, String sku, String barcode, String unit,
                            String branchId, String branchName, BigDecimal onHand, BigDecimal reserved,
                            BigDecimal available, BigDecimal minimumQuantity, BigDecimal lowThreshold,
                            BigDecimal recoveryMargin, String exhaustedBasis, String state,
                            BigDecimal targetQuantity, boolean restockPaused) {}
    public record PendingItem(String productId,String productName,String category,String itemId,String variantName,String sku,String unit) {}
    public record Alert(String id, String type, String productId, String productName, String variantName,
                        String sku, String unit, String branchId, String branchName, BigDecimal available,
                        BigDecimal minimumQuantity, Instant lastNotifiedAt) {}
    public record AccessInput(List<String> userIds) {}
}
