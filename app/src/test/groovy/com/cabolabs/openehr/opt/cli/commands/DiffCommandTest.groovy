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

   def "missing file returns a non-zero exit code"() {
      when:
      int exitCode = new CommandLine(new MainCli()).execute(
         "diff", "--old", "does-not-exist.opt", "--new", optPath('diff_test_v2.opt')
      )

      then:
      exitCode == 1
   }
}
