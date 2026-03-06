#!/usr/bin/env python3
"""
MCP server wrapping the openEHR SDK CLI (sdk command).

Commands exposed as MCP tools:
  optval            - Validate an OPT file against the openEHR XSD schema
  ingen             - Generate instances (COMPOSITION etc.) from an OPT
  inval             - Validate XML/JSON instances against schemas
  adl2opt           - Convert an ADL archetype to OPT
  trans_opt         - Transform OPT from XML to JSON
  trans_locatable   - Transform a Locatable between XML and JSON
  uigen             - Generate a Bootstrap HTML UI from an OPT

Transport: stdio (JSON-RPC 2.0 / MCP)
"""

import asyncio
import json
import subprocess
from pathlib import Path

from mcp.server import Server
from mcp.server.stdio import stdio_server
from mcp.types import Tool, TextContent

# ---------------------------------------------------------------------------
# CLI path configuration
# ---------------------------------------------------------------------------

_REPO_ROOT = Path(__file__).parent.parent.resolve()
_CLI = _REPO_ROOT / "app" / "build" / "install" / "openehr" / "bin" / "openehr"

# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------

def _run_cli(*args: str) -> dict:
    """Run the SDK CLI and return a structured result dict."""
    if not _CLI.exists():
        return {
            "exit_code": -1,
            "stdout": "",
            "stderr": f"CLI binary not found at {_CLI}. Run './gradlew installDist' first.",
            "success": False,
        }
    try:
        result = subprocess.run(
            [str(_CLI)] + list(args),
            capture_output=True,
            text=True,
            timeout=120,
        )
        return {
            "exit_code": result.returncode,
            "stdout": result.stdout.strip(),
            "stderr": result.stderr.strip(),
            "success": result.returncode == 0,
        }
    except subprocess.TimeoutExpired:
        return {
            "exit_code": -1,
            "stdout": "",
            "stderr": "CLI command timed out after 120 seconds.",
            "success": False,
        }

# ---------------------------------------------------------------------------
# MCP server definition
# ---------------------------------------------------------------------------

server = Server("openehr-cli")


@server.list_tools()
async def list_tools() -> list[Tool]:
    return [
        Tool(
            name="optval",
            description=(
                "Validate an Operational Template (OPT) file against the openEHR XSD schema. "
                "Returns whether the file is valid and any validation errors."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "source": {
                        "type": "string",
                        "description": "Absolute or relative path to the OPT file (.opt)",
                    }
                },
                "required": ["source"],
            },
        ),
        Tool(
            name="ingen",
            description=(
                "Generate sample openEHR instances (COMPOSITION, etc.) from an Operational Template. "
                "Can generate multiple instances in JSON or XML format."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "source": {
                        "type": "string",
                        "description": "Path to OPT file or folder containing OPT files",
                    },
                    "dest": {
                        "type": "string",
                        "description": "Destination folder where generated instances will be written",
                    },
                    "amount": {
                        "type": "integer",
                        "description": "Number of instances to generate (default: 1)",
                        "default": 1,
                    },
                    "format": {
                        "type": "string",
                        "enum": ["json", "xml"],
                        "description": "Output format: json or xml (default: json)",
                        "default": "json",
                    },
                    "type": {
                        "type": "string",
                        "enum": ["locatable", "version"],
                        "description": "Structure type: locatable or version (default: locatable)",
                        "default": "locatable",
                    },
                    "with_participations": {
                        "type": "boolean",
                        "description": "Include participations in generated COMPOSITION (default: false)",
                        "default": False,
                    },
                    "flavor": {
                        "type": "string",
                        "enum": ["rm", "api"],
                        "description": "Data structure flavor: rm (Reference Model) or api (default: rm)",
                        "default": "rm",
                    },
                },
                "required": ["source", "dest"],
            },
        ),
        Tool(
            name="inval",
            description=(
                "Validate openEHR XML or JSON instance files against the appropriate schema. "
                "Supports a single file or an entire folder. "
                "Optionally performs semantic validation against OPT."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "source": {
                        "type": "string",
                        "description": "Path to instance file (.xml or .json) or a folder",
                    },
                    "flavor": {
                        "type": "string",
                        "enum": ["rm", "api"],
                        "description": "Data structure flavor: rm or api (default: rm)",
                        "default": "rm",
                    },
                    "semantic": {
                        "type": "boolean",
                        "description": "Perform semantic validation against OPT (default: false)",
                        "default": False,
                    },
                },
                "required": ["source"],
            },
        ),
        Tool(
            name="adl2opt",
            description=(
                "Convert an ADL (Archetype Definition Language) archetype file "
                "into an Operational Template (OPT)."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "source": {
                        "type": "string",
                        "description": "Path to the ADL archetype file",
                    },
                    "dest": {
                        "type": "string",
                        "description": "Destination folder where the generated OPT will be written",
                    },
                },
                "required": ["source", "dest"],
            },
        ),
        Tool(
            name="trans_opt",
            description="Transform an Operational Template (OPT) from XML format to JSON format.",
            inputSchema={
                "type": "object",
                "properties": {
                    "source": {
                        "type": "string",
                        "description": "Path to the OPT XML file",
                    },
                    "dest": {
                        "type": "string",
                        "description": "Destination folder for the JSON output",
                    },
                },
                "required": ["source", "dest"],
            },
        ),
        Tool(
            name="trans_locatable",
            description=(
                "Transform a Locatable openEHR document between XML and JSON formats. "
                "Input format is detected automatically from the file extension."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "source": {
                        "type": "string",
                        "description": "Path to the Locatable file (.xml or .json)",
                    },
                    "dest": {
                        "type": "string",
                        "description": "Destination folder for the transformed output",
                    },
                },
                "required": ["source", "dest"],
            },
        ),
        Tool(
            name="uigen",
            description=(
                "Generate a Bootstrap HTML UI form from an Operational Template. "
                "Produces an HTML file with input fields for every data element in the template."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "source": {
                        "type": "string",
                        "description": "Path to the OPT file",
                    },
                    "dest": {
                        "type": "string",
                        "description": "Destination folder for the generated HTML",
                    },
                    "bootstrap": {
                        "type": "string",
                        "enum": ["bs4", "bs5"],
                        "description": "Bootstrap version to use: bs4 or bs5 (default: bs5)",
                        "default": "bs5",
                    },
                    "type": {
                        "type": "string",
                        "enum": ["full", "form"],
                        "description": "Generation type: full page or form fragment only (default: full)",
                        "default": "full",
                    },
                },
                "required": ["source", "dest"],
            },
        ),
    ]


