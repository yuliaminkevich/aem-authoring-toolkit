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
package com.exadel.aem.toolkit.core.util;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;

import com.exadel.aem.toolkit.api.annotations.main.Dialog;
import com.exadel.aem.toolkit.api.annotations.widgets.DialogField;
import com.exadel.aem.toolkit.api.annotations.widgets.IgnoreField;
import com.exadel.aem.toolkit.api.handlers.DialogHandler;
import com.exadel.aem.toolkit.api.handlers.DialogWidgetHandler;
import com.exadel.aem.toolkit.api.runtime.Injected;
import com.exadel.aem.toolkit.api.runtime.RuntimeContext;
import com.exadel.aem.toolkit.core.exceptions.ExtensionApiException;
import com.exadel.aem.toolkit.core.maven.PluginRuntime;
import com.exadel.aem.toolkit.core.maven.PluginRuntimeContext;

/**
 * Contains utility methods for manipulating AEM components Java classes, their fields, and the annotations these fields are marked with
 */
public class PluginReflectionUtility {
    /**
     * Default all-allowed predicate for {@code Field} instances
     */
    private static final Predicate<Field> TRUE_PREDICATE = field -> true;
    /**
     * Predicate for picking out non-static {@code Field} instances
     */
    private static final Predicate<Field> NON_STATIC_FIELD_PREDICATE = field -> !Modifier.isStatic(field.getModifiers());
    /**
     * Predicate for picking out {@code Field} instances marked with {@code IgnoreField} annotation
     */
    private static final Predicate<Field> IGNORE_PROPERTY = field -> field.getAnnotation(IgnoreField.class) != null;
    /**
     * Comparison function for sorting {@code Field} based on their {@code DialogField} annotations' ranking values
     */
    private static final Comparator<Field> FIELD_RANKING_COMPARATOR = (f1, f2) -> {
        int rank1 = 0;
        int rank2 = 0;
        if (f1.isAnnotationPresent(DialogField.class)) {
            DialogField dialogField1 = f1.getAnnotationsByType(DialogField.class)[0];
            rank1 = dialogField1.ranking();
        }
        if (f2.isAnnotationPresent(DialogField.class)) {
            DialogField dialogField2 = f2.getAnnotationsByType(DialogField.class)[0];
            rank2 = dialogField2.ranking();
        }
        if (rank1 != rank2) {
            return Integer.compare(rank1, rank2);
        }
        if (f1.getDeclaringClass() != f2.getDeclaringClass()) {
            if (ClassUtils.isAssignable(f1.getDeclaringClass(), f2.getDeclaringClass())) {
                return 1;
            }
            if (ClassUtils.isAssignable(f2.getDeclaringClass(), f1.getDeclaringClass())) {
                return -1;
            }
        }
        return 0;
    };
    private static final String PACKAGE_BASE_WILDCARD = ".*";

    private org.reflections.Reflections reflections;
    private List<DialogWidgetHandler> customDialogWidgetHandlers;
    private List<DialogHandler> customDialogHandlers;
    private String packageBase;


    private PluginReflectionUtility() {
    }

    /**
     * Used to initialize {@code PluginReflectionUtility} instance based on list of available classpath entries in the
     * scope of this Maven plugin
     * @param elements List of classpath elements
     * @param packageBase String representing package prefix of processable AEM backend components, like {@code com.acme.aem.components.*}.
     *                      If not specified, all available components will be processed
     * @return {@link PluginReflectionUtility} instance
     */
    public static PluginReflectionUtility fromCodeScope(List<String> elements, String packageBase) {
        URL[] urls = new URL[] {};
        if (elements != null) {
            urls = elements.stream()
                    .map(File::new)
                    .map(File::toURI)
                    .map(PluginReflectionUtility::toUrl)
                    .filter(Objects::nonNull).toArray(URL[]::new);
        }
        Reflections reflections = new org.reflections.Reflections(new ConfigurationBuilder()
                .addClassLoader(new URLClassLoader(urls, PluginReflectionUtility.class.getClassLoader()))
                .setUrls(urls)
                .setScanners(new TypeAnnotationsScanner(), new SubTypesScanner()));
        PluginReflectionUtility newInstance = new PluginReflectionUtility();
        newInstance.reflections = reflections;
        newInstance.packageBase = StringUtils.strip(StringUtils.defaultString(packageBase, StringUtils.EMPTY),
                PACKAGE_BASE_WILDCARD);
        return newInstance;
    }

