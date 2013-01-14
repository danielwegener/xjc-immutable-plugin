package com.github.danielwegener.xjcimmutable;

import org.junit.Test;
import com.sun.codemodel.*;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.Collection;

public class XJCImmutableValueClassesPluginTest {

    private final XJCImmutableValueClassesPlugin plugin = new XJCImmutableValueClassesPlugin();
    private final JCodeModel aModel = new JCodeModel();
    private final JPackage aPackage;
    private final JDefinedClass aClass;

    private final JMethod aSetter;
    private final JFieldVar aField;
    private final JFieldVar anotherField;
    private final JFieldVar aStaticField;
    private final JMethod aGetter;
    private final JDefinedClass aSuperClass;
    private final JFieldVar aSuperClassField;

    public XJCImmutableValueClassesPluginTest() throws Exception {
        aPackage = aModel._package("test");
        aClass = aPackage._class("AClass");

        aSetter = aClass.method(JMod.PUBLIC, aModel.VOID, "setField");

        aField = aClass.field(JMod.PRIVATE, aModel.INT, "field");
        anotherField = aClass.field(JMod.PRIVATE, aModel.BOOLEAN, "anotherField");
        aStaticField = aClass.field(JMod.STATIC|JMod.PUBLIC,aModel.SHORT,"staticField");
        aGetter = aClass.method(JMod.PUBLIC, aModel.INT, "getField");
        aGetter.body()._return(aField);
        final JVar setterParam = aSetter.param(aModel.INT, "field");
        aSetter.body().assign(aField, setterParam);

        aSuperClass = aPackage._class("ASuperClass");
        aClass._extends(aSuperClass);
        aSuperClassField = aSuperClass.field(JMod.PRIVATE, aModel.DOUBLE, "superClassField");

    }


    @Test
    public void testClearBlock() throws Exception {
        assertThat(aGetter.body().getContents(), not(empty()));
        plugin.clearBlock(aGetter.body());
        assertThat(aGetter.body().getContents(), empty());
        plugin.clearBlock(aGetter.body());
        assertThat(aGetter.body().getContents(), empty());
    }

    @Test
    public void testDefaultValue() {
        assertThat(plugin.defaultValue(aModel, aModel.BOOLEAN), equalTo(JExpr.FALSE));
        assertThat(plugin.defaultValue(aModel, aModel.ref(java.util.Date.class)), equalTo(JExpr._null()));
        //assertThat(plugin.defaultValue(aModel, aModel.INT), equalTo(JExpr.lit(0)));
        //assertThat(plugin.defaultValue(aModel, aModel.DOUBLE), equalTo(JExpr.lit(0)));
        //assertThat(plugin.defaultValue(aModel, aModel.FLOAT), equalTo(JExpr.lit(0)));
    }

    @Test
    public void testFindGetter() {
        assertThat(plugin.findGetter(aClass,aField), equalTo(aGetter));
        assertThat(plugin.findGetter(aClass,anotherField), nullValue());
    }

    @Test
    public void testGetInstanceFields() {
        Collection<JFieldVar> instanceFields =  plugin.getInstanceFields(aClass.fields().values());
        assertThat(instanceFields, not(hasItem(aStaticField)));
        assertThat(instanceFields, not(empty()));
    }

    @Test
    public void testGetSuperclassFields() {
        assertThat(plugin.getSuperclassFields(aClass), equalTo(Arrays.asList(aSuperClassField)));
    }

    @Test
    public void testIsSetter() {
        assertThat(plugin.isSetter(aSetter), equalTo(true));
        assertThat(plugin.isSetter(aGetter), equalTo(false));
    }

    @Test
    public void testIsStatic() {
        assertThat(plugin.isStatic(aStaticField), equalTo(true));
        assertThat(plugin.isStatic(aField), equalTo(false));
    }

}
