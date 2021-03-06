/*
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exadel.aem.toolkit.core.maven;

import com.exadel.aem.toolkit.api.runtime.ExceptionHandler;
import com.exadel.aem.toolkit.core.exceptions.PluginException;
import com.exadel.aem.toolkit.core.util.PluginReflectionUtility;
import com.exadel.aem.toolkit.core.util.PluginXmlUtility;

/**
 * The fallback implementation of {@link PluginRuntimeContext} for the AEM Authoring Toolkit plugin instance that
 * has not been properly and completely initialized
 */
class EmptyRuntimeContext implements PluginRuntimeContext {
    private static final String NOT_INITIALIZED_EXCEPTION_MESSAGE = "Plugin was not properly initialized";

    /**
     * Throws a {@code PluginException} upon call, since no {@code PluginReflectionUtility} instance has been initialized
     */
    @Override
    public PluginReflectionUtility getReflectionUtility() {
        throw new PluginException(NOT_INITIALIZED_EXCEPTION_MESSAGE);
    }

    /**
     * Throws a {@code PluginException} upon call, since no {@code PluginReflectionUtility} instance has been initialized
     */
    @Override
    public ExceptionHandler getExceptionHandler() {
        throw new PluginException(NOT_INITIALIZED_EXCEPTION_MESSAGE);
    }

    /**
     * Throws a {@code PluginException} upon call, since no {@code PluginReflectionUtility} instance has been initialized
     */
    @Override
    public PluginXmlUtility getXmlUtility() {
        throw new PluginException(NOT_INITIALIZED_EXCEPTION_MESSAGE);
    }
}
