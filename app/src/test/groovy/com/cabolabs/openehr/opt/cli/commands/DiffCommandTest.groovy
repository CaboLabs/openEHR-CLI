package com.cabolabs.openehr.opt.cli.commands

import spock.lang.Specification
import picocli.CommandLine
import com.cabolabs.openehr.opt.MainCli

// Fixtures: diff_test.opt -> diff_test_v2.opt removes the at0003 element and changes the
// template_id, at0002/at0004 stay the same (same pair used by the SDK's own
// SemanticOptDiffTest.testSemanticDiffStructural).
class DiffCommandTest extends Specification {

   String optPath(String name) {
      new File(getClass().getResource("/opts/diff/${name}").toURI()).path
   }

   ByteArrayOutputStream capture(Closure body) {
      def originalOut = System.out
      def bos = new ByteArrayOutputStream()
      System.out = new PrintStream(bos)
      try {
         body()
      } finally {
         System.out = originalOut
      }
      return bos
   }

   def "semantic diff tree reports the removed node and the template_id change"() {
      given:
      def out = capture {
         new CommandLine(new MainCli()).execute(
            "diff", "--old", optPath('diff_test.opt'), "--new", optPath('diff_test_v2.opt'), "--no-color"
         )
      }
      def text = out.toString()

      expect:
      text.contains('Semantic diff')
      text.contains("templateId: diff test -> diff test v2")
      text.contains('[removed]')
      text.contains('modified,')
   }

   def "structural diff tree labels itself as structural and reports the removed path"() {
      given:
      def out = capture {
         new CommandLine(new MainCli()).execute(
            "diff", "--old", optPath('diff_test.opt'), "--new", optPath('diff_test_v2.opt'),
            "--mode", "structural", "--no-color"
         )
      }
      def text = out.toString()

      expect:
      text.contains('Structural diff')
      text.contains('[removed]')
   }

   def "json format emits parseable, non-circular output"() {
      given:
      def out = capture {
         new CommandLine(new MainCli()).execute(
            "diff", "--old", optPath('diff_test.opt'), "--new", optPath('diff_test_v2.opt'), "--format", "json"
         )
      }
      def json = new groovy.json.JsonSlurper().parseText(out.toString())

      expect:
      json.root.status == 'modified'
      json.templateMetadataChanges.find { it.field == 'templateId' }.newValue == 'diff test v2'
   }

   String diffText(String oldOpt, String newOpt, String... extra) {
      def args = ["diff", "--old", optPath(oldOpt), "--new", optPath(newOpt)] + extra.toList()
      capture { new CommandLine(new MainCli()).execute(*args) }.toString()
   }

   def "tags confirmed and potential breaking changes inline and counts them"() {
      given:
      def text = diffText('test_diff_2.opt', 'test_diff_2_v1.opt', '--no-color')

      expect:
      // precision occurrences [0..1] -> [1..1] narrows the old OPT's constraint: confirmed
      text.readLines().any { it.contains('occurrences: [0..1] -> [1..1]') && it.contains('[CONFIRMED BREAKING]') }
      // DV_PROPORTION.type from the RM enumeration to [2]: potential
      text.readLines().any { it.contains('list: [0, 1, 2, 3, 4] -> [2]') && it.contains('[POTENTIAL BREAKING]') }
      text.contains('2 confirmed breaking, 6 potential breaking')
      !text.contains('Other breaking changes')
   }

   def "shows no constraint as the most open expression, never null"() {
      given:
      def text = diffText('test_diff_2.opt', 'test_diff_2_v1.opt', '--no-color')

      expect:
      text.contains('range: (*..*) -> [0..*)')   // DV_COUNT.magnitude, RM default allows negatives
      text.contains('pattern: any -> yyyy-mm-ddThh:mm:ss')
      text.contains('cm: magnitude: (*..*) -> [0.0..*)')
      !text.contains('null')
   }

   def "an optional new node is added without breaking changes"() {
      given:
      def lines = diffText('test_diff_2.opt', 'test_diff_2_v1.opt', '--no-color').readLines()

      expect:
      lines.any { it.contains('items[at0006]') && it.contains('[added]') }
      !lines.any { it.contains('items[at0006]') && it.contains('BREAKING') }
   }

   def "an added required node is a confirmed breaking change"() {
      given:
      def lines = diffText('test_diff_2.opt', 'test_diff_2_v2.opt', '--no-color').readLines()

      expect:
      lines.any { it.contains('items[at0006]') && it.contains('[added]') && it.contains('[CONFIRMED BREAKING]') }
      !lines.any { it.contains('items[at0006]/value') && it.contains('BREAKING') }
   }

   def "an optional node made required is a confirmed breaking change"() {
      given:
      def lines = diffText('test_diff_2_v1.opt', 'test_diff_2_v2.opt', '--no-color').readLines()

      expect:
      lines.any { it.contains('occurrences: [0..1] -> [1..1]') && it.contains('[CONFIRMED BREAKING]') }
      lines.any { it.contains('1 confirmed breaking, 0 potential breaking') }
   }

   def "removing a mandatory node is confirmed breaking, only at the removed node"() {
      given:
      def lines = diffText('test_diff_2_v2.opt', 'test_diff_2_v3_removed_mandatory.opt', '--no-color').readLines()

      expect:
      lines.any { it.contains('items[at0006] ') && it.contains('[removed]') && it.contains('[CONFIRMED BREAKING]') }
      !lines.any { it.contains('items[at0006]/value') && it.contains('BREAKING') }
      lines.any { it.contains('1 confirmed breaking, 0 potential breaking') }
   }

   def "removing an optional node is potential breaking, only at the removed node"() {
      given:
      def lines = diffText('test_diff_2_v1.opt', 'test_diff_2_v3_removed_optional.opt', '--no-color').readLines()

      expect:
      lines.any { it.contains('items[at0006] ') && it.contains('[removed]') && it.contains('[POTENTIAL BREAKING]') }
      !lines.any { it.contains('items[at0006]/value') && it.contains('BREAKING') }
      lines.any { it.contains('0 confirmed breaking, 1 potential breaking') }
   }

   def "json output has the breaking changes with their level and the text key"() {
      given:
      def json = new groovy.json.JsonSlurper().parseText(diffText('test_diff_2.opt', 'test_diff_2_v1.opt', '--format', 'json'))

      expect:
      json.breakingChanges.count { it.level == 'confirmed' && it.certain } == 2
      json.breakingChanges.count { it.level == 'potential' && !it.certain } == 6
      json.breakingChanges.find { it.category == 'constraint_added' && it.templatePath.endsWith('/value/type') }.newValue == [2]
      json.root.text == 'test diff 2 v1'
      !json.root.containsKey('name')
   }

   def "missing file returns a non-zero exit code"() {
      when:
      int exitCode = new CommandLine(new MainCli()).execute(
         "diff", "--old", "does-not-exist.opt", "--new", optPath('diff_test_v2.opt')
      )

      then:
      exitCode == 1
   }
}
