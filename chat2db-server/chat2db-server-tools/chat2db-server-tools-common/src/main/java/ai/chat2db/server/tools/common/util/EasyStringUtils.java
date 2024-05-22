package ai.chat2db.server.tools.common.util;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * String utility class
 *
 * @author Jiaju Zhuang
 */
public class EasyStringUtils {
    /**
     * 0 characters
     */
    private static final char ZERO_CHAR = '0';

    /**
     * Remove the 0 in front of the job number
     *
     * @param userId employee ID
     * @return modified job number
     */
    public static String cutUserId(String userId) {
        if (!StringUtils.isNumeric(userId)) {
            return userId;
        }
        int startIndex = 0;
        for (int i = 0; i < userId.length(); i++) {
            char c = userId.charAt(i);
            // Query the first position that is not 0
            if (ZERO_CHAR == c) {
                startIndex = i + 1;
            } else {
                break;
            }
        }
        // Maybe the entire account is 0
        if (startIndex == userId.length()) {
            return "0";
        }
        return userId.substring(startIndex);
    }

    /**
     * Remove the job number after the flower name
     *
     * @param name name or nickname
     * @return the name or nickname after removing the work number
     */
    public static String cutName(String name, String workNo) {
        if (StringUtils.isBlank(workNo) || StringUtils.isBlank(name)) {
            return name;
        }
        // There may be 0 knots here
        String cutName = RegExUtils.removeFirst(name, workNo);
        int lastIndex = cutName.length();
        for (int i = cutName.length() - 1; i >= 0; i--) {
            char c = cutName.charAt(i);
            // Query the last position that is not 0
            if (ZERO_CHAR == c) {
                lastIndex = i;
            } else {
                break;
            }
        }
        return cutName.substring(0, lastIndex);
    }

    /**
     * Add 0 in front of the job number
     *
     * @param userId employee ID
     * @return modified job number
     */
    public static String padUserId(String userId) {
        if (!StringUtils.isNumeric(userId)) {
            return userId;
        }
        return StringUtils.leftPad(userId, 6, '0');
    }

    /**
     * Build the name of the display
     *
     * @param name name
     * @param nickName flower name
     * @return display name name (flower name)
     */
    public static String buildShowName(String name, String nickName) {
        StringBuilder showName = new StringBuilder();
        if (StringUtils.isNotBlank(name)) {
            showName.append(name);
        }
        if (StringUtils.isNotBlank(nickName)) {
            showName.append("(");
            showName.append(nickName);
            showName.append(")");
        }
        return showName.toString();
    }

    /**
     * Splice multiple strings together
     *
     * @param delimiter delimiter cannot be empty
     * @param elements string can be empty and empty strings will be ignored
     * @return
     */
    public static String join(CharSequence delimiter, CharSequence... elements) {
        if (elements == null) {
            return null;
        }
        List<CharSequence> charSequenceList = Arrays.stream(elements).filter(
            org.apache.commons.lang3.StringUtils::isNotBlank).collect(Collectors.toList());
        if (charSequenceList.isEmpty()) {
            return null;
        }
        return String.join(delimiter, charSequenceList);
    }

    /**
     * Limit the length of a string string. If it exceeds the length, it will be replaced with...
     *
     * @param str string
     * @param length limit length
     * @return
     */
    public static String limitString(String str, int length) {
        if (Objects.isNull(str)) {
            return null;
        }
        String limitString = StringUtils.substring(str, 0, length);
        if (limitString.length() == length) {
            limitString += "...";
        }
        return limitString;
    }

}
