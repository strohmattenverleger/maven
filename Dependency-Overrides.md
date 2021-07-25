<!---
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# Dependency Overrides

## Summary
Provide a concise way for overriding dependencies in a graph instead of relying on excludes and new dependencies.

## Goals
* Provide POM XML elements to express dependency overrides.
* New POM XML elements must adhere to the Maven standard way of merging POMs.\
  The most local override element of all override elements for the same original dependency in a POM hierarchy is to be used.
* Leave a dependency graph virtually untouched.\
  Only change/override nodes in place. Don't exclude dependencies and include new ones on a different level in the graph.
* If an override has different transitive dependencies, they are to be used instead of the original's transitive dependencies.
* The artifact type of the dependency can be changed.\
  For instance, this would allow for Jars to be replaced with POMs (and their transitive dependencies) and vice versa.
* Overrides are subject to dependency management.\
  I.e. managed dependencies also apply to overrides.
* Enable overriding dependencies in POMs in a Maven reactor as well as in POMs of transitive dependencies.\
  This is mainly for consistency, because one has control over dependencies in a reactor.
* \[Optional] Declaring and using overrides in the same Maven reactor should produce a warning.\
  This enables developers to quickly spot projects/modules which use originals instead of overrides themselves.
* \[Optional] Overriding an original with an artifact, which is part of the reactor, should be forbidden, because it introduces cyclic dependencies. The [Maven Resolver] cannot resolve the artifact of the reactor for transitive dependencies, and neither should it be able to.\
  An error should be printed, if a reactor artifact is used as an override, and the Maven process should be terminated.
* Dependency overrides can be imported by using the existing POM import mechanism in the `dependencyManagement` section.
* All existing Maven plug-ins must not break.\
  They needn't necessarily produce the right results. E.g. the [Versions Plug-in] will most likely be unable to take overrides into account, because they don't exist, yet. It mustn't break though, if overrides are being used. A future version of the [Versions Plug-in] however might take overrides into account, if there is a use case.
* All existing POMs must still be supported.\
  This includes projects/modules as well as POMs of existing artifacts in the Maven central repository.
  This is to ensure full backwards compatibility.

## Non-Goals
* Resolvers other than [Maven Resolver] are outside the scope of this proposal.\
  Only Maven projects (Core, Resolver, etc.) are the responsibility of the Maven PMC. All other software solutions (IDEs, Apache Ivy, Gradle, etc.), which rely on Maven POMs and maybe ship their own code for resolving artifacts, are the responsibility of their owners and maintainers.\
  At the end of the day invoking `mvn` or using the [Maven Resolver] as a library must produce the correct/desired result, which the Maven developers had in mind when writing the code.
* Maven should in no way verify that a dependency is a valid replacement for another dependency. Not only is this pretty much impossible without the JPMS or OSGi, because only the JPMS and OSGi can prevent public classes, which are not API, from being visible to consumers, it is also unclear where to draw the line, i.e., when an override is _not_ a suitable replacement.

## Motivation
Solid dependency management is one of the corner stones of Maven. Over the last 10-15 years the Maven central repository has grown and many, many artifacts with different coordinates (groupId, artifactId, type, classifier) contain practically the same content or are intended to replace others by "simulating" their API. Good examples are Java EE APIs and logging frameworks, but it's not limited to the aforementioned artifacts and this will only get worse in the future. An issue arises when dependencies (in the same project/module), which provide the same API, have different coordinates. The Maven Resolver does not know how to resolve these issues nor is it designed to.\
Currently people have to resort to excludes and new dependencies, which requires much boilerplate XML code and is error prone, because excluding a dependency doesn't enforce a replacement. Also, dependency graphs are modified in a way that does not correspond to the intent of overriding a dependency.

Consider the following artifacts:
```xml
...
<groupId>w</groupId>
<artifactId>w</artifactId>
<dependencies>
  <dependency>
    <groupId>y</groupId>
    <artifactId>y</artifactId>
  </dependency>
</dependencies>
...
```

```xml
...
<groupId>x</groupId>
<artifactId>x</artifactId>
<dependencies>
  <dependency>
    <groupId>y</groupId>
    <artifactId>y</artifactId>
  </dependency>
</dependencies>
...
```

