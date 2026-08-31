package blog.yuanyuan.yuanlive.live.realtime.dispatch;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** 消费本实例专属队列；目标 epoch 不匹配的消息会由本机投递器拒绝。 */
@Component
public class RealtimeDispatchConsumer {
    private final LocalRealtimeEventDelivery localDelivery;

    public RealtimeDispatchConsumer(LocalRealtimeEventDelivery localDelivery) {
        this.localDelivery = localDelivery;
    }

    @RabbitListener(queues = "#{@realtimeInstanceDispatchQueue.name}")
    public void consume(InstanceDispatchMessage message) {
        localDelivery.deliver(message);
    }
}
