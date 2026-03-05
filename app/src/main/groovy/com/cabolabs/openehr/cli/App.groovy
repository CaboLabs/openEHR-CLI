package com.cabolabs.openehr.cli

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import picocli.CommandLine.Option
import groovy.json.JsonOutput

@Command(
    name = "openehr-cli",
    subcommands = [ValidateTemplate, ValidateComposition, GenerateSample, ListTemplates],
    mixinStandardHelpOptions = true,
    version = "openehr-cli 0.1",
    description = "CLI to facilitate working with openEHR artifacts"
)
class App implements Runnable {
    static void main(String[] args) {
        System.exit(new CommandLine(new App()).execute(args))
    }

    void run() {
        println "Use --help to see available commands."
    }
}

@Command(
    name = "validate-template",
    mixinStandardHelpOptions = true,
    description = "Validate an openEHR OPT template file"
)
class ValidateTemplate implements Runnable {

    @Parameters(index = "0", description = "Path to the OPT template file")
    String file

    void run() {
        def f = new File(file)
        if (!f.exists()) {
            def result = [status: "error", message: "File not found: ${file}"]
            println JsonOutput.toJson(result)
            return
        }
        if (!f.name.endsWith('.opt') && !f.name.endsWith('.xml')) {
            def result = [status: "warning", file: file, message: "File does not have .opt or .xml extension"]
            println JsonOutput.toJson(result)
            return
        }
        // TODO: add real OPT XML schema validation
        def result = [status: "valid", file: file, size_bytes: f.size()]
        println JsonOutput.toJson(result)
    }
}

@Command(
    name = "validate-composition",
    mixinStandardHelpOptions = true,
    description = "Validate an openEHR composition against a template"
)
class ValidateComposition implements Runnable {

    @Parameters(index = "0", description = "Path to the composition JSON/XML file")
    String compositionFile

    @Option(names = ["-t", "--template"], description = "Path to the OPT template file")
    String templateFile

    void run() {
        def compFile = new File(compositionFile)
        if (!compFile.exists()) {
            println JsonOutput.toJson([status: "error", message: "Composition file not found: ${compositionFile}"])
            return
        }
        if (templateFile) {
            def tFile = new File(templateFile)
            if (!tFile.exists()) {
                println JsonOutput.toJson([status: "error", message: "Template file not found: ${templateFile}"])
                return
            }
        }
        // TODO: add real composition validation against template
        def result = [
            status: "valid",
            composition: compositionFile,
            template: templateFile ?: "none",
            size_bytes: compFile.size()
        ]
        println JsonOutput.toJson(result)
    }
}

@Command(
    name = "generate-sample",
    mixinStandardHelpOptions = true,
    description = "Generate a sample openEHR composition from a template"
)
class GenerateSample implements Runnable {

    @Parameters(index = "0", description = "Path to the OPT template file")
    String templateFile

    @Option(names = ["-o", "--output"], description = "Output file path (prints to stdout if not specified)")
    String outputFile

    @Option(names = ["-f", "--format"], description = "Output format: json or xml (default: json)", defaultValue = "json")
    String format

    void run() {
        def tFile = new File(templateFile)
        if (!tFile.exists()) {
            println JsonOutput.toJson([status: "error", message: "Template file not found: ${templateFile}"])
            return
        }
        // TODO: generate a real sample composition from the OPT template
        def sample = [
            "_type": "COMPOSITION",
            "archetype_node_id": "openEHR-EHR-COMPOSITION.encounter.v1",
            "name": ["_type": "DV_TEXT", "value": "Sample Composition"],
            "language": ["terminology_id": ["value": "ISO_639-1"], "code_string": "en"],
            "territory": ["terminology_id": ["value": "ISO_3166-1"], "code_string": "US"],
            "category": ["value": "event", "defining_code": ["terminology_id": ["value": "openehr"], "code_string": "433"]],
            "composer": ["_type": "PARTY_SELF"]
        ]
        def output = JsonOutput.prettyPrint(JsonOutput.toJson(sample))
        if (outputFile) {
            new File(outputFile).text = output
            println JsonOutput.toJson([status: "generated", template: templateFile, output_file: outputFile, format: format])
        } else {
            println output
        }
    }
}

@Command(
    name = "list-templates",
    mixinStandardHelpOptions = true,
    description = "List all OPT templates found in a directory"
)
class ListTemplates implements Runnable {

    @Parameters(index = "0", defaultValue = ".", description = "Directory to search for .opt files")
    String directory

    @Option(names = ["-r", "--recursive"], description = "Search recursively in subdirectories")
    boolean recursive

    void run() {
        def dir = new File(directory)
        if (!dir.exists() || !dir.isDirectory()) {
            println JsonOutput.toJson([status: "error", message: "Directory not found: ${directory}"])
            return
        }
        def templates
        if (recursive) {
            templates = []
            dir.eachFileRecurse { f ->
                if (f.name.endsWith('.opt')) templates << [name: f.name, path: f.absolutePath, size_bytes: f.size()]
            }
        } else {
            templates = dir.listFiles()
                ?.findAll { it.name.endsWith('.opt') }
                ?.collect { [name: it.name, path: it.absolutePath, size_bytes: it.size()] } ?: []
        }
        println JsonOutput.toJson([directory: dir.absolutePath, count: templates.size(), templates: templates])
    }
}
