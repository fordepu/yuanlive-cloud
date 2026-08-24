package blog.yuanyuan.yuanlive.ai.controller;

import blog.yuanyuan.yuanlive.ai.domain.dto.ChatRequest;
import blog.yuanyuan.yuanlive.ai.domain.vo.ChatChunk;
import blog.yuanyuan.yuanlive.ai.service.AiChatService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatControllerTest {

    @Test
    void forwardsNewConversationRequestsAsAnUnbufferedStream() {
        AiChatService service = mock(AiChatService.class);
        AiChatController controller = new AiChatController();
        ReflectionTestUtils.setField(controller, "aiChatService", service);
        ChatRequest request = new ChatRequest();
        request.setConversationId("conversation-1");
        request.setClientMsgId("client-message-1");
        request.setContent("hello");
        ChatChunk done = ChatChunk.done("client-message-1", "user-message-1", "ai-message-1", null, null);
        when(service.streamChat(request)).thenReturn(Flux.just(done));

        List<ChatChunk> chunks = controller.streamChat(request).collectList().block();

        assertEquals(List.of(done), chunks);
        verify(service).streamChat(request);
    }
}
