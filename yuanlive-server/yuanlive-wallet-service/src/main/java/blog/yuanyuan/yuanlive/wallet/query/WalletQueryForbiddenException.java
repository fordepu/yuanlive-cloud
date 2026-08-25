package blog.yuanyuan.yuanlive.wallet.query;

import blog.yuanyuan.yuanlive.common.exception.ApiException;

public class WalletQueryForbiddenException extends ApiException {
    public WalletQueryForbiddenException() {
        super("无权查询该订单");
    }
}