```xml
...
<groupId>a</groupId>
<artifactId>a</artifactId>
<dependencies>
  <dependency>
    <groupId>w</groupId>
    <artifactId>w</artifactId>
  </dependency>
  <dependency>
    <groupId>x</groupId>
    <artifactId>x</artifactId>
  </dependency>
</dependencies>
...
```

The graph (tree) looks something like this:
```text
\- a:a
   +- w:w
   |  \- y:y
   \- x:x
      \- y:y (omitted for duplicate)
```

Now, if we wanted to replace `y:y` with `z:z`, we would need to write the following:
```xml
...
<groupId>a</groupId>
<artifactId>a</artifactId>
<dependencies>
  <dependency>
    <groupId>w</groupId>
    <artifactId>w</artifactId>
    <exclusions>
      <exclusion>
        <groupId>y</groupId>
        <artifactId>y</artifactId>
      </exclusion>
    </exclusions>
  </dependency>
  <dependency>
    <groupId>x</groupId>
    <artifactId>x</artifactId>
    <exclusions>
      <exclusion>
        <groupId>y</groupId>
        <artifactId>y</artifactId>
      </exclusion>
    </exclusions>
  </dependency>
  <dependency>
    <groupId>z</groupId>
    <artifactId>z</artifactId>
  </dependency>
</dependencies>
...
```

Writing this XML code is cumbersome and noisy. The more exclusions for the same dependency we write, the more bloated the XML gets.

The new graph looks like this:
```text
+- a:a
|  +- w:w
|  \- x:x
\- z:z
```

As we can see, two nodes have been removed, and a new one has been added. Considering the fact that we used exclusions, the graph is technically correct, but logically it's not, since we wanted to override/replace and not exclude and add.

Maven needs a combined "instruction" for excluding a dependency and adding a new one. This has been requested by many people in different ways over the last 10 years.
* [MNG-1977] Global dependency exclusions\
  This feature is a good idea. It reduces the XML code for repetitive exclusions. However, it does not include adding new dependencies.\
  I think this feature should exist on its own and does not compete with overrides.
* [MNG-4530] Add alias feature for moved/renamed dependencies\
  This feature is very close to what I have in mind when it comes to overrides and could be used/reopened as the driving issue for this proposal.
* [MNG-5652] "supplies"/"provides"/"proffers" concept proposal\
  This feature is overkill in my opinion. While it is a great idea, it requires constant maintenance of existing POMs or the consideration of many, if not all, equivalents or subsets for a new POM.\
  It is easier to provide a simple mechanism for developers to resolve those mismatching dependency coordinates, which actually apply to their projects/modules.

## Description
With dedicated override XML elements the replacement of dependencies could become much easier. The schema could look something like this (this is not an XSD, but an example of where the XML elements could be placed in a POM):

```xml
...
<dependencyManagement>
  <dependencyOverrides>
    <!-- All overrides in a single POM go here. -->
    <dependencyOverride>
      <!-- One override specifies a 1:1 relationship between the original and the override/replacement. -->
      <original>
        <!--
          Here only strictly necessary elements are required to identify the original dependency.
          Reusing the existing Dependency XML type could be done here, but it also includes the "version" and "exclusion" elements, which are unwanted.
        -->
        <groupId>string</groupId>
        <artifactId>string</artifactId>
        <type>string</type> <!-- Optional, defaults to "jar", as usual -->
        <classifier>string</classifier> <!-- Optional with no default, as usual -->
      </original>
      <override>
        <groupId>string</groupId>
        <artifactId>string</artifactId>
        <type>string</type> <!-- Optional, defaults to "jar", as usual -->
        <classifier>string</classifier> <!-- Optional with no default, as usual -->
        <version>string</version> <!-- Optional, if a corresponding managed dependency exists -->
      </override>
    </dependencyOverride>
  </dependencyOverrides>
</dependencyManagement>
...
```

