/*
 * Copyright 2012 Vladimir Rudev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.crazycoder.plugins.tabdir;

import com.intellij.openapi.application.ApplicationManager;
import ru.crazycoder.plugins.tabdir.configuration.FolderConfiguration;
import ru.crazycoder.plugins.tabdir.configuration.GlobalConfig;

import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.apache.commons.lang3.StringUtils.getCommonPrefix;

/**
 * User: crazycoder
 * Date: Aug 21, 2010
 * Time: 9:28:09 AM
 */
public class TitleFormatter {

    private static final int MIN_DUPLICATE_LENGTH = 3;
    private static final GlobalConfig globalConfig = ApplicationManager.getApplication().getService(GlobalConfig.class);

    private static String joinPrefixesWithRemoveDuplication(LinkedHashMap<String, Set<String>> prefixes, FolderConfiguration configuration) {
        List<String> keys = new LinkedList<>(prefixes.keySet());
        keys = getPrefixesSublist(keys, configuration);
        List<String> resultPrefixes = new ArrayList<>(keys.size());
        for (String key : keys) {
            resultPrefixes.add(removeDuplicates(key, prefixes.get(key)));
        }
        StringBuilder buffer = join(resultPrefixes, configuration);
        String bufferString = buffer.toString();
        String dirSeparator = configuration.getDirSeparator();

        if (bufferString.endsWith(dirSeparator)) {
            return bufferString.substring(0, bufferString.length() - dirSeparator.length());
        }

        return bufferString;
    }

    /**
     * <ol>
     * <li>
     * Finds greatest common prefix of key and all neighbours,
     * this prefix replaced by first char and {@link FolderConfiguration#DUPLICATES_DELIMITER}.
     * </li>
     * <li>
     * Remove all neighbours that differs from key after removed common prefix.
     * </li>
     * <li>Doing 1, 2 until have neighbours and common prefixes.</li>
     * </ol>
     * See tests for examples.
     *
     * @param key        main folder name
     * @param neighbours folders with similar files in same level as key
     * @return key, with removed duplicates
     * @see ru.crazycoder.plugins.tabdir.SameFilenameTitleProviderTest#testRemoveMultiDuplicates()
     */
    @SuppressWarnings("JavadocReference")
    private static String removeDuplicates(final String key, Set<String> neighbours) {
        StringBuilder result = new StringBuilder();
        neighbours.remove(null);
        List<String> list = new ArrayList<>(neighbours);

        // this neighbours unnecessary
        list.removeIf(string -> getCommonPrefix(string, key).length() <= MIN_DUPLICATE_LENGTH);

        list.add(key);
        String commonPrefix = getCommonPrefix(list.toArray(new String[list.size()]));
        String suffix = key;
        while (!commonPrefix.isBlank()) {
            int prefixLength = commonPrefix.length();
            if (prefixLength == suffix.length()) {
                return result + suffix;
            }
            result.append(commonPrefix.charAt(0)).append(FolderConfiguration.DUPLICATES_DELIMITER);

            suffix = suffix.substring(prefixLength);
            List<String> newList = new ArrayList<>();
            for (String s : list) {
                String substring = s.substring(prefixLength);
                boolean notBlank = (!substring.isBlank()) && getCommonPrefix(substring, suffix).length() > MIN_DUPLICATE_LENGTH;
                if (notBlank) {
                    newList.add(substring);
                }
            }
            list = newList;
            commonPrefix = getCommonPrefix(list.toArray(new String[list.size()]));
        }
        return result + suffix;
    }

    public static String format(LinkedHashMap<String, Set<String>> prefixes, String tabName, FolderConfiguration configuration) {
        String joinedPrefixes = joinPrefixesWithRemoveDuplication(prefixes, configuration);

        String tabTitle = MessageFormat.format(configuration.getTitleFormat(), joinedPrefixes, tabName);

        tabTitle = getRegexedTabTitle(tabTitle);

        return tabTitle;
    }

    //-----simple format
    public static String format(List<String> prefixes, String tabName, FolderConfiguration configuration) {
        String joinedPrefixes = joinPrefixes(prefixes, configuration);
        String tabTitle = MessageFormat.format(configuration.getTitleFormat(), joinedPrefixes, tabName);

        tabTitle = getRegexedTabTitle(tabTitle);

        if (joinedPrefixes.isBlank()) {
            tabTitle = configuration.getEmptyPathReplacement() + tabTitle;
        }

        return tabTitle;
    }

    // tabTitle regex replacements
    public static String getRegexedTabTitle(String tabTitle) {
        String[] filenameRegexes = globalConfig.getFilenameRegexes().split("\\R");

        String result = tabTitle;
        for (String regex : filenameRegexes) {
            try {
                if (!regex.contains("::")) continue;
                String[] r = regex.split("[::]");
                if (!(r.length == 3 || r.length == 1)) continue;
                String match = r[0];
                String replacement = r.length == 3 ? r[2] : "";
                Pattern pattern = Pattern.compile(match, Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(result);
                result = matcher.replaceAll(replacement);
            } catch (PatternSyntaxException ex) {
                // Syntax error in the regular expression
            } catch (IllegalArgumentException ex) {
                // Syntax error in the replacement text (unescaped $ signs?)
            } catch (IndexOutOfBoundsException ex) {
                // Non-existent backreference used the replacement text
            }
        }
        return result;
    }

    private static String joinPrefixes(List<String> prefixes, FolderConfiguration configuration) {
        prefixes = getPrefixesSublist(prefixes, configuration);
        StringBuilder buffer = join(prefixes, configuration);
        String bufferString = buffer.toString();
        String dirSeparator = configuration.getDirSeparator();

        if (bufferString.endsWith(dirSeparator)) {
            return bufferString.substring(0, bufferString.length() - dirSeparator.length());
        }

        return bufferString;
    }

    private static List<String> getPrefixesSublist(List<String> prefixes, FolderConfiguration configuration) {
        int maxDirsToShow = configuration.getMaxDirsToShow();
        if (maxDirsToShow > 0 && maxDirsToShow < prefixes.size()) {
            int beginIndex = prefixes.size() - maxDirsToShow;
            int endIndex = prefixes.size();
            if (configuration.isCountMaxDirsFromStart()) {
                beginIndex = 0;
                endIndex = maxDirsToShow;
            }
            return prefixes.subList(beginIndex, endIndex);
        }
        return prefixes;
    }

    private static StringBuilder join(List<String> prefixes, FolderConfiguration configuration) {
        StringBuilder buffer = new StringBuilder();
        for (String prefix : prefixes) {
            if (configuration.isReduceDirNames()) {
                String reducedDir;
                if (configuration.isReduceDirNames() && prefix.contains(FolderConfiguration.DUPLICATES_DELIMITER)) {
                    int start = prefix.lastIndexOf(FolderConfiguration.DUPLICATES_DELIMITER);
                    reducedDir = prefix.substring(0, start + configuration.getCharsInName() + 1);
                } else {
                    reducedDir = prefix.substring(0, Math.min(prefix.length(), configuration.getCharsInName()));

                }
                buffer.append(reducedDir);
            } else {
                buffer.append(prefix);
            }
            buffer.append(configuration.getDirSeparator());
        }
        return buffer;
    }

    public static String example(FolderConfiguration configuration) {
        List<String> examplePrefixes = Arrays.asList("first", "second", "third", "fourth", "fifth", "sixth");
        String exampleFileName = "FileName";
        return format(examplePrefixes, exampleFileName, configuration);
    }
}

