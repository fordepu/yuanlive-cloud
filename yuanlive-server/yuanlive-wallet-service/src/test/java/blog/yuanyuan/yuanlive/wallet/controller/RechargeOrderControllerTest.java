package blog.yuanyuan.yuanlive.wallet.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class RechargeOrderControllerTest {

    @Test
    void simulatePaidBindsOrderNoWithoutDependingOnCompilerParameterNames() throws Exception {
        Method simulatePaid = RechargeOrderController.class.getMethod("simulatePaid", String.class);
        PathVariable pathVariable = (PathVariable) simulatePaid.getParameters()[0].getAnnotation(PathVariable.class);

        assertThat(pathVariable).isNotNull();
        assertThat(pathVariable.value()).isEqualTo("orderNo");
    }
}
