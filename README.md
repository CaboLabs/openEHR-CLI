# openehr-cli

CLI to facilitate working with openEHR artifacts such as Operational Templates (OPTs), clinical instances, and ADL archetypes.

## Build

```bash
./gradlew installDist
```

The executable is placed at `app/build/install/openehr/bin/openehr`.

## Usage

```
openehr [-hV] <command> [options]
```

Global options:

| Option | Description |
|--------|-------------|
| `-h, --help` | Show help and exit |
| `-V, --version` | Print version and exit |

---

## Commands

### `uigen` – Generate UI from an Operational Template

Generates an HTML form or full page from an OPT using Bootstrap.

```
openehr uigen -s <source> -d <dest> [--bootstrap <version>] [--type <type>]
```

| Option | Required | Default | Values | Description |
|--------|----------|---------|--------|-------------|
| `-s, --source` | yes | — | file path | Path to OPT file |
| `-d, --dest` | yes | — | folder path | Destination folder |
| `--bootstrap` | no | `bs5` | `bs4`, `bs5` | Bootstrap version |
| `--type` | no | `full` | `full`, `form` | Generation type: full HTML page or form fragment |

**Example:**

```bash
openehr uigen -s ./templates/blood_pressure.opt -d ./output --bootstrap bs5 --type form
```

---

### `ingen` – Generate instances from an Operational Template

Generates synthetic clinical instances (compositions or other locatables) from an OPT.

```
openehr ingen -s <source> -d <dest> [-n <amount>] [-f <format>] [-t <type>] [--flavor <flavor>] [--with-participations]
```

| Option | Required | Default | Values | Description |
|--------|----------|---------|--------|-------------|
| `-s, --source` | yes | — | file or folder path | Path to OPT file or folder of OPTs |
| `-d, --dest` | yes | — | folder path | Destination folder |
| `-n, --amount` | no | `1` | integer > 0 | Number of instances to generate per OPT |
| `-f, --format` | no | `json` | `json`, `xml` | Output format |
| `-t, --type` | no | `locatable` | `locatable`, `version` | Whether to wrap output in a Version container |
| `--flavor` | no | `rm` | `rm`, `api` | Data structure flavor: Reference Model or REST API |
| `--with-participations` | no | false | flag | Add participations (COMPOSITION templates only) |

**Example:**

```bash
openehr ingen -s ./templates/ -d ./output -n 5 -f json -t version --flavor api
```

---

### `optval` – Validate an Operational Template

Validates an OPT XML file against the openEHR XSD schema.

```
openehr optval -s <source>
```

| Option | Required | Description |
|--------|----------|-------------|
| `-s, --source` | yes | Path to OPT file |

**Example:**

```bash
openehr optval -s ./templates/blood_pressure.opt
```

---

### `inval` – Validate clinical instances

Validates XML or JSON clinical instances against their schemas. Optionally performs semantic validation against the source OPT.

```
openehr inval -s <source> [--flavor <flavor>] [--semantic]
```

| Option | Required | Default | Values | Description |
|--------|----------|---------|--------|-------------|
| `-s, --source` | yes | — | file or folder path | Path to instance file or folder |
| `--flavor` | no | `rm` | `rm`, `api` | Data structure flavor: Reference Model or REST API |
| `--semantic` | no | false | flag | Perform semantic validation against OPT |

**Example:**

```bash
openehr inval -s ./instances/ --flavor api --semantic
```

---

### `trans` – Transform between formats

Parent command for format transformations. Use one of its subcommands:

#### `trans opt` – Transform OPT from XML to JSON

```
openehr trans opt -s <source> -d <dest>
```

| Option | Required | Description |
|--------|----------|-------------|
| `-s, --source` | yes | Path to OPT XML file |
| `-d, --dest` | yes | Destination folder |

**Example:**

```bash
openehr trans opt -s ./templates/blood_pressure.opt -d ./output/json/
```

#### `trans locatable` – Transform Locatable between XML and JSON

Detects the input format from the file extension (`.xml` or `.json`) and converts to the other format.

```
openehr trans locatable -s <source> -d <dest>
```

| Option | Required | Description |
|--------|----------|-------------|
| `-s, --source` | yes | Path to Locatable file (.xml or .json) |
| `-d, --dest` | yes | Destination folder |

