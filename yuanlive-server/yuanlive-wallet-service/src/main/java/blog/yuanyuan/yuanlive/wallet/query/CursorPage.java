package blog.yuanyuan.yuanlive.wallet.query;

import java.util.List;

public record CursorPage<T>(List<T> items, Long nextCursor) {
}
