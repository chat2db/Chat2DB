package ai.chat2db.community.domain.core.impl.task.imports;

import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.domain.api.model.task.ImportColumnMapping;
import ai.chat2db.community.domain.api.model.task.ImportTaskSpec;
import ai.chat2db.community.domain.api.model.task.UnmappedTargetStrategy;
import ai.chat2db.community.tools.exception.ParamBusinessException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves which file column feeds which table column. Explicit mappings win; otherwise matching is
 * case-insensitive, preserving whitespace as in the existing preview and ordinary importer.
 */
public final class ImportColumnResolver {

    /**
     * Ordered pair lists: entry {@code i} binds {@code fileValues[fileIndexes[i]]} to
     * {@code tableColumns[i]}.
     */
    public record Resolution(List<TableColumn> tableColumns, List<Integer> fileIndexes,
                             List<String> missingTableColumns) {
    }

    private ImportColumnResolver() {
    }

    public static Resolution resolveForSpec(List<TableColumn> tableColumns, List<String> fileHeaders,
            ImportTaskSpec spec) {
        return resolve(tableColumns, fileHeaders, spec.getColumnMappings(), spec.getUnmappedTarget());
    }

    public static void validateForImport(List<TableColumn> columns, Resolution resolution, ImportTaskSpec spec) {
        if (resolution.fileIndexes().stream().noneMatch(java.util.Objects::nonNull)) {
            throw new ParamBusinessException("At least one import column mapping is required");
        }
        for (TableColumn column : columns) {
            if (resolution.missingTableColumns().contains(column.getName())
                    && Integer.valueOf(0).equals(column.getNullable())
                    && !Boolean.TRUE.equals(column.getAutoIncrement())
                    && (spec.getUnmappedTarget() == UnmappedTargetStrategy.NULL || column.getDefaultValue() == null)) {
                throw new ParamBusinessException("Required import column is unmapped: " + column.getName());
            }
        }
    }

    private static Resolution resolve(List<TableColumn> tableColumns, List<String> fileHeaders,
            List<ImportColumnMapping> mappings, UnmappedTargetStrategy unmappedTarget) {
        Map<String, Integer> byNormalizedName = new LinkedHashMap<>();
        for (int index = 0; index < fileHeaders.size(); index++) {
            if (byNormalizedName.putIfAbsent(normalize(fileHeaders.get(index)), index) != null) {
                throw new ParamBusinessException("Duplicate import source column: " + fileHeaders.get(index));
            }
        }
        Map<String, Integer> explicitTargets = new LinkedHashMap<>();
        java.util.Set<Integer> explicitSources = new java.util.HashSet<>();
        java.util.Set<String> knownTargets = tableColumns.stream().map(column -> normalize(column.getName()))
                .collect(java.util.stream.Collectors.toSet());
        if (mappings != null) {
            for (ImportColumnMapping mapping : mappings) {
                if (mapping == null || StringUtils.isBlank(mapping.getSourceColumn())
                        || StringUtils.isBlank(mapping.getTargetColumn())) {
                    throw new ParamBusinessException("columnMappings");
                }
                Integer sourceIndex = byNormalizedName.get(normalize(mapping.getSourceColumn()));
                if (sourceIndex == null) {
                    throw new ParamBusinessException("columnMappings source: " + mapping.getSourceColumn());
                }
                String target = normalize(mapping.getTargetColumn());
                if (!knownTargets.contains(target) || !explicitSources.add(sourceIndex)
                        || explicitTargets.putIfAbsent(target, sourceIndex) != null) {
                    throw new ParamBusinessException("Duplicate or invalid import column mapping");
                }
            }
        }

        List<TableColumn> resolvedColumns = new ArrayList<>();
        List<Integer> fileIndexes = new ArrayList<>();
        List<String> missingTableColumns = new ArrayList<>();
        for (TableColumn column : tableColumns) {
            Integer sourceIndex = explicitTargets.get(normalize(column.getName()));
            if (sourceIndex == null && mappings == null) {
                sourceIndex = byNormalizedName.get(normalize(column.getName()));
            }
            if (sourceIndex != null) {
                resolvedColumns.add(column);
                fileIndexes.add(sourceIndex);
            } else {
                missingTableColumns.add(column.getName());
                if (mappings != null && unmappedTarget == UnmappedTargetStrategy.NULL
                        && !Boolean.TRUE.equals(column.getAutoIncrement())) {
                    resolvedColumns.add(column);
                    fileIndexes.add(null);
                }
            }
        }

        return new Resolution(resolvedColumns, fileIndexes, missingTableColumns);
    }

    private static String normalize(String name) {
        return name == null ? "" : name.toUpperCase(java.util.Locale.ROOT);
    }
}
