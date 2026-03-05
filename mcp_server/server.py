#!/usr/bin/env python3
"""
MCP server that exposes openEHR-CLI commands as tools for LLMs.

This server bridges the Model Context Protocol (MCP) with the openEHR-CLI
Groovy application, allowing LLMs like Claude to call CLI commands as tools.

Transport: stdio (JSON-RPC 2.0)
"""

import subprocess
import json
import asyncio
from pathlib import Path

from mcp.server import Server
from mcp.server.stdio import stdio_server
from mcp.types import Tool, TextContent

# ---------------------------------------------------------------------------
# CLI path configuration
# ---------------------------------------------------------------------------

_REPO_ROOT = Path(__file__).parent.parent.resolve()
CLI_PATH = _REPO_ROOT / "app" / "build" / "install" / "app" / "bin" / "app"

# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------

def run_cli(*args: str) -> str:
    """Run the openEHR-CLI binary with the given arguments.

    Returns stdout on success, or a JSON error object on failure.
    """
    if not CLI_PATH.exists():
        return json.dumps({
            "error": f"CLI binary not found at {CLI_PATH}. "
                     "Run './gradlew installDist' first."
        })

    try:
        result = subprocess.run(
            [str(CLI_PATH)] + list(args),
            capture_output=True,
            text=True,
            timeout=30,
        )
    except subprocess.TimeoutExpired:
        return json.dumps({"error": "CLI command timed out after 30 seconds"})

    if result.returncode != 0:
        return json.dumps({
            "error": result.stderr.strip() or f"CLI exited with code {result.returncode}"
        })

    return result.stdout.strip()


# ---------------------------------------------------------------------------
# MCP server definition
# ---------------------------------------------------------------------------

app = Server("openehr-cli-mcp")


@app.list_tools()
async def list_tools() -> list[Tool]:
    """Return the list of tools that the MCP server exposes."""
    return [
        Tool(
            name="validate_template",
            description=(
                "Validate an openEHR Operational Template (OPT) file. "
                "Returns a JSON object with 'status' (valid/error/warning) "
                "and file metadata."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "file": {
                        "type": "string",
                        "description": "Absolute or relative path to the .opt template file.",
                    }
                },
                "required": ["file"],
            },
        ),
        Tool(
            name="validate_composition",
            description=(
                "Validate an openEHR composition (JSON or XML) against an optional "
                "OPT template. Returns a JSON object with 'status' and file metadata."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "composition_file": {
                        "type": "string",
                        "description": "Path to the composition file (JSON or XML).",
                    },
                    "template_file": {
                        "type": "string",
                        "description": "Optional path to the OPT template for validation.",
                    },
                },
                "required": ["composition_file"],
            },
        ),
        Tool(
            name="generate_sample",
            description=(
                "Generate a sample openEHR COMPOSITION JSON from an OPT template. "
                "Optionally write the result to a file."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "template_file": {
                        "type": "string",
                        "description": "Path to the OPT template file.",
                    },
                    "output_file": {
                        "type": "string",
                        "description": "Optional output file path. If omitted, result is returned inline.",
                    },
                    "format": {
                        "type": "string",
                        "enum": ["json", "xml"],
                        "description": "Output format — 'json' (default) or 'xml'.",
                    },
                },
                "required": ["template_file"],
            },
        ),
        Tool(
            name="list_templates",
            description=(
                "List all OPT template files (.opt) found in a directory. "
                "Returns a JSON object with a 'templates' array and a count."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "directory": {
                        "type": "string",
                        "description": "Directory path to search. Defaults to current directory.",
                    },
                    "recursive": {
                        "type": "boolean",
                        "description": "If true, search subdirectories recursively.",
                    },
                },
            },
        ),
    ]


@app.call_tool()
async def call_tool(name: str, arguments: dict) -> list[TextContent]:
    """Dispatch a tool call to the openEHR-CLI binary."""

    if name == "validate_template":
        output = run_cli("validate-template", arguments["file"])

    elif name == "validate_composition":
        args = ["validate-composition", arguments["composition_file"]]
        if "template_file" in arguments:
            args += ["--template", arguments["template_file"]]
        output = run_cli(*args)

    elif name == "generate_sample":
        args = ["generate-sample", arguments["template_file"]]
        if "output_file" in arguments:
            args += ["--output", arguments["output_file"]]
        if "format" in arguments:
            args += ["--format", arguments["format"]]
        output = run_cli(*args)

    elif name == "list_templates":
        args = ["list-templates"]
        if "directory" in arguments:
            args.append(arguments["directory"])
        if arguments.get("recursive"):
            args.append("--recursive")
        output = run_cli(*args)

    else:
        output = json.dumps({"error": f"Unknown tool: {name}"})

    return [TextContent(type="text", text=output)]


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

async def main() -> None:
    async with stdio_server() as (read_stream, write_stream):
        await app.run(
            read_stream,
            write_stream,
            app.create_initialization_options(),
        )


if __name__ == "__main__":
    asyncio.run(main())
