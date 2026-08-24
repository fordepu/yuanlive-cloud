package blog.yuanyuan.yuanlive.ai.listener;

import blog.yuanyuan.yuanlive.ai.config.RiskMessageTopology;
import blog.yuanyuan.yuanlive.ai.strategy.RiskStrategies;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Argument;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@Slf4j
public class ChatDetectListener {
    @Resource(name = "riskWorkflow")
    private CompiledGraph riskWorkflow;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @RabbitListener(containerFactory = "reliableRabbitListenerContainerFactory", bindings = @QueueBinding(
            value = @Queue(value = "${live.mq.ai-detect.queue}", durable = "true", arguments = {
                    @Argument(name = "x-dead-letter-exchange", value = RiskMessageTopology.DEAD_LETTER_EXCHANGE),
                    @Argument(name = "x-dead-letter-routing-key", value = RiskMessageTopology.DEAD_LETTER_ROUTING_KEY)
            }),
            exchange = @Exchange(value = "${live.mq.ai-detect.exchange}", type = ExchangeTypes.DIRECT),
            key = "${live.mq.ai-detect.routing-key}" // 必须和发送端的 Routing Key 一致
    ))
    public void onRiskMessage(String messageStr) {
        log.info("收到风控审计任务: {}", messageStr);
        try {
            Map<String, Object> data = JSONUtil.parseObj(messageStr);
            String roomId = (String) data.get("roomId");
            String history = (String) data.get("history");

            // 准备初始状态（对应 RiskStrategies 定义的 Key）
            Map<String, Object> inputData = Map.of(
                    RiskStrategies.ROOM_ID, roomId,
                    RiskStrategies.CHAT_HISTORY, history // 这里传入收割到的整段文本
            );
            // 配置执行上下文
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(roomId)
                    .build();
            log.debug("--- 房间 [{}] 开始执行风控工作流 ---", roomId);
            // 使用 stream 并 blockLast 确保异步节点也能顺序执行完毕，直到遇到 END 或中断点
            riskWorkflow.stream(inputData, config)
                    .doOnNext(output ->
                            log.debug("房间 [{}] 节点 [{}] 执行完毕", roomId, output.node()))
                    .blockLast();

            StateSnapshot snapshot = riskWorkflow.getState(config);
            Integer score = (Integer) snapshot.state().data().get(RiskStrategies.RISK_SCORE);
            log.info("AI 判定分数: {}，当前所处位置: {}", score, snapshot.next());
            if (snapshot.next().contains("admin_review")) {
                log.warn("房间 [{}] 风控命中，已保留检查点等待人工审核", roomId);
                return;
            }
            log.info("房间 [{}] 风控流程正常结束,分数 {}, 最终执行决策 {}",
                    roomId,
                    score,
                    snapshot.state().data().get("last_action"));
            RiskStrategies.cleanRedis(roomId, stringRedisTemplate);
        } catch (Exception e) {
            log.error("风控工作流执行异常", e);
            throw new IllegalStateException("风控工作流执行失败", e);
        }
    }

}
