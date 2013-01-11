xjc-immutable-plugin
====================

Ever got lost in the JAX-WS wsgen swamp on the way to highroad of immutable models?

This XJC (JAX-B Compiler) Plugin comes for the rescue and provides generating of
immutable value object model from xml schema. To be honest, this is not JAX-B
standard conform, but it should work in most implementations.

Profit!

Example
---------------------
Instead of standard JAX-B beans like in the following example
```
TODO example
```

this plugin generates immutable, threadsafe and still serializable beans like in the following example.
```
TODO example
```


Usage
---------------------

*xjc-immutable-plugin is not yet in central*

In contract first scenarios webservice clients models are often generated with jaxws.wsgen or

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
            <artifactId>xjc-immutable-plugin</artifactId>
            <groupId>com.github.danielwegener</groupId>
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
            <artifactId>xjc-immutable-plugin</artifactId>
            <groupId>com.github.danielwegener</groupId>
            <version>0.1</version>
        </dependency>
    </dependencies>
</plugin>
```

Processing Rules
---------------------
This plugin
- makes all fields final
- removes all setter methods
- creates a public constructor with all fields as parameters
- creates a protected no-arg constructor, initializing fields with null (primitives with zero or false)
- wraps all collection like parameters with Collection.unmodifiable. views
- if a collection type field is null, a Collection.empty. is returned