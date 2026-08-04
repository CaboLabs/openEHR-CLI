package com.cabolabs.openehr.opt

import spock.lang.Specification
import picocli.CommandLine

class MainCliTest extends Specification {

   def "CLI exits with usage code when no args given"() {
      when:
      int exitCode = new CommandLine(new MainCli()).execute()

      then:
      exitCode == CommandLine.ExitCode.USAGE
   }

   def "CLI shows help without error"() {
      when:
      int exitCode = new CommandLine(new MainCli()).execute("--help")

      then:
      exitCode == 0
   }
}
