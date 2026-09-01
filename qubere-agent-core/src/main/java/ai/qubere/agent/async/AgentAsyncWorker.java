package ai.qubere.agent.async;

import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.SmartLifecycle;

public class AgentAsyncWorker implements SmartLifecycle {

    private final AgentAsyncRuntimeService runtimeService;
    private final AgentPlatformProperties.Async properties;
    private final ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public AgentAsyncWorker(AgentAsyncRuntimeService runtimeService, AgentPlatformProperties properties) {
        this.runtimeService = runtimeService;
        this.properties = properties == null ? new AgentPlatformProperties.Async() : properties.getAsync();
        this.executorService = Executors.newSingleThreadExecutor(new WorkerThreadFactory());
    }

    @Override
    public void start() {
        if (!properties.isEnabled() || !properties.isWorkerEnabled() || !running.compareAndSet(false, true)) {
            return;
        }
        executorService.submit(this::runLoop);
    }

    @Override
    public void stop() {
        running.set(false);
        executorService.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void runLoop() {
        Duration pollInterval = Duration.ofMillis(properties.getPollIntervalMillis());
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                for (int i = 0; i < properties.getMaxRunsPerPoll(); i++) {
                    if (runtimeService.processNext().isEmpty()) {
                        break;
                    }
                }
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                running.set(false);
            } catch (RuntimeException ex) {
                // Keep the worker alive after a single failed execution. The execution store and
                // callback dispatcher already capture the individual run failure.
            }
        }
    }

    private static final class WorkerThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "agent-async-worker");
            thread.setDaemon(true);
            return thread;
        }
    }
}
