/*
 * Copyright 2013 Daniel Wegener
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.github.danielwegener.xjcimmutable;

import com.sun.codemodel.*;
import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import org.xml.sax.ErrorHandler;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

/**
 * <p>Generate final fields, a constructor for all fields in property order and a protected default constructor that
 * initializes all fields with <code>null</code> or primitive default values. All setter methods are removed.</p>
 *
 * <strong>Plugin parameters</strong>
 * <dl>
 *  <dt>-Ximmutable-model:skipUnmodifiableCollections</dt>
 *  <dd>Disable wrapping of collections with unmodifiable wrapper.</dd>
 * </dl>
 * Inspired by jaxb2-commons <a href="http://java.net/projects/jaxb2-commons/pages/Default-Value">Default Value plugin </a>
 *
 * @author Daniel Wegener
 * @author Kenny MacLeod
 */
public class XjcImmutableValueClassesPlugin extends Plugin {

    public static final String OPTION_NAME = "Ximmutable-model";
    public static final String SKIP_UNMODIFIABLE_COLLECTIONS_PARAM = "-"+OPTION_NAME + ":skipUnmodifiableCollections";

    @Override
    public String getOptionName() {
        return OPTION_NAME;
    }

    @Override
    public String getUsage() {
        return "  -" + OPTION_NAME + "\t:  enable generation of immutable domain model"
             + "\n    -" + SKIP_UNMODIFIABLE_COLLECTIONS_PARAM + "\t:  dont wrap collection parameters with Collections.unmodifiable...";

    }

    @Override
    public int parseArgument(Options opt, String[] args, int i) throws BadCommandLineException, IOException {

        final String arg = args[i].trim();
        if (SKIP_UNMODIFIABLE_COLLECTIONS_PARAM.equals(arg)) {
            skipUnmodifiableCollections = true;
            return 1;
        }
        return 0;
    }

    private boolean skipUnmodifiableCollections = false;

    @Override
    public boolean run(final Outline outline, final Options options, final ErrorHandler errorHandler) {
        // For each defined class
        final JCodeModel model = outline.getCodeModel();
        for (final ClassOutline classOutline : outline.getClasses()) {

            final JDefinedClass implClass = classOutline.implClass;
            final Collection<JFieldVar> superClassInstanceFields = getInstanceFields(getSuperclassFields(implClass));
            final Collection<JFieldVar> thisClassInstanceFields = getInstanceFields(implClass.fields().values());



            for (final JFieldVar field : thisClassInstanceFields) {

                // Make field final
                field.mods().setFinal(true);

                final JMethod getter = findGetter(implClass,field);
                if (getter != null) {
                    returnEmptyCollectionInsteadOfReassignFinalValue(model,getter,field);
                }


            }

            // Remove setters
            final List<JMethod> toRemove = new LinkedList<JMethod>();
            for (JMethod method : implClass.methods()) {
                if (isSetter(method))
                    toRemove.add(method);
            }
            implClass.methods().removeAll(toRemove);


            final boolean createPrimaryCtor = thisClassInstanceFields.size() + superClassInstanceFields.size() > 0;
            // Create the skeleton of the value constructor
            if (createPrimaryCtor) {
                final JMethod valueConstructor = implClass.constructor(JMod.PUBLIC);

                // If our superclass is also being generated, then we can assume it will also have
                // its own value constructor, so we add an invocation of that constructor.
                if (implClass._extends() instanceof JDefinedClass) {
                    final JInvocation superInvocation = valueConstructor.body().invoke("super");
                    // Add each argument to the super constructor.
                    for (JFieldVar superClassField : superClassInstanceFields) {
                        final JVar arg = valueConstructor.param(JMod.FINAL, superClassField.type(), superClassField.name());
                        superInvocation.arg(arg);
                    }
                }

                // Now add constructor parameters for each field in "this" class, and assign them to
                // our fields.
                for (final JFieldVar field : thisClassInstanceFields) {
                    final JVar arg = valueConstructor.param(JMod.FINAL, field.type(), field.name());
                    final JExpression wrappedParameter = wrapCollectionsWithUnmodifiable(model, arg);
                    valueConstructor.body().assign(JExpr.refthis(field.name()), wrappedParameter);
                }

            }

            final boolean createDefaultCtor = !thisClassInstanceFields.isEmpty();

            if (createDefaultCtor) {
                // Create the default, no-arg constructor
                final JMethod defaultConstructor = implClass.constructor(JMod.PROTECTED);
                final JAnnotationUse suppressWarningAnnotation = defaultConstructor.annotate(SuppressWarnings.class);
                suppressWarningAnnotation.param("value", "unused");
                defaultConstructor.javadoc().add("Used by JAX-B");
                defaultConstructor.body().invoke("super");

                if (implClass._extends() instanceof JDefinedClass) {
                    final JInvocation defaultSuperInvocation = defaultConstructor.body().invoke("super");
                    for (JFieldVar superClassField : superClassInstanceFields) {
                        defaultSuperInvocation.arg(defaultValue(model, superClassField.type()));
                    }
                }

                for (final JFieldVar field : thisClassInstanceFields) {
                    defaultConstructor.body().assign(JExpr.refthis(field.name()), defaultValue(model, field.type()));
                }
            }

        }


        return true;
    }

    protected boolean isSetter(JMethod method) {
        return method.name().startsWith("set") && method.params().size() == 1;
    }

