package blog.yuanyuan.yuanlive.wallet.query;

import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record CursorPage<T>(List<T> items, @JsonSerialize(using = ToStringSerializer.class) Long nextCursor) {
}