    /**
     * Initializes as necessary and returns collection of {@code CustomDialogComponentHandler}s defined within the Compile
     * scope the plugin is operating in
     * @return {@code List<DialogWidgetHandler>} of instances
     */
    public List<DialogWidgetHandler> getCustomDialogWidgetHandlers() {
        if (customDialogWidgetHandlers != null) {
            return customDialogWidgetHandlers;
        }
        customDialogWidgetHandlers = getHandlers(DialogWidgetHandler.class);
        return customDialogWidgetHandlers;
    }

    /**
     * Initializes as necessary and returns collection of {@code CustomDialogHandler}s defined within the Compile
     * scope the plugin is operating in
     * @return {@code List<DialogHandler>} of instances
     */
    List<DialogHandler> getCustomDialogHandlers() {
        if (customDialogHandlers != null) {
            return customDialogHandlers;
        }
        customDialogHandlers = getHandlers(DialogHandler.class);
        return customDialogHandlers;
    }

    /**
     * Returns list of {@code @Dialog}-annotated classes within the Compile scope the plugin is operating in, to
     * determine which of the component folders to process.
     * If {@code componentsPath} is set for this instance, classes are tested to be under that path
     * @return {@code List<Class>} of instances
     */
    public List<Class<?>> getComponentClasses() {
        return reflections.getTypesAnnotatedWith(Dialog.class, true).stream()
                .filter(cls -> StringUtils.isEmpty(packageBase) || cls.getName().startsWith(packageBase))
                .collect(Collectors.toList());
    }

