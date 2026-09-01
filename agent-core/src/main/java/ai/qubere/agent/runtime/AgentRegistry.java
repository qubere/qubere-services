package ai.qubere.agent.runtime;

import ai.qubere.agent.api.Agent;
import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class AgentRegistry {

    private static final Pattern SEMVER_PATTERN = Pattern.compile("\\d+(\\.\\d+){0,2}.*");

    public AgentRegistry(Collection<Agent<?, ?>> agents) {
        this(agents, new AgentPlatformProperties());
    }

    private final Map<String, Map<String, Agent<?, ?>>> agentsByIdAndVersion;
    private final Map<String, String> defaultVersions;

    public AgentRegistry(Collection<Agent<?, ?>> agents, AgentPlatformProperties properties) {
        AgentPlatformProperties safeProperties = properties == null ? new AgentPlatformProperties() : properties;
        this.defaultVersions = Map.copyOf(safeProperties.getRegistry().getDefaultVersions());
        this.agentsByIdAndVersion = indexAgents(
                agents == null ? List.of() : agents,
                safeProperties.getRegistry().isStrictDescriptorValidation()
        );
    }

    public Collection<AgentDescriptor> listAgents() {
        return agentsByIdAndVersion.values().stream()
                .flatMap(versionedAgents -> versionedAgents.values().stream())
                .map(Agent::descriptor)
                .toList();
    }

    public Optional<Agent<?, ?>> findAgent(String agentId) {
        String selectedVersion = selectVersion(agentId).orElse(null);
        return selectedVersion == null ? Optional.empty() : findAgent(agentId, selectedVersion);
    }

    public Optional<Agent<?, ?>> findAgent(String agentId, String version) {
        if (agentId == null || agentId.isBlank() || version == null || version.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(agentsByIdAndVersion.getOrDefault(agentId, Map.of()).get(version));
    }

    public Optional<RegisteredAgent> findRegisteredAgent(String agentId) {
        return selectVersion(agentId).flatMap(version -> findRegisteredAgent(agentId, version));
    }

    public Optional<RegisteredAgent> findRegisteredAgent(String agentId, String version) {
        return findAgent(agentId, version)
                .map(agent -> new RegisteredAgent(agent.descriptor(), agent, isDefaultVersion(agentId, version)));
    }

    public Collection<AgentDescriptor> listVersions(String agentId) {
        return agentsByIdAndVersion.getOrDefault(agentId, Map.of()).values().stream()
                .map(Agent::descriptor)
                .toList();
    }

    private Map<String, Map<String, Agent<?, ?>>> indexAgents(Collection<Agent<?, ?>> agents, boolean strictValidation) {
        Map<String, Map<String, Agent<?, ?>>> indexed = new LinkedHashMap<>();
        for (Agent<?, ?> agent : agents) {
            AgentDescriptor descriptor = agent == null ? null : agent.descriptor();
            if (strictValidation) {
                AgentDescriptorValidator.validate(descriptor);
            }
            if (descriptor == null || descriptor.id() == null || descriptor.version() == null) {
                continue;
            }
            Map<String, Agent<?, ?>> versions = indexed.computeIfAbsent(descriptor.id(), ignored -> new LinkedHashMap<>());
            Agent<?, ?> previous = versions.putIfAbsent(descriptor.version(), agent);
            if (previous != null) {
                throw new AgentDescriptorValidationException(
                        "Duplicate agent registration for id '%s' and version '%s'".formatted(descriptor.id(), descriptor.version())
                );
            }
        }
        Map<String, Map<String, Agent<?, ?>>> immutable = new HashMap<>();
        indexed.forEach((agentId, versions) -> immutable.put(agentId, Map.copyOf(versions)));
        return Map.copyOf(immutable);
    }

    private Optional<String> selectVersion(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return Optional.empty();
        }
        Map<String, Agent<?, ?>> versions = agentsByIdAndVersion.get(agentId);
        if (versions == null || versions.isEmpty()) {
            return Optional.empty();
        }
        String configuredDefault = defaultVersions.get(agentId);
        if (configuredDefault != null && versions.containsKey(configuredDefault)) {
            return Optional.of(configuredDefault);
        }
        return versions.keySet().stream().max(AgentRegistry::compareVersions);
    }

    private boolean isDefaultVersion(String agentId, String version) {
        return selectVersion(agentId)
                .map(version::equals)
                .orElse(false);
    }

    private static int compareVersions(String left, String right) {
        if (SEMVER_PATTERN.matcher(left).matches() && SEMVER_PATTERN.matcher(right).matches()) {
            return compareSemverLike(left, right);
        }
        return Comparator.<String>naturalOrder().compare(left, right);
    }

    private static int compareSemverLike(String left, String right) {
        String[] leftParts = left.split("[.-]");
        String[] rightParts = right.split("[.-]");
        int max = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < max; index++) {
            int comparison = Integer.compare(numericPart(leftParts, index), numericPart(rightParts, index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return left.compareTo(right);
    }

    private static int numericPart(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].replaceAll("\\D.*", ""));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
