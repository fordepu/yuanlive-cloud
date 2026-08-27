package blog.yuanyuan.yuanlive.wallet.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record WalletAccountResult(@JsonSerialize(using = ToStringSerializer.class) Long userId,
                                  Long availableCoin, String status) {
}
