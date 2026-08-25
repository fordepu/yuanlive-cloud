package blog.yuanyuan.yuanlive.live.service.exception;

public class TransientGiftDeliveryException extends RuntimeException {

    public TransientGiftDeliveryException(String message) {
        super(message);
    }

    public TransientGiftDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
