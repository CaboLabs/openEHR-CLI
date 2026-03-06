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

## MCP Server

The `mcp_server/` directory contains a [Model Context Protocol](https://modelcontextprotocol.io) server that exposes all CLI commands as tools, allowing LLMs like Claude to work with openEHR artifacts directly in conversation.

### Setup

**1. Install Python dependencies**

```bash
pip install -r mcp_server/requirements.txt
```

**2. Build the CLI** (required before starting the server)

```bash
./gradlew installDist
```

**3. Register with Claude Code**

```bash
claude mcp add openehr-cli python3 /path/to/openEHR-CLI/mcp_server/server.py
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
