package com.cabolabs.openehr.opt

import com.cabolabs.openehr.opt.cli.commands.*
import picocli.CommandLine
import picocli.CommandLine.Command

@Command(name = "openehr",
         version = "1.0",
         description = "openEHR SDK Command Line Tools",
         mixinStandardHelpOptions = true,
         subcommands = [
            UiGenCommand,
            InstanceGenCommand,
            OptValidateCommand,
            InstanceValidateCommand,
            TransformCommand,
            Adl2OptCommand,
            DiffCommand
         ])
class MainCli {
   static void main(String[] args) {
      int exitCode = new CommandLine(new MainCli()).execute(args)
      System.exit(exitCode)
   }
}
