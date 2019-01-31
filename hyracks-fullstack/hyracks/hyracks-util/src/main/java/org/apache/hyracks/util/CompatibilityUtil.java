/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.hyracks.util;

import java.io.IOException;
import java.lang.reflect.Field;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CompatibilityUtil {
    private static final Logger LOGGER = LogManager.getLogger();

    private CompatibilityUtil() {
    }

    public static Object readField(Object obj, String fieldName) throws IOException {
        Class<?> objClass = obj.getClass();
        LOGGER.debug("reading field '{}' on object of type {}", fieldName, objClass);
        try {
            Field f = objClass.getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(obj);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOGGER.warn("exception reading field '{}' on object of type {}", fieldName, objClass, e);
            throw new IOException(e);
        }
    }

    public static void writeField(Object obj, String fieldName, Object newValue) throws IOException {
        Class<?> objClass = obj.getClass();
        LOGGER.debug("updating field '{}' on object of type {} to {}", fieldName, objClass, newValue);
        try {
            Field f = objClass.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(obj, newValue);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOGGER.warn("exception updating field '{}' object of type {} to {}", fieldName, objClass, newValue, e);
            throw new IOException(e);
        }
    }
}
