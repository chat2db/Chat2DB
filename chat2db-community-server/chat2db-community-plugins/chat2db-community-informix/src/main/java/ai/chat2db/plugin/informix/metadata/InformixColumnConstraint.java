package ai.chat2db.plugin.informix.metadata;

/** Native Informix constraint name and catalog type (P, U, R, C, N, ...). */
public record InformixColumnConstraint(String name, String type) {
}
