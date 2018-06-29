/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.cache.internal;

import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.io.filefilter.FileFilterUtils.directoryFileFilter;

public class SharedVersionedCacheCleanup {

    private static final Logger LOGGER = Logging.getLogger(SharedVersionedCacheCleanup.class);

    private final File baseDir;
    private final String cacheName;
    private final Pattern cacheNamePattern;
    private final CacheVersionMapping cacheVersionMapping;
    private final GradleVersionProvider gradleVersionProvider;

    public SharedVersionedCacheCleanup(File baseDir, String cacheName, CacheVersionMapping cacheVersionMapping, GradleVersionProvider gradleVersionProvider) {
        this.baseDir = baseDir;
        this.cacheName = cacheName;
        this.cacheNamePattern = Pattern.compile('^' + Pattern.quote(cacheName) + "-(\\d+)$");
        this.cacheVersionMapping = cacheVersionMapping;
        this.gradleVersionProvider = gradleVersionProvider;
    }

    public void deleteUnusedCacheDirectories() {
        Timer timer = Time.startTimer();
        Set usedVersions = determineUsedCacheVersions();
        for (File cacheDir : listCacheDirs()) {
            Matcher matcher = cacheNamePattern.matcher(cacheDir.getName());
            if (matcher.matches()) {
                int version = Integer.parseInt(matcher.toMatchResult().group(1));
                if (version < cacheVersionMapping.getLatestVersion() && !usedVersions.contains(version)) {
                    LOGGER.debug("Deleting unused shared versioned cache directory at {}", cacheDir);
                    FileUtils.deleteQuietly(cacheDir);
                }
            }
        }
        LOGGER.debug("Processed shared versioned cache directories for name '{}' at {} in {}", cacheName, baseDir, timer.getElapsed());
    }

    private Set<Integer> determineUsedCacheVersions() {
        Set<Integer> usedVersions = Sets.newTreeSet();
        for (GradleVersion gradleVersion : gradleVersionProvider.getRecentlyUsedVersions().headSet(GradleVersion.current())) {
            usedVersions.addAll(cacheVersionMapping.getVersionUsedBy(gradleVersion).asSet());
        }
        return usedVersions;
    }

    private Collection<File> listCacheDirs() {
        FileFilter combinedFilter = FileFilterUtils.and(directoryFileFilter(), new RegexFileFilter(cacheNamePattern));
        File[] result = baseDir.listFiles(combinedFilter);
        return result == null ? Collections.<File>emptySet() : Arrays.asList(result);
    }
}
