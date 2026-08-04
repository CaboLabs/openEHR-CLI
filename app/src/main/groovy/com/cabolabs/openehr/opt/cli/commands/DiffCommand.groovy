package com.cabolabs.openehr.opt.cli.commands

import com.cabolabs.openehr.opt.cli.services.OptDiffService
import com.cabolabs.openehr.opt.diff.OperationalTemplateDiffAlgorithm
import com.cabolabs.openehr.opt.diff.SemanticOperationalTemplateDiffAlgorithm
import com.cabolabs.openehr.opt.parser.OperationalTemplateParser
import groovy.json.JsonOutput
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.concurrent.Callable

@Command(name = "diff", description = "Diff two Operational Templates")
class DiffCommand implements Callable<Integer> {

   @Option(names = ["--old"], required = true, description = "Path to the old/baseline OPT file")
   String oldPath

   @Option(names = ["--new"], required = true, description = "Path to the new OPT file")
   String newPath

   @Option(names = ["--mode"], defaultValue = "semantic", description = "Diff mode: semantic or structural (default: semantic)")
   String mode

   @Option(names = ["--format"], defaultValue = "tree", description = "Output format: tree or json (default: tree)")
   String format

   @Option(names = ["--full"], description = "Include unchanged (same) nodes in the output")
   boolean full

   @Option(names = ["--no-color"], description = "Disable ANSI colors in tree output")
   boolean noColor

   @Override
   Integer call() {
      try {
         if (!['semantic', 'structural'].contains(mode)) {
            println "Mode must be 'semantic' or 'structural', got: $mode"
            return 1
         }

         if (!['tree', 'json'].contains(format)) {
            println "Format must be 'tree' or 'json', got: $format"
            return 1
         }

         def oldFile = new File(oldPath)
         if (!oldFile.exists()) {
            println "File doesn't exist: $oldPath"
            return 1
         }

         def newFile = new File(newPath)
         if (!newFile.exists()) {
            println "File doesn't exist: $newPath"
            return 1
         }

         def parser = new OperationalTemplateParser()
         def opt1 = parser.parse(oldFile.text)
         def opt2 = parser.parse(newFile.text)

         boolean color = !noColor && System.getenv('NO_COLOR') == null && System.console() != null

         if (mode == 'semantic') {
            def diff = new SemanticOperationalTemplateDiffAlgorithm().diff(opt1, opt2)
            if (format == 'json') {
               println JsonOutput.prettyPrint(JsonOutput.toJson(OptDiffService.semanticDiffToMap(diff)))
            } else {
               print OptDiffService.renderSemantic(diff, full, color)
            }
         } else {
            def diff = new OperationalTemplateDiffAlgorithm().diff(opt1, opt2)
            if (format == 'json') {
               println JsonOutput.prettyPrint(JsonOutput.toJson(OptDiffService.structuralDiffToMap(diff)))
            } else {
               print OptDiffService.renderStructural(diff, full, color)
            }
         }

         return 0

      } catch (Exception e) {
         println "Error: ${e.message}"
         return 1
      }
   }
}