@server.call_tool()
async def call_tool(name: str, arguments: dict) -> list[TextContent]:
    if name == "optval":
        result = _run_cli("optval", "-s", arguments["source"])

    elif name == "ingen":
        args = ["ingen", "-s", arguments["source"], "-d", arguments["dest"]]
        if "amount" in arguments:
            args += ["-n", str(arguments["amount"])]
        if "format" in arguments:
            args += ["-f", arguments["format"]]
        if "type" in arguments:
            args += ["-t", arguments["type"]]
        if arguments.get("with_participations"):
            args.append("--with-participations")
        if "flavor" in arguments:
            args += ["--flavor", arguments["flavor"]]
        result = _run_cli(*args)

    elif name == "inval":
        args = ["inval", "-s", arguments["source"]]
        if "flavor" in arguments:
            args += ["--flavor", arguments["flavor"]]
        if arguments.get("semantic"):
            args.append("--semantic")
        result = _run_cli(*args)

    elif name == "adl2opt":
        result = _run_cli("adl2opt", "-s", arguments["source"], "-d", arguments["dest"])

    elif name == "trans_opt":
        result = _run_cli("trans", "opt", "-s", arguments["source"], "-d", arguments["dest"])

    elif name == "trans_locatable":
        result = _run_cli("trans", "locatable", "-s", arguments["source"], "-d", arguments["dest"])

    elif name == "uigen":
        args = ["uigen", "-s", arguments["source"], "-d", arguments["dest"]]
        if "bootstrap" in arguments:
            args += ["--bootstrap", arguments["bootstrap"]]
        if "type" in arguments:
            args += ["--type", arguments["type"]]
        result = _run_cli(*args)

    else:
        result = {"success": False, "stderr": f"Unknown tool: {name}", "stdout": "", "exit_code": -1}

    return [TextContent(type="text", text=json.dumps(result, indent=2))]


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

async def main() -> None:
    async with stdio_server() as (read_stream, write_stream):
        await server.run(
            read_stream,
            write_stream,
            server.create_initialization_options(),
        )


if __name__ == "__main__":
    asyncio.run(main())
