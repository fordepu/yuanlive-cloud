package blog.yuanyuan.yuanlive.live.controller;

import blog.yuanyuan.yuanlive.entity.live.entity.LiveRoom;
import blog.yuanyuan.yuanlive.feign.live.dto.GiftRoomValidationResult;
import blog.yuanyuan.yuanlive.live.service.GiftRoomValidationPolicy;
import blog.yuanyuan.yuanlive.live.service.LiveRoomService;
import blog.yuanyuan.yuanlive.common.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveGiftValidationControllerTest {

    private final GiftRoomValidationPolicy policy = new GiftRoomValidationPolicy();

    @Test
    void rejectsUnknownRoomWithoutExposingAnyWalletData() {
        GiftRoomValidationResult result = policy.validate(null);

        assertThat(result.roomId()).isNull();
        assertThat(result.anchorId()).isNull();
        assertThat(result.liveStatus()).isNull();
        assertThat(result.acceptingGifts()).isFalse();
        assertThat(result.rejectionReason()).isEqualTo("ROOM_NOT_FOUND");
    }

    @Test
    void rejectsRoomThatIsNotLive() {
        GiftRoomValidationResult result = policy.validate(room(200L, 300L, 0, true));

        assertThat(result.roomId()).isEqualTo(200L);
        assertThat(result.anchorId()).isEqualTo(300L);
        assertThat(result.liveStatus()).isZero();
        assertThat(result.acceptingGifts()).isFalse();
        assertThat(result.rejectionReason()).isEqualTo("ROOM_NOT_LIVE");
    }

    @Test
    void rejectsLiveRoomThatHasDisabledGifts() {
        GiftRoomValidationResult result = policy.validate(room(201L, 301L, 1, false));

        assertThat(result.liveStatus()).isEqualTo(1);
        assertThat(result.acceptingGifts()).isFalse();
        assertThat(result.rejectionReason()).isEqualTo("GIFTS_DISABLED");
    }

    @Test
    void acceptsLiveRoomThatAllowsGifts() {
        GiftRoomValidationResult result = policy.validate(room(202L, 302L, 1, true));

        assertThat(result.roomId()).isEqualTo(202L);
        assertThat(result.anchorId()).isEqualTo(302L);
        assertThat(result.liveStatus()).isEqualTo(1);
        assertThat(result.acceptingGifts()).isTrue();
        assertThat(result.rejectionReason()).isNull();
    }

    @Test
    void exposesReadOnlyGiftValidationFromRoomEndpoint() {
        LiveRoomService liveRoomService = mock(LiveRoomService.class);
        GiftRoomValidationResult validation = new GiftRoomValidationResult(202L, 302L, 1, true, null);
        when(liveRoomService.validateGiftRoom(202L)).thenReturn(validation);
        LiveRoomController controller = new LiveRoomController();
        ReflectionTestUtils.setField(controller, "liveRoomService", liveRoomService);

        Result<GiftRoomValidationResult> result = controller.validateGiftRoom(202L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(validation);
    }

    private LiveRoom room(Long roomId, Long anchorId, int liveStatus, boolean acceptingGifts) {
        LiveRoom room = new LiveRoom();
        room.setId(roomId);
        room.setAnchorId(anchorId);
        room.setRoomStatus(liveStatus);
        room.setAcceptingGifts(acceptingGifts);
        return room;
    }
}
