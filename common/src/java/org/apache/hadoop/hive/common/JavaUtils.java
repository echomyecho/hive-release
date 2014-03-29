/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Arrays;

/**
 * Collection of Java class loading/reflection related utilities common across
 * Hive.
 */
public final class JavaUtils {

  private static final Log LOG = LogFactory.getLog(JavaUtils.class);
  private static final Method SUN_MISC_UTIL_RELEASE;

  static {
    if (Closeable.class.isAssignableFrom(URLClassLoader.class)) {
      SUN_MISC_UTIL_RELEASE = null;
    } else {
      Method release = null;
      try {
        Class<?> clazz = Class.forName("sun.misc.ClassLoaderUtil");
        release = clazz.getMethod("releaseLoader", URLClassLoader.class);
      } catch (Exception e) {
        // ignore
      }
      SUN_MISC_UTIL_RELEASE = release;
    }
  }

  /**
   * Standard way of getting classloader in Hive code (outside of Hadoop).
   * 
   * Uses the context loader to get access to classpaths to auxiliary and jars
   * added with 'add jar' command. Falls back to current classloader.
   * 
   * In Hadoop-related code, we use Configuration.getClassLoader().
   */
  public static ClassLoader getClassLoader() {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    if (classLoader == null) {
      classLoader = JavaUtils.class.getClassLoader();
    }
    return classLoader;
  }

  public static void closeClassLoadersTo(ClassLoader current, ClassLoader stop) {
    if (!isValidHierarchy(current, stop)) {
      return;
    }
    for (; current != null && current != stop; current = current.getParent()) {
      try {
        closeClassLoader(current);
      } catch (IOException e) {
        LOG.info("Failed to close class loader " + current +
            Arrays.toString(((URLClassLoader) current).getURLs()), e);
      }
    }
  }

  // check before closing loaders, not to close app-classloader, etc. by mistake
  private static boolean isValidHierarchy(ClassLoader current, ClassLoader stop) {
    if (current == null || stop == null || current == stop) {
      return false;
    }
    for (; current != null && current != stop; current = current.getParent()) {
    }
    return current == stop;
  }

  // best effort to close
  // see https://issues.apache.org/jira/browse/HIVE-3969 for detail
  public static void closeClassLoader(ClassLoader loader) throws IOException {
    if (loader instanceof Closeable) {
      ((Closeable)loader).close();
    } else if (SUN_MISC_UTIL_RELEASE != null && loader instanceof URLClassLoader) {
      try {
        SUN_MISC_UTIL_RELEASE.invoke(null, loader);
      } catch (InvocationTargetException e) {
        if (e.getTargetException() instanceof IOException) {
          throw (IOException)e.getTargetException();
        }
        throw new IOException(e.getTargetException());
      } catch (Exception e) {
        throw new IOException(e);
      }
    }
  }

  private JavaUtils() {
    // prevent instantiation
  }
}
