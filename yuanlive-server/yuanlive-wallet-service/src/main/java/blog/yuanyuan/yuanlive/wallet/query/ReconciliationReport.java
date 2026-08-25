package blog.yuanyuan.yuanlive.wallet.query;

import java.util.List;

public record ReconciliationReport(String orderNo, boolean orderExists, boolean ledgerExists,
                                   boolean incomeExists, boolean outboxExists, String outboxStatus,
                                   boolean inboxExists, String inboxStatus, List<String> anomalies) {
    public boolean consistent() {
        return anomalies.isEmpty();
    }
}
