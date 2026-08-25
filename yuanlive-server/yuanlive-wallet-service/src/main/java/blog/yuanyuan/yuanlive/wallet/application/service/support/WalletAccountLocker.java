package blog.yuanyuan.yuanlive.wallet.application.service.support;

import blog.yuanyuan.yuanlive.entity.wallet.entity.WalletAccount;
import blog.yuanyuan.yuanlive.wallet.mapper.WalletAccountMapper;
import lombok.RequiredArgsConstructor;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class WalletAccountLocker {

    private final WalletAccountMapper walletAccountMapper;
    private final IdGeneratorProvider idGeneratorProvider;

    @Transactional(propagation = Propagation.MANDATORY)
    public WalletAccount lockOrOpen(Long userId) {
        // 同一用户最多等待 10 秒；获取失败直接抛错，由调用方资金事务整体回滚。
        if (!Integer.valueOf(1).equals(walletAccountMapper.acquireAccountMutex(userId))) {
            throw new IllegalStateException("钱包开户锁获取失败");
        }
        try {
            // 互斥区串行化重复键检查，避免多个 INSERT IGNORE 共享锁同时升级为账户行独占锁。
            walletAccountMapper.insertIgnoreActive(idGeneratorProvider.getShare().generate(), userId);
            // 无论本事务是否插入成功，后续资金更新都必须持有同一账户行锁。
            WalletAccount account = walletAccountMapper.selectByUserIdForUpdate(userId);
            if (account == null) {
                throw new IllegalStateException("钱包开户失败");
            }
            return account;
        } finally {
            // GET_LOCK 是连接级锁，不随事务自动释放；异常路径也必须显式释放，避免污染连接池。
            if (!Integer.valueOf(1).equals(walletAccountMapper.releaseAccountMutex(userId))) {
                throw new IllegalStateException("钱包开户锁释放失败");
            }
        }
    }
}
