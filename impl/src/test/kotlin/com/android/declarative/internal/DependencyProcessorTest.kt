/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.declarative.internal

import com.android.declarative.internal.model.DependencyType
import com.android.declarative.internal.model.FilesDependencyInfo
import com.android.declarative.internal.model.MavenDependencyInfo
import com.android.declarative.internal.model.NotationDependencyInfo
import com.android.utils.ILogger
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.ConfigurableFileCollection
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import org.tomlj.Toml

@Suppress("UnstableApiUsage")
class DependencyProcessorTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.LENIENT)

    @Mock
    lateinit var dependencyHandler: DependencyHandler

    @Mock
    lateinit var dependencyFactory: DependencyFactory

    @Mock
    lateinit var project: Project

    @Before
    fun setup() {
        Mockito.`when`(project.rootProject).thenReturn(project)
        Mockito.`when`(project.files()).thenReturn(Mockito.mock(ConfigurableFileCollection::class.java))
    }

    @Test
    fun testProjectDependency() {
        val parser = createDependenciesParser()
        val dependency = createSubProjectAndWireDependency(":lib1")
        val dependencies = listOf(
            NotationDependencyInfo(
                DependencyType.PROJECT,
                "implementation",
                ":lib1"
            )
        )
        parser.process(dependencies)

        Mockito.verify(dependencyHandler).add("implementation", dependency)
        Mockito.verifyNoMoreInteractions(dependencyHandler)
    }

    @Test
    fun testMultipleShortFormProjectDependency() {
        val parser = createDependenciesParser()
        val dependency1 = createSubProjectAndWireDependency(":lib1")
        val dependency2 = createSubProjectAndWireDependency(":lib2")
        val dependencies = listOf(
            NotationDependencyInfo(
                DependencyType.PROJECT,
                "implementation",
                ":lib1"
            ),
            NotationDependencyInfo(
                DependencyType.PROJECT,
                "testImplementation",
                ":lib2"
            )
        )
        parser.process(dependencies)
        Mockito.verify(dependencyHandler).add("implementation", dependency1)
        Mockito.verify(dependencyHandler).add("testImplementation", dependency2)
        Mockito.verifyNoMoreInteractions(dependencyHandler)
    }

    @Test
    fun testSimpleExternalDependency() {
        val parser = createDependenciesParser()
        val dependency = createExternalDependency("org.mockito:mockito-core:4.8.0")
        val dependencies = listOf(
            NotationDependencyInfo(
                DependencyType.NOTATION,
                configuration = "implementation",
                notation = "org.mockito:mockito-core:4.8.0",
            )
        )
        parser.process(dependencies)
        Mockito.verify(dependencyHandler).add("implementation", dependency)
        Mockito.verifyNoMoreInteractions(dependencyHandler)
    }

    @Test
    fun testPartialExternalDependency() {
        val parser = createDependenciesParser()
        val dependency = createExternalDependency("org.mockito", "mockito-core", "4.8.0")
        val dependencies = listOf(
            MavenDependencyInfo(
                configuration = "implementation",
                group = "org.mockito",
                name = "mockito-core",
                version = "4.8.0",
            )
        )
        parser.process(dependencies)
        Mockito.verify(dependencyHandler).add("implementation", dependency)
        Mockito.verifyNoMoreInteractions(dependencyHandler)
    }

    @Test
    fun testPartialExternalDependencyInDottedNames() {
        val parser = createDependenciesParser()
        val dependency1 = createExternalDependency("org.mockito", "mockito-core", "4.8.0")
        val dependency2 = createExternalDependency("org.junit", "junit", "5.7.0")

        val toml = Toml.parse(
            """
            [dependencies.testImplementation]
            mockito = { group = "org.mockito", name = "mockito-core", version = "4.8.0" }
            junit = { group = "org.junit", name = "junit", version = "5.7.0" }
        """.trimIndent()
        )
        val dependencies = listOf(
            MavenDependencyInfo(
                configuration = "testImplementation",
                group = "org.mockito",
                name = "mockito-core",
                version = "4.8.0",
            ),
            MavenDependencyInfo(
                configuration = "testImplementation",
                group = "org.junit",
                name = "junit",
                version = "5.7.0",
            )
        )
        parser.process(dependencies)
        Mockito.verify(dependencyHandler).add("testImplementation", dependency1)
        Mockito.verify(dependencyHandler).add("testImplementation", dependency2)
        Mockito.verifyNoMoreInteractions(dependencyHandler)
    }

    @Test
    fun testVersionCatalogDependency() {
        val parser = createDependenciesParser()
        val dependency = createExternalDependency("libs.junit")
        val dependencies = listOf(
            NotationDependencyInfo(
                DependencyType.NOTATION,
                "implementation",
                "libs.junit"
            )
        )
        parser.process(dependencies)
        Mockito.verify(dependencyHandler).add("implementation", dependency)
        Mockito.verifyNoMoreInteractions(dependencyHandler)
    }

    @Test
    fun testFilesNotationDependency() {
        val fileCollection = Mockito.mock(ConfigurableFileCollection::class.java)
        val parser = DependencyProcessor(
            { id -> project.rootProject.project(id)},
            { fileCollection },
            dependencyFactory,
            dependencyHandler,
            IssueLogger(lenient = true, logger = Mockito.mock(ILogger::class.java))
        )
        val dependency = Mockito.mock(FileCollectionDependency::class.java).also {
            Mockito.`when`(dependencyFactory.create(fileCollection)).thenReturn(
                it
            )
        }
        val dependencies = listOf(
            FilesDependencyInfo(
                "implementation",
                listOf("local.jar")
            ),
            FilesDependencyInfo(
                "implementation",
                listOf("some.jar", "something.else", "final.one")
            )
        )
        parser.process(dependencies)
        Mockito.verify(dependencyHandler, times(2)).add("implementation", dependency)
        Mockito.verifyNoMoreInteractions(dependencyHandler)
    }

    @Test
    fun testMultipleProjectDependency() {
        val dependency1 = createSubProjectAndWireDependency(":lib1")
        val dependency2 = createSubProjectAndWireDependency(":lib2")
        val dependency3 = createSubProjectAndWireDependency(":lib3")

        val parser = createDependenciesParser()

        val dependencies = listOf(
            NotationDependencyInfo(
                type = DependencyType.PROJECT,
                configuration = "implementation",
                notation = ":lib1"
            ),
            NotationDependencyInfo(
                type = DependencyType.PROJECT,
                configuration = "implementation",
                notation = ":lib2"
            ),
            NotationDependencyInfo(
                type = DependencyType.PROJECT,
                configuration = "implementation",
                notation = ":lib3"
            ),
        )
        parser.process(dependencies)
        Mockito.verify(dependencyHandler).add("implementation", dependency1)
        Mockito.verify(dependencyHandler).add("implementation", dependency2)
        Mockito.verify(dependencyHandler).add("implementation", dependency3)
        Mockito.verifyNoMoreInteractions(dependencyHandler)
    }

    private fun createDependenciesParser() =
        DependencyProcessor(
            { id -> project.rootProject.project(id)},
            { project.files() },
            dependencyFactory,
            dependencyHandler,
            IssueLogger(lenient = true, logger = Mockito.mock(ILogger::class.java))
    )
    private fun createSubProject(projectPath: String): Project =
        Mockito.mock(Project::class.java).also {
            Mockito.`when`(project.project(projectPath)).thenReturn(it)
        }

    private fun createSubProjectAndWireDependency(projectPath: String): ProjectDependency =
        Mockito.mock(ProjectDependency::class.java).also {
            Mockito.`when`(dependencyFactory.create(createSubProject(projectPath))).thenReturn(it)
        }

    private fun createExternalDependency(group: String?, name: String, version: String?) =
        Mockito.mock(ExternalModuleDependency::class.java).also {
            Mockito.`when`(dependencyFactory.create(group, name, version)).thenReturn(
                it
            )
        }

    private fun createExternalDependency(notation: String) =
        Mockito.mock(ExternalModuleDependency::class.java).also {
            Mockito.`when`(dependencyFactory.create(notation)).thenReturn(
                it
            )
        }
}