**Example:**

```bash
openehr trans locatable -s ./instances/composition.xml -d ./output/json/
```

---

### `adl2opt` – Generate OPT from an ADL archetype

Transforms an ADL archetype file into an Operational Template (OPT).

```
openehr adl2opt -s <source> -d <dest>
```

| Option | Required | Description |
|--------|----------|-------------|
| `-s, --source` | yes | Path to ADL file |
| `-d, --dest` | yes | Destination folder |

**Example:**

```bash
openehr adl2opt -s ./archetypes/openEHR-EHR-OBSERVATION.blood_pressure.v2.adl -d ./output/
```

---

### `diff` – Diff two Operational Templates

Compares two OPTs and reports what changed between them, as a tree.

```
openehr diff --old <old> --new <new> [--mode <mode>] [--format <format>] [--full] [--no-color]
```

| Option | Required | Default | Values | Description |
|--------|----------|---------|--------|-------------|
| `--old` | yes | — | file path | Path to the old/baseline OPT file |
| `--new` | yes | — | file path | Path to the new OPT file |
| `--mode` | no | `semantic` | `semantic`, `structural` | `semantic` reports field-level constraint changes (text, occurrences, code lists, ranges, etc.) and flags the breaking ones; `structural` only reports whether a node's path was added/removed/kept |
| `--format` | no | `tree` | `tree`, `json` | Output format |
| `--full` | no | false | flag | Include unchanged (`same`) nodes in the tree instead of collapsing them |
| `--no-color` | no | false | flag | Disable ANSI colors in tree output |

Output is a tree prefixed with `+`/`-`/`~`/` ` (added/removed/modified/same), collapsed to only the
changed paths by default, ending with a summary line. For large diffs, pipe the tree output to
`less`:

```bash
openehr diff --old ./templates/blood_pressure_v1.opt --new ./templates/blood_pressure_v2.opt | less
```

**Example:**

```bash
openehr diff --old ./templates/blood_pressure_v1.opt --new ./templates/blood_pressure_v2.opt --mode semantic
```

#### Reading the semantic diff report

```
~ /content[...]/data[at0001]/items[at0004] 'count node 1' [modified]
    ~ .../items[at0004]/value/magnitude [modified]
        range: (*..*) -> [0..*)  [POTENTIAL BREAKING]
- .../items[at0006] 'ordinal node 1' [removed]  [CONFIRMED BREAKING]

2 confirmed breaking, 6 potential breaking (potential ones need a check against existing data)
```

**Status prefix:** `+` added, `-` removed, `~` modified, blank = same (only shown with `--full`). The quoted text after the path is the text of the OPT node, resolved from its `node_id`. Nodes without a `node_id` get a synthetic text, the dotted attribute path (e.g. `count node 1.value`).

**Breaking tags.** The tags answer: *could data that was valid against the old OPT be invalid against the new one?*

| Tag | Meaning | What to do |
|-----|---------|------------|
| `[CONFIRMED BREAKING]` | The change alone invalidates data that was valid under the old OPT, no need to look at the data. E.g. an optional node made required, a new required node, a mandatory node removed, a range or a list the old OPT had that got tighter, a code or unit removed. | Migrate or fix the data before moving to the new OPT. |
| `[POTENTIAL BREAKING]` | Breaks only the data that has certain values, or the queries that use the path. E.g. a constraint over something the old OPT didn't constrain, an optional node removed. | Check the existing data (it can be automated); if no data has those values, nothing breaks. |
| no tag | Not breaking: e.g. an optional new node, a code added to a list, a constraint relaxed, text changes. | |

**What "not constrained" means.** An OPT only states what it constrains, the rest is ruled by the openEHR RM, so the old value of a constraint that the old OPT didn't have is the most open expression the RM allows, never empty:

- `(*..*)` for an interval, e.g. `DV_COUNT.magnitude` allows negatives in the RM, so `[0..*)` is a narrowing, not a new constraint.
- `any` for a pattern or a list of strings.
- The RM enumeration for enumerated attributes, e.g. `DV_PROPORTION.type: [0, 1, 2, 3, 4] -> [2]`.
- `-` only when there is no value to show, e.g. a text that was empty.

