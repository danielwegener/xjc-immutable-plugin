xjc-immutable-plugin
====================

Ever got lost in the JAX-WS wsgen swamp on the way to highroad of immutable models?

This XJC (JAX-B Compiler) Plugin comes for the rescue and provides the generation of
immutable value classes from xml schema. To be honest, this is not JAX-B
standard conform, but it should work in most implementations.

Profit!

Example
---------------------
Instead of standard JAX-B beans like in the following example
```
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "thunderbolt", propOrder = {
    "intensity"
})
public class Thunderbolt {

    protected Double intensity;

    public Double getIntensity() {
        return intensity;
    }

    public void setIntensity(Double value) {
        this.intensity = value;
    }
}
```

This plugin generates immutable, thread-safe and still serializable value classes like in the following example.
```
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "thunderbolt", propOrder = {
    "intensity"
})
public class Thunderbolt {

    protected final Double intensity;

    public Thunderbolt(final Double intensity) {
        this.intensity = intensity;
    }

    @SuppressWarnings("unused")
    protected Thunderbolt() {
        super();
        this.intensity = null;
    }

    public Double getIntensity() {
        return intensity;
    }

}
```

This plugin
- marks all fields final and removes all setter methods
- creates a public constructor with all fields as parameters
- creates a protected no-arg constructor, initializing fields with null (primitives with zero or false)
- wraps all collection like parameters with Collection.unmodifiable views
- within getters, if a collection typed field is null, a Collection.emptyX is returned
- TODO: if the ctor-argument type is a java.util.Date and the argument is not null, it will be copied with Dates copy-ctor

Usage
---------------------

In contract first scenarios webservice clients are often generated with wsgen. The resulting source- or bytecode
confirms the standard bean contract. But we love immutability!

# using jaxws-maven-plugin
```
<plugin>
    <groupId>org.jvnet.jax-ws-commons</groupId>
    <artifactId>jaxws-maven-plugin</artifactId>
    <executions>
        <execution>
            <phase>generate-sources</phase>
            <goals>
                <goal>wsimport</goal>
            </goals>
            <configuration>
                <wsdlFiles>
                    <wsdlFile>${basedir}/src/test/resources/test.wsdl</wsdlFile>
                </wsdlFiles>
                <args>
                    <arg>-B-Ximmutable-model</arg>
                    <!--<arg>-B-xjc-Ximmutable-model:skipUnmodifiableCollections</arg>-->
                </args>
            </configuration>
        </execution>
    </executions>
    <dependencies>
       <dependency>
           <groupId>com.github.danielwegener.xjc</groupId>
           <artifactId>xjc-guava-plugin</artifactId>
           <version>0.1</version>
       </dependency>
    </dependencies>
</plugin>
```

# using cxf-codegen-plugin

```
<plugin>
    <groupId>org.apache.cxf</groupId>
    <artifactId>cxf-codegen-plugin</artifactId>
    <version>2.7.1</version>
    <executions>
        <execution>
            <phase>generate-sources</phase>
            <configuration>
                <sourceRoot>${project.build.directory}/generated-sources/cxf</sourceRoot>
                <wsdlOptions>

                    <wsdlOption>
                        <extraargs>
                            <extraarg>-xjc-Ximmutable-model</extraarg>
                            <!--<extraarg>-xjc-Ximmutable-model:skipUnmodifiableCollections</extraarg>-->
                        </extraargs>
                        <wsdl>${basedir}/src/test/resources/test.wsdl</wsdl>
                    </wsdlOption>
                </wsdlOptions>
            </configuration>
            <goals>
                <goal>wsdl2java</goal>
            </goals>
        </execution>
    </executions>
    <dependencies>
        <dependency>
           <groupId>com.github.danielwegener.xjc</groupId>
           <artifactId>xjc-guava-plugin</artifactId>
           <version>0.1</version>
       </dependency>
    </dependencies>
</plugin>
```

# direct xjc invokation

TODO: example
