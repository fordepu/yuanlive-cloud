package blog.yuanyuan.yuanlive.live.service;

import blog.yuanyuan.yuanlive.entity.live.entity.LiveRoom;
import blog.yuanyuan.yuanlive.feign.live.dto.GiftRoomValidationResult;
import org.springframework.stereotype.Component;

@Component
public class GiftRoomValidationPolicy {

    public GiftRoomValidationResult validate(LiveRoom room) {
        if (room == null) {
            return new GiftRoomValidationResult(null, null, null, false, "ROOM_NOT_FOUND");
        }
        if (!Integer.valueOf(1).equals(room.getRoomStatus())) {
            return new GiftRoomValidationResult(
                    room.getId(), room.getAnchorId(), room.getRoomStatus(), false, "ROOM_NOT_LIVE");
        }
        if (!Boolean.TRUE.equals(room.getAcceptingGifts())) {
            return new GiftRoomValidationResult(
                    room.getId(), room.getAnchorId(), room.getRoomStatus(), false, "GIFTS_DISABLED");
        }
        return new GiftRoomValidationResult(room.getId(), room.getAnchorId(), room.getRoomStatus(), true, null);
    }
}