Because the RM already defines those attributes, a constraint on them is `modified` (from the RM default to the new constraint), not `added`.

**Added and removed nodes.** Only the added/removed node itself is evaluated, not what is below it: an optional new node with a required `value` is not breaking (no legacy data has the node), and a removed node is reported once, at the node.

**Summary lines.** The first line counts nodes (`added`, `removed`, `modified`, `same`), the last one counts breaking changes. Breaking changes that have no line to carry the tag (e.g. a removed attribute) are listed under `Other breaking changes`.

With `--format json`, the output includes a top-level `breakingChanges` list with `templatePath`, `category`, `field`, `oldValue`, `newValue`, `reason`, `certain` and `level` (`confirmed` or `potential`). Node diffs use `text` for the node text (it was `name` before openEHR-SDK 2.7.5).

The full rules (categories, RM baselines, how added/removed subtrees are treated) are documented in the openEHR-SDK README, section *Breaking-change detection*.

---

## MCP Server

The `mcp_server/` directory contains a [Model Context Protocol](https://modelcontextprotocol.io) server that exposes all CLI commands as tools, allowing LLMs like Claude to work with openEHR artifacts directly in conversation.

### Setup

**1. Install Python dependencies**

On Debian/Ubuntu, first ensure `venv` is available:

```bash
sudo apt install python3-venv
```

Then create the virtualenv and install dependencies:

```bash
python3 -m venv mcp_server/.venv
source mcp_server/.venv/bin/activate
pip install -r mcp_server/requirements.txt
```

**2. Build the CLI** (required before starting the server)

```bash
./gradlew installDist
```

**3. Register with an MCP-compatible client**

The server follows the open [MCP protocol](https://modelcontextprotocol.io) and works with any compatible client — not just Claude Code. Examples:

| Client | MCP support |
|--------|-------------|
| [Claude Code](https://claude.ai/code) | Native — `claude mcp add` |
| [Claude.ai](https://claude.ai) desktop app | Native — Settings → Integrations (Pro/Team/Enterprise) |
| [Cursor](https://cursor.com) | Native — `.cursor/mcp.json` |
| [Cline](https://github.com/cline/cline), [Continue](https://continue.dev), [Windsurf](https://windsurf.com) | Native MCP support |
| ChatGPT / OpenAI | No native support; community adapters available |

**Claude Code example:**

```bash
claude mcp add openehr-cli mcp_server/.venv/bin/python3 /path/to/openEHR-CLI/mcp_server/server.py
```

**Generic `mcp.json` config** (for Cursor and other JSON-configured clients):

```json
{
  "mcpServers": {
    "openehr-cli": {
      "command": "/path/to/openEHR-CLI/mcp_server/.venv/bin/python3",
      "args": ["/path/to/openEHR-CLI/mcp_server/server.py"]
    }
  }
}
```

### Available tools

| Tool | Equivalent CLI command | Description |
|------|----------------------|-------------|
| `optval` | `openehr optval` | Validate an OPT against the XSD schema |
| `ingen` | `openehr ingen` | Generate clinical instances from an OPT |
| `inval` | `openehr inval` | Validate XML/JSON instances against schemas |
| `adl2opt` | `openehr adl2opt` | Convert an ADL archetype to OPT |
| `trans_opt` | `openehr trans opt` | Transform OPT from XML to JSON |
| `trans_locatable` | `openehr trans locatable` | Transform a Locatable between XML and JSON |
| `uigen` | `openehr uigen` | Generate a Bootstrap HTML UI from an OPT |

### Example prompts

Once registered, you can ask Claude things like:

> *"Validate the template at `./templates/blood_pressure.opt` and tell me if it's valid."*

> *"Generate 3 sample JSON compositions from `./templates/blood_pressure.opt` and save them to `./output/`."*

> *"Validate all the instances in `./output/` using the api flavor."*

> *"Convert `./templates/blood_pressure.opt` to JSON and save it to `./output/json/`."*

> *"Generate an HTML form for `./templates/blood_pressure.opt` using Bootstrap 5 and save it to `./ui/`."*

> *"I have an ADL archetype at `./archetypes/blood_pressure.adl` — convert it to an OPT and save to `./templates/`."*
