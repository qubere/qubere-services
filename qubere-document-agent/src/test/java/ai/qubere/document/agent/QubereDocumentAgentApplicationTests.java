package ai.qubere.document.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "agent-platform.prompts.provider=in-memory",
        "agent-platform.async.worker-enabled=false"
})
class QubereDocumentAgentApplicationTests {

    @Test
    void contextLoads() {
    }
}
