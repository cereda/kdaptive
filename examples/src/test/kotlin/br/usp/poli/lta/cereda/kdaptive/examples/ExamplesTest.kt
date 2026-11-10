// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.dot.GraphVizVisitor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExamplesTest {

    // -----------------------------------------------------------------------
    // RegularLanguageExample: (ab)+ over {a, b}
    // -----------------------------------------------------------------------
    @Test
    fun `regular language accepts ab`() {
        assertTrue(RegularLanguageExample().recognize("ab"))
    }

    @Test
    fun `regular language accepts abab`() {
        assertTrue(RegularLanguageExample().recognize("abab"))
    }

    @Test
    fun `regular language accepts ababab`() {
        assertTrue(RegularLanguageExample().recognize("ababab"))
    }

    @Test
    fun `regular language rejects empty string`() {
        assertFalse(RegularLanguageExample().recognize(""))
    }

    @Test
    fun `regular language rejects a`() {
        assertFalse(RegularLanguageExample().recognize("a"))
    }

    @Test
    fun `regular language rejects aba`() {
        assertFalse(RegularLanguageExample().recognize("aba"))
    }

    @Test
    fun `regular language rejects ba`() {
        assertFalse(RegularLanguageExample().recognize("ba"))
    }

    // -----------------------------------------------------------------------
    // ContextFreeLanguageExample: a^n b^n (n >= 0)
    // -----------------------------------------------------------------------
    @Test
    fun `context-free language accepts empty string`() {
        assertTrue(ContextFreeLanguageExample().recognize(""))
    }

    @Test
    fun `context-free language accepts ab`() {
        assertTrue(ContextFreeLanguageExample().recognize("ab"))
    }

    @Test
    fun `context-free language accepts aabb`() {
        assertTrue(ContextFreeLanguageExample().recognize("aabb"))
    }

    @Test
    fun `context-free language accepts aaabbb`() {
        assertTrue(ContextFreeLanguageExample().recognize("aaabbb"))
    }

    @Test
    fun `context-free language rejects a`() {
        assertFalse(ContextFreeLanguageExample().recognize("a"))
    }

    @Test
    fun `context-free language rejects b`() {
        assertFalse(ContextFreeLanguageExample().recognize("b"))
    }

    @Test
    fun `context-free language rejects abb`() {
        assertFalse(ContextFreeLanguageExample().recognize("abb"))
    }

    @Test
    fun `context-free language rejects aab`() {
        assertFalse(ContextFreeLanguageExample().recognize("aab"))
    }

    @Test
    fun `context-free language rejects ba`() {
        assertFalse(ContextFreeLanguageExample().recognize("ba"))
    }

    // -----------------------------------------------------------------------
    // EpsilonLanguageExample: a* b* over {a, b}
    // -----------------------------------------------------------------------
    @Test
    fun `epsilon language accepts empty string`() {
        assertTrue(EpsilonLanguageExample().recognize(""))
    }

    @Test
    fun `epsilon language accepts aabb`() {
        assertTrue(EpsilonLanguageExample().recognize("aabb"))
    }

    @Test
    fun `epsilon language accepts bb`() {
        assertTrue(EpsilonLanguageExample().recognize("bb"))
    }

    @Test
    fun `epsilon language accepts aa`() {
        assertTrue(EpsilonLanguageExample().recognize("aa"))
    }

    @Test
    fun `epsilon language accepts ab`() {
        assertTrue(EpsilonLanguageExample().recognize("ab"))
    }

    @Test
    fun `epsilon language rejects ba`() {
        assertFalse(EpsilonLanguageExample().recognize("ba"))
    }

    // -----------------------------------------------------------------------
    // CallReturnExample: a c* b
    // -----------------------------------------------------------------------
    @Test
    fun `call return example accepts acb`() {
        assertTrue(CallReturnExample().recognize("acb"))
    }

    @Test
    fun `call return example accepts acccb`() {
        assertTrue(CallReturnExample().recognize("acccb"))
    }

    @Test
    fun `call return example accepts ab`() {
        assertTrue(CallReturnExample().recognize("ab"))
    }

    @Test
    fun `call return example rejects a`() {
        assertFalse(CallReturnExample().recognize("a"))
    }

    @Test
    fun `call return example rejects ac`() {
        assertFalse(CallReturnExample().recognize("ac"))
    }

    @Test
    fun `call return example rejects b`() {
        assertFalse(CallReturnExample().recognize("b"))
    }

    @Test
    fun `call return example rejects abc`() {
        assertFalse(CallReturnExample().recognize("abc"))
    }

    // -----------------------------------------------------------------------
    // ConfigParserExample: a=[01]+;
    // -----------------------------------------------------------------------
    @Test
    fun `config parser accepts a=0 semicolon`() {
        assertTrue(ConfigParserExample().recognize("a=0;"))
    }

    @Test
    fun `config parser accepts a=1 semicolon`() {
        assertTrue(ConfigParserExample().recognize("a=1;"))
    }

    @Test
    fun `config parser accepts a=01 semicolon`() {
        assertTrue(ConfigParserExample().recognize("a=01;"))
    }

    @Test
    fun `config parser accepts a=1010 semicolon`() {
        assertTrue(ConfigParserExample().recognize("a=1010;"))
    }

    @Test
    fun `config parser rejects a= semicolon`() {
        assertFalse(ConfigParserExample().recognize("a=;"))
    }

    @Test
    fun `config parser rejects a=0`() {
        assertFalse(ConfigParserExample().recognize("a=0"))
    }

    @Test
    fun `config parser rejects b=0 semicolon`() {
        assertFalse(ConfigParserExample().recognize("b=0;"))
    }

    @Test
    fun `config parser rejects a=0 semicolon semicolon`() {
        assertFalse(ConfigParserExample().recognize("a=0;;"))
    }

    @Test
    fun `config parser rejects empty string`() {
        assertFalse(ConfigParserExample().recognize(""))
    }

    // -----------------------------------------------------------------------
    // ArithmeticExample: digit op digit where digit in {0,1}, op in {+,*}
    // -----------------------------------------------------------------------
    @Test
    fun `arithmetic accepts 0+0`() {
        assertTrue(ArithmeticExample().recognize("0+0"))
    }

    @Test
    fun `arithmetic accepts 0+1`() {
        assertTrue(ArithmeticExample().recognize("0+1"))
    }

    @Test
    fun `arithmetic accepts 1+0`() {
        assertTrue(ArithmeticExample().recognize("1+0"))
    }

    @Test
    fun `arithmetic accepts 1+1`() {
        assertTrue(ArithmeticExample().recognize("1+1"))
    }

    @Test
    fun `arithmetic accepts 0x0`() {
        assertTrue(ArithmeticExample().recognize("0*0"))
    }

    @Test
    fun `arithmetic accepts 0x1`() {
        assertTrue(ArithmeticExample().recognize("0*1"))
    }

    @Test
    fun `arithmetic accepts 1x0`() {
        assertTrue(ArithmeticExample().recognize("1*0"))
    }

    @Test
    fun `arithmetic accepts 1x1`() {
        assertTrue(ArithmeticExample().recognize("1*1"))
    }

    @Test
    fun `arithmetic rejects 0+`() {
        assertFalse(ArithmeticExample().recognize("0+"))
    }

    @Test
    fun `arithmetic rejects x1`() {
        assertFalse(ArithmeticExample().recognize("*1"))
    }

    @Test
    fun `arithmetic rejects 0++1`() {
        assertFalse(ArithmeticExample().recognize("0++1"))
    }

    @Test
    fun `arithmetic rejects 2+2`() {
        assertFalse(ArithmeticExample().recognize("2+2"))
    }

    // -----------------------------------------------------------------------
    // DslShowcaseExample: a b c with dynamic c transition
    // -----------------------------------------------------------------------
    @Test
    fun `dsl showcase accepts abc`() {
        assertTrue(DslShowcaseExample().recognize("abc"))
    }

    @Test
    fun `dsl showcase rejects ab`() {
        assertFalse(DslShowcaseExample().recognize("ab"))
    }

    @Test
    fun `dsl showcase rejects ac`() {
        assertFalse(DslShowcaseExample().recognize("ac"))
    }

    @Test
    fun `dsl showcase rejects a`() {
        assertFalse(DslShowcaseExample().recognize("a"))
    }

    @Test
    fun `dsl showcase rejects empty string`() {
        assertFalse(DslShowcaseExample().recognize(""))
    }

    @Test
    fun `dsl showcase rejects abcd`() {
        assertFalse(DslShowcaseExample().recognize("abcd"))
    }

    // -----------------------------------------------------------------------
    // ContextSensitiveLanguageExample: a^n b^n c^n (n > 0)
    // -----------------------------------------------------------------------
    @Test
    fun `context sensitive accepts aaabbbccc`() {
        assertTrue(ContextSensitiveLanguageExample().recognize("aaabbbccc"))
    }

    @Test
    fun `context sensitive accepts abc`() {
        assertTrue(ContextSensitiveLanguageExample().recognize("abc"))
    }

    @Test
    fun `context sensitive accepts aabbcc`() {
        assertTrue(ContextSensitiveLanguageExample().recognize("aabbcc"))
    }

    @Test
    fun `context sensitive rejects empty string`() {
        assertFalse(ContextSensitiveLanguageExample().recognize(""))
    }

    @Test
    fun `context sensitive rejects ab`() {
        assertFalse(ContextSensitiveLanguageExample().recognize("ab"))
    }

    @Test
    fun `context sensitive rejects aabbbcc`() {
        assertFalse(ContextSensitiveLanguageExample().recognize("aabbbcc"))
    }

    @Test
    fun `context sensitive accepts a^n b^n c^n for every n up to six`() {
        for (n in 1..6) {
            val input = "a".repeat(n) + "b".repeat(n) + "c".repeat(n)
            assertTrue(ContextSensitiveLanguageExample().recognize(input), "expected '$input' to be accepted")
        }
    }

    @Test
    fun `context sensitive rejects mismatched counts`() {
        val mismatches =
            listOf(
                "aabbbccc", // too few a's
                "aaabbbcc", // too few c's
                "aaabbbbcc", // too many b's
                "abcabcc", // interleaved
                "aabbccabc", // two blocks
            )
        for (input in mismatches) {
            assertFalse(ContextSensitiveLanguageExample().recognize(input), "expected '$input' to be rejected")
        }
    }

    // -----------------------------------------------------------------------
    // NestedCallReturnExample: a c e d b with nested submachine calls
    // -----------------------------------------------------------------------
    @Test
    fun `nested call return accepts acedb`() {
        assertTrue(NestedCallReturnExample().recognize("acedb"))
    }

    @Test
    fun `nested call return accepts aceddb`() {
        assertTrue(NestedCallReturnExample().recognize("aceddb"))
    }

    @Test
    fun `nested call return accepts acedddb`() {
        assertTrue(NestedCallReturnExample().recognize("acedddb"))
    }

    @Test
    fun `nested call return rejects missing e`() {
        assertFalse(NestedCallReturnExample().recognize("acdb"))
    }

    @Test
    fun `nested call return rejects missing d`() {
        assertFalse(NestedCallReturnExample().recognize("aceb"))
    }

    @Test
    fun `nested call return rejects missing b`() {
        assertFalse(NestedCallReturnExample().recognize("aced"))
    }

    @Test
    fun `nested call return rejects empty string`() {
        assertFalse(NestedCallReturnExample().recognize(""))
    }

    @Test
    fun `nested call return rejects missing initial a`() {
        assertFalse(NestedCallReturnExample().recognize("cedb"))
    }

    // -----------------------------------------------------------------------
    // NondeterminismExample: a(b|c)
    // -----------------------------------------------------------------------
    @Test
    fun `nondeterminism accepts ab`() {
        assertTrue(NondeterminismExample().recognize("ab"))
    }

    @Test
    fun `nondeterminism accepts ac`() {
        assertTrue(NondeterminismExample().recognize("ac"))
    }

    @Test
    fun `nondeterminism rejects a`() {
        assertFalse(NondeterminismExample().recognize("a"))
    }

    @Test
    fun `nondeterminism rejects b`() {
        assertFalse(NondeterminismExample().recognize("b"))
    }

    @Test
    fun `nondeterminism rejects c`() {
        assertFalse(NondeterminismExample().recognize("c"))
    }

    @Test
    fun `nondeterminism rejects abc`() {
        assertFalse(NondeterminismExample().recognize("abc"))
    }

    @Test
    fun `nondeterminism rejects acc`() {
        assertFalse(NondeterminismExample().recognize("acc"))
    }

    // -----------------------------------------------------------------------
    // BalancedParenthesesExample: balanced parentheses over {(, )}
    // -----------------------------------------------------------------------
    @Test
    fun `balanced parentheses accepts empty string`() {
        assertTrue(BalancedParenthesesExample().recognize(""))
    }

    @Test
    fun `balanced parentheses accepts ()`() {
        assertTrue(BalancedParenthesesExample().recognize("()"))
    }

    @Test
    fun `balanced parentheses accepts (())`() {
        assertTrue(BalancedParenthesesExample().recognize("(())"))
    }

    @Test
    fun `balanced parentheses accepts ()()`() {
        assertTrue(BalancedParenthesesExample().recognize("()()"))
    }

    @Test
    fun `balanced parentheses accepts (()())`() {
        assertTrue(BalancedParenthesesExample().recognize("(()())"))
    }

    @Test
    fun `balanced parentheses accepts ((()))`() {
        assertTrue(BalancedParenthesesExample().recognize("((()))"))
    }

    @Test
    fun `balanced parentheses rejects (`() {
        assertFalse(BalancedParenthesesExample().recognize("("))
    }

    @Test
    fun `balanced parentheses rejects )`() {
        assertFalse(BalancedParenthesesExample().recognize(")"))
    }

    @Test
    fun `balanced parentheses rejects (()`() {
        assertFalse(BalancedParenthesesExample().recognize("(()"))
    }

    @Test
    fun `balanced parentheses rejects ())`() {
        assertFalse(BalancedParenthesesExample().recognize("())"))
    }

    @Test
    fun `balanced parentheses rejects )(`() {
        assertFalse(BalancedParenthesesExample().recognize(")("))
    }

    @Test
    fun `balanced parentheses rejects ()()(`() {
        assertFalse(BalancedParenthesesExample().recognize("()()("))
    }

    // -----------------------------------------------------------------------
    // ModuloExample: a^n where n mod 3 = 0
    // -----------------------------------------------------------------------
    @Test
    fun `modulo example accepts empty string`() {
        assertTrue(ModuloExample().recognize(""))
    }

    @Test
    fun `modulo example accepts aaa`() {
        assertTrue(ModuloExample().recognize("aaa"))
    }

    @Test
    fun `modulo example accepts aaaaaa`() {
        assertTrue(ModuloExample().recognize("aaaaaa"))
    }

    @Test
    fun `modulo example rejects a`() {
        assertFalse(ModuloExample().recognize("a"))
    }

    @Test
    fun `modulo example rejects aa`() {
        assertFalse(ModuloExample().recognize("aa"))
    }

    @Test
    fun `modulo example rejects aaaa`() {
        assertFalse(ModuloExample().recognize("aaaa"))
    }

    @Test
    fun `modulo example rejects b`() {
        assertFalse(ModuloExample().recognize("b"))
    }

    // -----------------------------------------------------------------------
    // SubstringExample: a(b|c) - wait, this is a(b|c)? No, substring "ab"
    // -----------------------------------------------------------------------
    @Test
    fun `substring example accepts ab`() {
        assertTrue(SubstringExample().recognize("ab"))
    }

    @Test
    fun `substring example accepts aab`() {
        assertTrue(SubstringExample().recognize("aab"))
    }

    @Test
    fun `substring example accepts abb`() {
        assertTrue(SubstringExample().recognize("abb"))
    }

    @Test
    fun `substring example accepts baab`() {
        assertTrue(SubstringExample().recognize("baab"))
    }

    @Test
    fun `substring example rejects a`() {
        assertFalse(SubstringExample().recognize("a"))
    }

    @Test
    fun `substring example rejects b`() {
        assertFalse(SubstringExample().recognize("b"))
    }

    @Test
    fun `substring example rejects aa`() {
        assertFalse(SubstringExample().recognize("aa"))
    }

    @Test
    fun `substring example rejects bb`() {
        assertFalse(SubstringExample().recognize("bb"))
    }

    @Test
    fun `substring example rejects ba`() {
        assertFalse(SubstringExample().recognize("ba"))
    }

    @Test
    fun `substring example rejects empty string`() {
        assertFalse(SubstringExample().recognize(""))
    }

    // -----------------------------------------------------------------------
    // GraphVizExample: ab, with a GraphViz snapshot of every mutation
    // -----------------------------------------------------------------------
    @Test
    fun `graphviz example accepts ab`() {
        assertTrue(GraphVizExample().recognize("ab"))
    }

    @Test
    fun `graphviz example accepts aba`() {
        assertTrue(GraphVizExample().recognize("aba"))
    }

    @Test
    fun `graphviz example rejects a`() {
        assertFalse(GraphVizExample().recognize("a"))
    }

    @Test
    fun `graphviz example rejects empty string`() {
        assertFalse(GraphVizExample().recognize(""))
    }

    @Test
    fun `graphviz example emits a DOT snapshot per topology mutation`() {
        val frames = mutableListOf<Pair<Int, String>>()
        val example = GraphVizExample()
        example.visitors.add(GraphVizVisitor { pathId, dot -> frames.add(pathId to dot) })

        assertTrue(example.recognize("ab"))

        assertFalse(frames.isEmpty())
        assertTrue(frames.all { frame -> frame.second.startsWith("digraph Automaton {") })
        assertTrue(frames.any { frame -> frame.second.contains("subgraph cluster_M") })
    }

    // -----------------------------------------------------------------------
    // CostExample: a* b, priced by a consumer of the instrumentation facility
    // -----------------------------------------------------------------------
    @Test
    fun `cost example accepts b`() {
        assertTrue(CostExample().recognize("b"))
    }

    @Test
    fun `cost example accepts ab`() {
        assertTrue(CostExample().recognize("ab"))
    }

    @Test
    fun `cost example accepts aab`() {
        assertTrue(CostExample().recognize("aab"))
    }

    @Test
    fun `cost example rejects aabb`() {
        assertFalse(CostExample().recognize("aabb"))
    }

    @Test
    fun `cost example rejects empty string`() {
        assertFalse(CostExample().recognize(""))
    }

    @Test
    fun `cost visitor counts the operations performed by the adaptive action`() {
        val visitor = CostVisitor()
        val example = CostExample()
        example.visitors.add(visitor)

        assertTrue(example.recognize("ab"))

        // One 'a' consumed: one query, one removal, one addition, one fact.
        assertEquals(1, visitor.queries)
        assertEquals(1, visitor.removals)
        assertEquals(1, visitor.additions)
        assertEquals(1, visitor.facts)

        // The removal matched nothing, so it took no edge out of the mapping.
        assertEquals(0, visitor.edgesRemoved)
        assertEquals(1, visitor.edgesAdded)
    }

    @Test
    fun `cost visitor prices the work of a longer recognition`() {
        val visitor = CostVisitor()
        val example = CostExample()
        example.visitors.add(visitor)

        assertTrue(example.recognize("aab"))

        // Two 'a's consumed: two of each operation, and the second removal
        // retires the marker edge installed by the first.
        assertEquals(2, visitor.queries)
        assertEquals(2, visitor.removals)
        assertEquals(2, visitor.additions)
        assertEquals(2, visitor.facts)
        assertEquals(1, visitor.edgesRemoved)
        assertEquals(2, visitor.edgesAdded)

        // The cost grows with the input: each firing scans a larger mapping.
        assertTrue(visitor.cost > 0.0)
        assertEquals(1, visitor.costPerPath().size)
    }

    @Test
    fun `cost visitor keeps the paths apart`() {
        val visitor = CostVisitor()
        val example = CostExample()
        example.visitors.add(visitor)

        example.recognize("ab")
        example.recognize("aab")

        // Two recognitions, two paths.
        assertEquals(2, visitor.costPerPath().size)
        assertEquals(2, visitor.paths)
    }
}
