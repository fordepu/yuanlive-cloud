package blog.yuanyuan.yuanlive.live.service.exception;

public class InvalidGiftDeliveredEventException extends RuntimeException {

    public InvalidGiftDeliveredEventException(String message) {
        super(message);
    }

    public InvalidGiftDeliveredEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