    /**
     * Gets generic list of handler instances invoked from all available derivatives of specified handler {@code Class}.
     * Each is supplied with a reference to {@link PluginRuntimeContext} as required
     * @param handlerClass {@code Class} object
     * @param <T> Expected handler type
     * @return {@link List<T>} of instances
     */
    private <T> List<T> getHandlers(Class<? extends T> handlerClass) {
        return reflections.getSubTypesOf(handlerClass).stream()
                .map(PluginReflectionUtility::getHandlerInstance)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Creates new instance object of a handler {@code Class} and populates {@link RuntimeContext} instance to
     * every field annotated with {@link Injected}
     * @param handlerClass The class to instantiate
     * @param <T> Instance type
     * @return New object instance
     */
    private static <T> T getHandlerInstance(Class<? extends T> handlerClass) {
        try {
            T instance = handlerClass.getConstructor().newInstance();
            Arrays.stream(handlerClass.getDeclaredFields())
                    .filter(field -> field.isAnnotationPresent(Injected.class)
                            && ClassUtils.isAssignable(field.getType(), RuntimeContext.class))
                    .forEach(field -> populateRuntimeContext(instance, field));
            return instance;
        } catch (InstantiationException
                | IllegalAccessException
                | NoSuchMethodException
                | InvocationTargetException e) {
            PluginRuntime.context().getExceptionHandler().handle(new ExtensionApiException(handlerClass, e));
            return null;
        }
    }

    /**
     * Used to set a reference to {@link PluginRuntimeContext} to the handler instance
     * @param handler Handler instance
     * @param field The field of handler to populate
     */
    private static void populateRuntimeContext(Object handler, Field field) {
        field.setAccessible(true);
        try {
            field.set(handler, PluginRuntime.context());
        } catch (IllegalAccessException e) {
            PluginRuntime.context().getExceptionHandler().handle(new ExtensionApiException(handler.getClass(), e));
        }
    }

    /**
     * Retrieves annotations of a {@code Field} instance as a sequential {@code Stream}
     * @param field The field to analyze
     * @return Stream of annotation objects,
     */
    public static Stream<Class<? extends Annotation>> getFieldAnnotations(Field field){
        return Arrays.stream(field.getDeclaredAnnotations())
                .map(Annotation::annotationType);
    }

    /**
     * Analyzes a {@code Class} and retrieves {@code List} of its {@code Field}s marked with {@code IgnoreField} annotation
     * @param targetClass The class to analyze
     * @return List of {@code Field} objects
     */
    public static List<Field> getAllIgnoredFields(Class<?> targetClass) {
        return getAllFields(targetClass, Collections.singletonList(IGNORE_PROPERTY), null);
    }

    /**
     * Analyzes a {@code Class} and retrieves {@code List} of its non-static {@code Field}s
     * @param targetClass The class to analyze
     * @return List of {@code Field} objects
     */
    public static List<Field> getAllNonStaticFields(Class<?> targetClass) {
        return getAllFields(targetClass, Collections.singletonList(NON_STATIC_FIELD_PREDICATE), FIELD_RANKING_COMPARATOR);
    }

    /**
     * Retrieves a complete {@code Field} list of a {@code Class}
     * @param targetClass The class to analyze
     * @return List of {@code Field} objects
     */
    public static List<Field> getAllFields(Class<?> targetClass) {
        return getAllFields(targetClass, Collections.emptyList(), FIELD_RANKING_COMPARATOR);
    }

    /**
     * Retrieves an ordered list of all {@code Field}s of a certain {@code Class} that match specific criteria
     * @param targetClass The class to analyze
     * @param predicates List of {@code Predicate<Field>} instances to pick up appropriate fields
     * @param comparator Comparison function to put picked fields in special order
     * @return List of {@code Field} objects
     */
    private static List<Field> getAllFields(Class<?> targetClass, List<Predicate<Field>> predicates, Comparator<Field> comparator) {
        List<Field> fields = new LinkedList<>();
        Predicate<Field> predicate = TRUE_PREDICATE;
        if (predicates != null && !predicates.isEmpty()) {
            predicate = predicates.stream().filter(Objects::nonNull).reduce(TRUE_PREDICATE, Predicate::and);
        }
        for (Class<?> clazz : getAllSuperClasses(targetClass)) {
            List<Field> classFields = Arrays.stream(clazz.getDeclaredFields())
                    .filter(predicate)
                    .collect(Collectors.toList());
            fields.addAll(classFields);
        }
        if (comparator != null) fields.sort(comparator);
        return fields;
    }

    /**
     * Retrieves type of object(s) returned by the method. If method is designed to provide single object, its type
     * is returned. But if method supplies an array, type of array's element is returned
     * @param method The method to analyze
     * @return Appropriate {@code Class} instance
     */
    public static Class<?> getMethodPlainType(Method method) {
        return method.getReturnType().isArray()
                ? method.getReturnType().getComponentType()
                : method.getReturnType();
    }

    /**
     * Retrieves the sequential list of superclasses of a specific {@code Class}, including this class itself.
     * Closer ancestors are enumerated first
     * @param targetClass The class to analyze
     * @return List of {@code Class} objects
     */
    private static List<Class<?>> getAllSuperClasses(Class<?> targetClass) {
        List<Class<?>> superClasses = new LinkedList<>();
        if (targetClass != null && !targetClass.isInterface() && !targetClass.equals(Object.class)) {
            superClasses.add(targetClass);
            Class<?> superClass = targetClass.getSuperclass();
            if (superClass != null) {
                superClasses.addAll(getAllSuperClasses(superClass));
            }
        }
        return superClasses;
    }

    /**
     * Retrieves list of properties of an {@code Annotation} object to which non-default values have been set
     * @param annotation The annotation instance to analyze
     * @return List of {@code Method} instances that represent properties initialized with non-defaults
     */
    public static List<Method> getAnnotationNonDefaultProperties(Annotation annotation) {
        return Arrays.stream(annotation.annotationType().getDeclaredMethods())
                .filter(method -> annotationPropertyIsNotDefault(annotation, method))
                .collect(Collectors.toList());
    }

    /**
     * Gets whether an {@code Annotation} property has a value which is not default
     * @param annotation The annotation to analyze
     * @param method The method representing the property
     * @return True or false
     */
    static boolean annotationPropertyIsNotDefault(Annotation annotation, Method method) {
        try {
            Object defaultValue = method.getDefaultValue();
            if (defaultValue == null) {
                return true;
            }
            Object invocationResult = method.invoke(annotation);
            if (method.getReturnType().isArray() && ArrayUtils.isEmpty((Object[])invocationResult)) {
                return false;
            }
            return !defaultValue.equals(invocationResult);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return true;
        }
    }

    /**
     * Gets whether any of the {@code Annotation}'s properties has a value which is not default
     * @param annotation The annotation to analyze
     * @return True or false
     */
    public static boolean annotationIsNotDefault(Annotation annotation) {
        return Arrays.stream(annotation.annotationType().getDeclaredMethods())
                .anyMatch(method -> annotationPropertyIsNotDefault(annotation, method));
    }

    /**
     * Converts {@link URI} parameter, such as of a classpath element, to an {@link URL} instance used by {@link Reflections}
     * @param uri {@code URI} value
     * @return {@code URL} value
     */
    private static URL toUrl(URI uri){
        try {
            return uri.toURL();
        } catch (MalformedURLException e) {
            PluginRuntime.context().getExceptionHandler().handle(e);
        }
        return null;
    }
}
