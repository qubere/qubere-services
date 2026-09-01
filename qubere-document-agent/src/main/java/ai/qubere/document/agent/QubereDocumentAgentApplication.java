package ai.qubere.document.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"ai.qubere.agent", "ai.qubere.document.agent"})
public class QubereDocumentAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(QubereDocumentAgentApplication.class, args);
    }
}
