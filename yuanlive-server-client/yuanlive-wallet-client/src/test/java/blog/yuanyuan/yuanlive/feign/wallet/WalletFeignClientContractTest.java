package blog.yuanyuan.yuanlive.feign.wallet;

import blog.yuanyuan.yuanlive.feign.wallet.dto.WalletBalanceQueryResult;
import blog.yuanyuan.yuanlive.feign.wallet.dto.WalletOrderQueryResult;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class WalletFeignClientContractTest {

    @Test
    void exposesOnlyReadQueriesForCrossServiceWalletLookup() throws NoSuchMethodException {
        FeignClient feignClient = WalletFeignClient.class.getAnnotation(FeignClient.class);
        Method balanceQuery = WalletFeignClient.class.getMethod("getBalance", Long.class);
        Method orderQuery = WalletFeignClient.class.getMethod("getOrder", String.class);

        assertThat(feignClient.value()).isEqualTo("wallet-service");
        assertThat(balanceQuery.getAnnotation(GetMapping.class).value())
                .containsExactly("/internal/wallet/accounts/{userId}");
        assertThat(orderQuery.getAnnotation(GetMapping.class).value())
                .containsExactly("/internal/wallet/orders/{orderNo}");
        assertThat(Arrays.stream(WalletFeignClient.class.getMethods())
                .filter(method -> method.getDeclaringClass() == WalletFeignClient.class)
                .allMatch(method -> method.isAnnotationPresent(GetMapping.class)))
                .isTrue();
        assertThat(WalletBalanceQueryResult.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("userId", "availableCoin");
        assertThat(WalletOrderQueryResult.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("orderNo", "userId", "status");
    }
}
