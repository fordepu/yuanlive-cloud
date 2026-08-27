package blog.yuanyuan.yuanlive.wallet.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class WalletQueryControllerTest {

    @Test
    void listIncomesDeclaresBeforeIdRequestParameterNameExplicitly() throws Exception {
        Method listIncomes = WalletQueryController.class.getMethod("listIncomes", Long.class, int.class);
        RequestParam beforeId = listIncomes.getParameters()[0].getAnnotation(RequestParam.class);

        assertThat(beforeId).isNotNull();
        assertThat(beforeId.value()).isEqualTo("beforeId");
    }
}
