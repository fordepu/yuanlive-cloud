package blog.yuanyuan.yuanlive.live.realtime.routing;

/**
 * 一次进程生命周期内的实例身份。
 * instanceId 表示稳定实例地址，epoch 在每次启动变化，用来拒绝投递给同地址的旧进程。
 */
public record RealtimeInstanceIdentity(String instanceId, String epoch) {
    public String memberValue() {
        return instanceId + "|" + epoch;
    }
}