Rewriting the same exclusions/new dependency of the previous example with overrides could look like this:
```xml
...
<groupId>a</groupId>
<artifactId>a</artifactId>
<dependencyManagement>
  <dependencyOverrides>
    <dependencyOverride>
      <original>
        <groupId>y</groupId>
        <artifactId>y</artifactId>
      </original>
      <override>
        <groupId>z</groupId>
        <artifactId>z</artifactId>
      </override>
    </dependencyOverride>
  </dependencyOverrides>
</dependencyManagement>
<dependencies>
  <dependency>
    <groupId>w</groupId>
    <artifactId>w</artifactId>
  </dependency>
  <dependency>
    <groupId>x</groupId>
    <artifactId>x</artifactId>
  </dependency>
</dependencies>
...
```

The corresponding graph looks like this:
```text
\- a:a
   +- w:w
   |  \- z:z (replaces y:y)
   \- x:x
      \- z:z (replaces y:y, omitted for duplicate)
```

While the example, which uses overrides, isn't actually shorter than the example, which uses excludes, it replaces `y:y` with `z:z` in _all_ places with just _one_ instruction. Furthermore, the graph shape remains unchanged. In real and more complex projects the XML code for exclusions quickly outgrows the XML code for overrides. A future version of Maven could also bring an XML schema which utilizes XML element attributes or a DSL, which would compact the code even further.

Because of new and backwards compatible XML elements, the POM would require a minor version change. This means not only Maven Core needs some code changes, but also [Maven Resolver] for it to be able to take overrides into account.

# Alternatives
* [MNG-1977] Global dependency exclusions\
  As mentioned earlier, this mechanism can be used to dispose of unwanted dependencies with little XML code, but lacks the functionality to introduce a different dependency, which is what this proposal is about.\
  This too requires a minor model change.
* [MNG-4530] Add alias feature for moved/renamed dependencies\
  If this were to be implemented the way it is described in the issue, it would be very much like this proposal, but with different XML elements. The main difference is that this proposal would introduce an XML element for overriding a dependency with exactly _one_ other dependency (hence the 1:1 relationship). Using [MNG-4530]'s approach of having a managed dependency which declares _many_ aliases (i.e. originals) for a new dependency (the override/replacement) would violate the single-responsibility principle. A managed dependency only contains version information and optionally a scope for a dependency to use. Overriding/replacing a dependency should be kept separate as it is a different feature entirely.
* [MNG-5652] "supplies"/"provides"/"proffers" concept proposal\
  As mentioned earlier, it introduces the need for too much maintenance in existing POMs which is a burden to developers. Introducing dependency overrides in one's own POM brings a lot of flexibility with very little effort.

## Testing
Existing unit tests must not break, unless they depend on the exact current model version (4.0.0), which needs a minor change (4.1.0) in order to provide new XML elements.\
New unit tests need to be implemented for the following cases:
* Having a dependency override X -> Y (X becomes Y),\
  and a direct and a transitive dependency to X,\
  when resolving\
  then Y must be resolved instead of X for both dependencies.
* Having a dependency override X -> Y in a POM,\
  and a dependency override X -> * in the _same physical_ POM,\
  when creating the model (as part of the reactor)\
  then an error must be emitted, and the Maven process must be halted.
* Having a dependency override X -> Z in a POM,\
  and a dependency override X -> Y in the POM's parent POM,\
  when creating the model\
  then only the dependency override X -> Z must exist in the effective POM.
* Having an imported POM with a dependency override X -> Y,\
  when creating the model\
  then the dependency override X -> Y must exist in the effective POM.
* Having an imported POM with a dependency override X -> Y,\
  and a dependency override X -> Z\
  when creating the model\
  then only the dependency override X -> Z must exist in the effective POM.
* Having an imported POM with a dependency override X -> Y,\
  and an imported POM with a dependency override X -> Z,\
  when creating the model\

[Maven Resolver]: https://github.com/apache/maven-resolver
[MNG-1977]: https://issues.apache.org/jira/browse/MNG-1977
[MNG-4530]: https://issues.apache.org/jira/browse/MNG-4530
[MNG-5652]: https://issues.apache.org/jira/browse/MNG-5652
[Versions Plug-in]: https://www.mojohaus.org/versions-maven-plugin/
