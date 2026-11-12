// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.memberProperties

/**
 * The null-free doctrine, enforced.
 *
 * No public member of the library has a nullable type. "Not configured yet"
 * is a real state, but it is a private one: it surfaces as an exception, not
 * as a null the consumer has to guard against.
 *
 * This sweep reads the compiled library and fails on any public member whose
 * type — or whose type arguments — is nullable. It cannot regress silently.
 *
 * The sweep has two passes, because no single mechanism sees everything:
 *
 * - the **reflective** pass walks the compiled library and inspects every
 *   public member, its receivers included;
 * - the **source** pass reads the declarations that carry a *reified* type
 *   parameter. Kotlin's reflection does not report them at all — a function
 *   with a reified type parameter is invisible to the reflective walk — so
 *   the few declarations of that shape are read from the sources instead.
 */
class NullFreeApiTest {

    @Test
    fun `no public member of the library has a nullable type`() {
        val offenders = mutableListOf<String>()

        libraryClasses().forEach { klass ->
            if (klass.visibility != KVisibility.PUBLIC) return@forEach
            klass.declaredMembers.forEach { member ->
                if (member.visibility != KVisibility.PUBLIC && member.visibility != KVisibility.PROTECTED) {
                    return@forEach
                }
                when (member) {
                    is KFunction<*> -> inspectFunction(klass, member, offenders)
                    is KProperty<*> -> inspectProperty(klass, member, offenders)
                    else -> Unit
                }
            }
            klass.memberProperties.forEach { property ->
                if (property.visibility != KVisibility.PUBLIC && property.visibility != KVisibility.PROTECTED) {
                    return@forEach
                }
                inspectProperty(klass, property, offenders)
            }
        }

        assertTrue(
            offenders.isEmpty(),
            "The public API of the library must be null-free, but these members are nullable:\n" +
                offenders.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `no reified declaration accepts a nullable type`() {
        val offenders = reifiedDeclarations().filter { declaration -> "?" in declaration }

        assertTrue(
            offenders.isEmpty(),
            "The public API of the library must be null-free, but these reified declarations are nullable:\n" +
                offenders.joinToString("\n") { "  $it" },
        )
    }

    /**
     * The public declarations that carry a reified type parameter.
     *
     * The declarations are read from the sources: the reflective pass cannot
     * see them, and a reified type parameter is the only way a public
     * declaration of this library could hide a nullable type from it.
     *
     * @return the declarations, one per line
     */
    private fun reifiedDeclarations(): List<String> {
        val root = Path(sourceRoot())
        if (!root.isDirectory()) return emptyList()
        return root
            .walk()
            .filter { it.name.endsWith(".kt") }
            .flatMap { file -> file.readLines().asSequence() }
            .map { it.trim() }
            .filter { it.startsWith("inline fun ") || it.startsWith("fun ") }
            .filter { !it.startsWith("private ") && !it.startsWith("internal ") }
            .filter { "reified" in it }
            .toList()
    }

    /** The root of the sources of the library. */
    private fun sourceRoot(): String = System.getProperty(SOURCE_ROOT_PROPERTY) ?: DEFAULT_SOURCE_ROOT

    /** Inspects the return type and the parameters of [function]. */
    private fun inspectFunction(klass: KClass<*>, function: KFunction<*>, offenders: MutableList<String>) {
        // The standard override of equals takes an Any?, and it is the one
        // nullable parameter the language itself mandates.
        if (function.name == "equals" && function.parameters.size == 2) return

        if (isNullable(function.returnType)) {
            offenders += "${klass.simpleName}.${function.name} returns ${function.returnType}"
        }
        function.parameters.forEach { parameter ->
            if (!isNullable(parameter.type)) return@forEach
            if (parameter.kind == KParameter.Kind.VALUE) {
                offenders += "${klass.simpleName}.${function.name}(${parameter.name}) takes ${parameter.type}"
            } else {
                offenders += "${klass.simpleName}.${function.name} has a ${parameter.kind} of type ${parameter.type}"
            }
        }
    }

    /** Inspects the type of [property]. */
    private fun inspectProperty(klass: KClass<*>, property: KProperty<*>, offenders: MutableList<String>) {
        if (isNullable(property.returnType)) {
            offenders += "${klass.simpleName}.${property.name} is ${property.returnType}"
        }
    }

    /**
     * Tells whether [type] is nullable, either itself or through one of its
     * type arguments.
     */
    private fun isNullable(type: KType): Boolean =
        type.isMarkedNullable || type.arguments.any { argument -> isNullable(argument.type ?: return false) }

    /** The public classes of the library, as the compiler produced them. */
    private fun libraryClasses(): List<KClass<*>> {
        val location = AdaptiveAutomaton::class.java.protectionDomain.codeSource.location.toURI()
        val root = Path(location.toString())
        val loader = AdaptiveAutomaton::class.java.classLoader
        return root
            .walk()
            .filter { it.name.endsWith(".class") }
            .map { file -> file.relativeTo(root).joinToString(".").removeSuffix(".class") }
            .filter { name -> name.startsWith(LIBRARY_PACKAGE) }
            .filter { name -> !name.contains("\$DefaultImpls") }
            .mapNotNull { name ->
                runCatching {
                    val raw = Class.forName(name, false, loader)
                    // The compiler's own classes — inlined lambdas, anonymous
                    // helpers — are not part of the API and do not survive a
                    // reflective walk.
                    if (raw.isSynthetic || raw.isAnonymousClass || raw.isLocalClass) {
                        null
                    } else {
                        raw.kotlin
                    }
                }.getOrNull()
            }
            .toList()
    }

    private companion object {
        /** The package every class of the library lives in. */
        const val LIBRARY_PACKAGE = "br.usp.poli.lta.cereda.kdaptive"

        /** The property naming the root of the sources of the library. */
        const val SOURCE_ROOT_PROPERTY = "kdaptive.sourceRoot"

        /** The root of the sources of the library, when the build did not name it. */
        const val DEFAULT_SOURCE_ROOT = "src/main/kotlin"
    }
}
