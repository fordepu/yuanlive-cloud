package blog.yuanyuan.yuanlive.live.controller;

import blog.yuanyuan.yuanlive.live.domain.dto.SrsCallBackDTO;
import blog.yuanyuan.yuanlive.live.service.LiveRoomService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveRoomControllerTest {

    @Test
    void returnsSrsSuccessCodeWhenLiveStartSucceeds() {
        LiveRoomService service = mock(LiveRoomService.class);
        LiveRoomController controller = new LiveRoomController();
        ReflectionTestUtils.setField(controller, "liveRoomService", service);
        SrsCallBackDTO callback = new SrsCallBackDTO();
        callback.setStream("10001");
        when(service.startLive(callback)).thenReturn(true);

        assertEquals(0, controller.startLive(callback));
        verify(service).startLive(callback);
    }

    @Test
    void alwaysAcknowledgesSrsEndCallbackAfterServiceFailure() {
        LiveRoomService service = mock(LiveRoomService.class);
        LiveRoomController controller = new LiveRoomController();
        ReflectionTestUtils.setField(controller, "liveRoomService", service);
        SrsCallBackDTO callback = new SrsCallBackDTO();
        callback.setStream("10001");
        when(service.endLive(callback)).thenThrow(new IllegalStateException("already ended"));

        assertEquals(0, controller.endLive(callback));
        verify(service).endLive(callback);
    }
}