    protected JExpression defaultValue(JCodeModel model, JType type) {
        if (type.isPrimitive()) {
            if (model.BOOLEAN.equals(type))
                return JExpr.lit(false);
            else
                return JExpr.lit(0);
        }
        return JExpr._null();
    }

    protected JMethod findGetter(JDefinedClass clazz, JFieldVar field) {
        final String fieldName = field.name();
        final String  getterName =  "get"+Character.toUpperCase(fieldName.charAt(0))+fieldName.substring(1);
        return clazz.getMethod(getterName, new JType[0]);
    }

    /**
     * Takes a collection of fields, and returns a new collection containing only the instance
     * (i.e. non-static) fields.
     */
    protected Collection<JFieldVar> getInstanceFields(final Collection<JFieldVar> fields) {
        final List<JFieldVar> instanceFields = new ArrayList<JFieldVar>();
        for (final JFieldVar field : fields) {
            if (!isStatic(field)) {
                instanceFields.add(field);
            }
        }
        return instanceFields;
    }

    protected List<JFieldVar> getSuperclassFields(final JDefinedClass implClass) {
        final List<JFieldVar> fieldList = new LinkedList<JFieldVar>();

        JClass superclass = implClass._extends();
        while (superclass instanceof JDefinedClass) {
            fieldList.addAll(0, ((JDefinedClass) superclass).fields().values());
            superclass = superclass._extends();
        }

        return fieldList;
    }

    /**
     * replaces for supported CollectionTypes
     * <pre>
     *   CollectionType&lt;T&gt; getX() {
     *      if (x == null) return new CollectionTypeImpl&lt;T&gt;();
     *      else return x;
     *  }
     * </pre>
     * with
     * <pre>
     *     CollectionType&lt;T&gt; getX() {
     *      if (x == null) return Collections.emptyCollectionType();
     *      else return x;
     *     }
     * </pre>
     */
    protected void returnEmptyCollectionInsteadOfReassignFinalValue(JCodeModel model, JMethod getter, JFieldVar field) {
        final JClass collectionType = model.ref(Collection.class);
        final JClass listType = model.ref(List.class);
        final JClass setType = model.ref(Set.class);
        final JClass mapType = model.ref(Map.class);
        final JClass sortedSetType = model.ref(SortedSet.class);
        final JClass sortedMapType = model.ref(SortedMap.class);
        final JClass collections = model.ref(Collections.class);

        final JType returnType = getter.type();
        final JInvocation emptyCollection;
        if (returnType.erasure().equals(collectionType)) {
            emptyCollection = collections.staticInvoke("emptyList");
        } else if (returnType.erasure().equals(listType)) {
            emptyCollection = collections.staticInvoke("emptyList");
        } else if (returnType.erasure().equals(setType)) {
            emptyCollection = collections.staticInvoke("emptySet");
        } else if (returnType.erasure().equals(mapType)) {
            emptyCollection = collections.staticInvoke("emptyMap");
        } else if (returnType.erasure().equals(sortedSetType)) {
            emptyCollection = JExpr._new(sortedSetType);
        } else if (returnType.erasure().equals(sortedMapType)) {
            emptyCollection = JExpr._new(sortedMapType);
        } else {
            //nothing to change, its not a collection
            return;
        }

        final JBlock body = getter.body();
       clearBlock(body);

        final JConditional ifBlock = body._if(field.eq(JExpr._null()));
        ifBlock._then()._return(emptyCollection);
        ifBlock._else()._return(field);
        getter.javadoc().append("The returned collection is unmodifiable.");
    }


    protected void clearBlock(JBlock block) {
        //FIXME: This is bad and not spec-conform
        try {
            final Field bodyContentField = JBlock.class.getDeclaredField("content");
            bodyContentField.setAccessible(true);
            final List<?> content;
            content = (List<?>)bodyContentField.get(block);
            content.clear();
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Could not reset JBlock",e);
        }  catch (IllegalAccessException e) {
            throw new RuntimeException("Could not reset JBlock",e);
        }
        block.pos(0);
    }

    protected JExpression wrapCollectionsWithUnmodifiable(JCodeModel model, JVar var) {
        if (skipUnmodifiableCollections) return var;
        final JClass collectionType = model.ref(Collection.class);
        final JClass listType = model.ref(List.class);
        final JClass setType = model.ref(Set.class);
        final JClass mapType = model.ref(Map.class);
        final JClass sortedSetType = model.ref(SortedSet.class);
        final JClass sortedMapType = model.ref(SortedMap.class);
        final JClass collections = model.ref(Collections.class);

        if (var.type().erasure().equals(collectionType)) {
            return collections.staticInvoke("unmodifiableCollection").arg(var);
        } else if (var.type().erasure().equals(listType)) {
            return collections.staticInvoke("unmodifiableList").arg(var);
        } else if (var.type().erasure().equals(setType)) {
            return collections.staticInvoke("unmodifiableSet").arg(var);
        } else if (var.type().erasure().equals(mapType)) {
            return collections.staticInvoke("unmodifiableMap").arg(var);
        } else if (var.type().erasure().equals(sortedSetType)) {
            return collections.staticInvoke("unmodifiableSortedSet").arg(var);
        } else if (var.type().erasure().equals(sortedMapType)) {
            return collections.staticInvoke("unmodifiableSortedMap").arg(var);
        }
        return var;
    }

    protected boolean isStatic(final JFieldVar field) {
        final JMods fieldMods = field.mods();
        return (fieldMods.getValue() & JMod.STATIC) > 0;
    }
}