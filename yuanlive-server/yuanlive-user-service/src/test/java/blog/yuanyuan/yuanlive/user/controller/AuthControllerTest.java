package blog.yuanyuan.yuanlive.user.controller;

import blog.yuanyuan.yuanlive.common.result.Result;
import blog.yuanyuan.yuanlive.user.domain.dto.LoginDTO;
import blog.yuanyuan.yuanlive.user.domain.vo.LoginVO;
import blog.yuanyuan.yuanlive.user.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void delegatesPasswordLoginAndReturnsTheIssuedTokens() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "authService", authService);
        LoginDTO request = new LoginDTO();
        request.setAccount("yuanlive");
        request.setPassword("password");
        LoginVO login = new LoginVO("access", "refresh", 0, "1001", 123L);
        when(authService.login(request)).thenReturn(login);

        Result<LoginVO> result = controller.login(request);

        assertEquals(login, result.getData());
        verify(authService).login(request);
    }
}
