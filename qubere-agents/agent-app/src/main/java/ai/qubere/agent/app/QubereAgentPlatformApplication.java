package ai.qubere.agent.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "ai.qubere.agent")
public class QubereAgentPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(QubereAgentPlatformApplication.class, args);
    }
}
