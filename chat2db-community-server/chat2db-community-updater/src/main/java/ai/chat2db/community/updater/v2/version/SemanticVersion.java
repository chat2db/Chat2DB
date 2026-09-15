package ai.chat2db.community.updater.v2.version;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SemanticVersion implements Comparable<SemanticVersion> {

    private static final Pattern VERSION_PATTERN = Pattern.compile(
        "^(?:v)?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
            + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?"
            + "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$"
    );

    private final long major;
    private final long minor;
    private final long patch;
    private final List<String> preRelease;
    private final String buildMetadata;
    private final String value;

    private SemanticVersion(long major, long minor, long patch, List<String> preRelease,
            String buildMetadata, String value) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = List.copyOf(preRelease);
        this.buildMetadata = buildMetadata;
        this.value = value;
    }

    public static SemanticVersion parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Version is required");
        }
        Matcher matcher = VERSION_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid semantic version: " + value);
        }
        List<String> preRelease = splitIdentifiers(matcher.group(4));
        for (String identifier : preRelease) {
            if (isNumeric(identifier) && identifier.length() > 1 && identifier.charAt(0) == '0') {
                throw new IllegalArgumentException("Numeric prerelease identifiers cannot contain leading zeroes: " + value);
            }
        }
        return new SemanticVersion(
            parseNumber(matcher.group(1), value),
            parseNumber(matcher.group(2), value),
            parseNumber(matcher.group(3), value),
            preRelease,
            matcher.group(5),
            value.trim()
        );
    }

    private static long parseNumber(String number, String value) {
        try {
            return Long.parseLong(number);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Semantic version component is too large: " + value, exception);
        }
    }

    private static List<String> splitIdentifiers(String value) {
        if (value == null) {
            return List.of();
        }
        String[] identifiers = value.split("\\.", -1);
        List<String> result = new ArrayList<>(identifiers.length);
        for (String identifier : identifiers) {
            if (identifier.isEmpty()) {
                throw new IllegalArgumentException("Semantic version identifier cannot be empty");
            }
            result.add(identifier);
        }
        return result;
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int result = Long.compare(major, other.major);
        if (result != 0) {
            return result;
        }
        result = Long.compare(minor, other.minor);
        if (result != 0) {
            return result;
        }
        result = Long.compare(patch, other.patch);
        if (result != 0) {
            return result;
        }
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) {
            return 0;
        }
        if (preRelease.isEmpty()) {
            return 1;
        }
        if (other.preRelease.isEmpty()) {
            return -1;
        }
        int length = Math.min(preRelease.size(), other.preRelease.size());
        for (int index = 0; index < length; index++) {
            result = compareIdentifier(preRelease.get(index), other.preRelease.get(index));
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(preRelease.size(), other.preRelease.size());
    }

    private static int compareIdentifier(String left, String right) {
        boolean leftNumeric = isNumeric(left);
        boolean rightNumeric = isNumeric(right);
        if (leftNumeric && rightNumeric) {
            return compareNumericIdentifier(left, right);
        }
        if (leftNumeric != rightNumeric) {
            return leftNumeric ? -1 : 1;
        }
        return left.compareTo(right);
    }

    private static int compareNumericIdentifier(String left, String right) {
        int result = Integer.compare(left.length(), right.length());
        return result != 0 ? result : left.compareTo(right);
    }

    private static boolean isNumeric(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return !value.isEmpty();
    }

    public boolean isPreRelease() {
        return !preRelease.isEmpty();
    }

    public String buildMetadata() {
        return buildMetadata;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof SemanticVersion other)) {
            return false;
        }
        return compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, preRelease);
    }

    @Override
    public String toString() {
        return value;
    }
}
