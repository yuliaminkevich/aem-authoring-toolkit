/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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
package com.exadel.aem.toolkit.api.annotations.widgets.rte;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.exadel.aem.toolkit.api.annotations.meta.IgnoreValue;
import com.exadel.aem.toolkit.api.annotations.meta.ValueRestriction;

/**
 * Used to set up an entry in {@code RichTextEditor#specialCharacters()} array.
 * Represents a <a href="https://helpx.adobe.com/experience-manager/6-5/sites/administering/using/configure-rich-text-editor-plug-ins.html#main-pars_title_4">
 * name-entity pair</a>, or a <a href="https://helpx.adobe.com/experience-manager/6-5/sites/administering/using/configure-rich-text-editor-plug-ins.html#main-pars_title_5">
 *     range of character codes</a> for characters displayed in an "Insert Symbol" dialog
 * @see RichTextEditor
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ValueRestriction("CharactersObjectValidator")
@SuppressWarnings("unused")
public @interface Characters {
    /**
     * In a name-entity buildup, represents the name of a character entry
     * @return String value, non-blank
     */
    String name() default "";
    /**
     * In a name-entity buildup, represents the entity code of a character entry
     * @return String value, non-blank
     */
    String entity() default "";
    /**
     * In a range buildup, represents the first position in the Unicode subset
     * @return Long value, greater than zero
     */
    @IgnoreValue("0")
    long rangeStart() default 0;
    /**
     * In a range buildup, represents the last position in the Unicode subset
     * @return Long value, greater than zero
     */
    @IgnoreValue("0")
    long rangeEnd() default 0;
}
