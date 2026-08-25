package blog.yuanyuan.yuanlive.wallet.mapper;

import blog.yuanyuan.yuanlive.entity.wallet.entity.WalletAccount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface WalletAccountMapper extends BaseMapper<WalletAccount> {

    @Select("SELECT GET_LOCK(CONCAT('yuanlive-wallet-account:', #{userId}), 10)")
    Integer acquireAccountMutex(Long userId);

    @Select("SELECT RELEASE_LOCK(CONCAT('yuanlive-wallet-account:', #{userId}))")
    Integer releaseAccountMutex(Long userId);

    @Select("SELECT * FROM wallet_account WHERE user_id = #{userId} FOR UPDATE")
    WalletAccount selectByUserIdForUpdate(Long userId);

    @Select("SELECT * FROM wallet_account WHERE user_id = #{userId} LIMIT 1")
    WalletAccount selectByUserId(Long userId);

    @Insert("INSERT IGNORE INTO wallet_account (id, user_id, available_coin, version, status) "
            + "VALUES (#{id}, #{userId}, 0, 0, 'ACTIVE')")
    int insertIgnoreActive(@Param("id") Long id, @Param("userId") Long userId);

    @Update("UPDATE wallet_account "
            + "SET available_coin = available_coin + #{amount}, version = version + 1 "
            + "WHERE id = #{accountId}")
    int credit(@Param("accountId") Long accountId, @Param("amount") Long amount);

    @Update("UPDATE wallet_account "
            + "SET available_coin = available_coin - #{amount}, version = version + 1 "
            + "WHERE id = #{accountId} AND available_coin >= #{amount}")
    int debitIfEnough(@Param("accountId") Long accountId, @Param("amount") Long amount);
}
