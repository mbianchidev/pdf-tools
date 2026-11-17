# Markdown to PDF

Operation key: `markdown-to-pdf`

Markdown to PDF renders one UTF-8 Markdown document, or a ZIP bundle containing
Markdown plus images, into one PDF. It reuses the [HTML to PDF](html-to-pdf.md)
print path: the backend turns Markdown into one self-contained HTML document,
then the networkless Playwright/Chromium sidecar prints it.

## Inputs

- `.md` or `.markdown`: one strict UTF-8 document (BOM allowed, no null
  characters, not blank).
- `.zip`: a bundle such as the [PDF to Markdown](pdf-to-markdown.md) output.
  The renderer uses `document.md` when present; otherwise the bundle must have
  exactly one top-level `.md` or `.markdown` file. A ZIP that nests everything
  under one folder, as "compress folder" tools do, is treated as rooted at that
  folder. `__MACOSX/` and `.DS_Store` entries are ignored. Absolute,
  backslash, drive-letter, `.`/`..`, and duplicate entry names reject the whole
  bundle.

The web page also accepts pasted text, which it uploads as `document.md`.

## Options

```json
{
  "pageSize": "a4",
  "orientation": "portrait",
  "printBackground": true,
  "marginMm": 10,
  "outputFilename": "document.pdf"
}
```

The print options and their validation are shared with HTML to PDF, so invalid
values return the same `INVALID_HTML_*` error codes. `outputFilename` is
optional, must end in `.pdf` within 120 UTF-8 bytes, and defaults to the source
name with `.pdf`.

## Rendering

Markdown is parsed with flexmark using CommonMark plus GitHub-style tables,
strikethrough, task lists, autolinks, footnotes, and heading IDs. Tables
follow GitHub's rules: one header row, delimiter cells with a single dash
(`|-|:-:|`), rows with or without outer pipes, and tables that start directly
under a line of paragraph text. A `[text]` line under a table stays a
paragraph rather than becoming a caption.

GitHub alerts (`> [!NOTE]`, `> [!TIP]`, `> [!IMPORTANT]`, `> [!WARNING]`, and
`> [!CAUTION]`, with the marker alone on the first quote line) render as
titled, colored callouts with an icon. Emoji shortcodes such as `:rocket:` or
`:warning:` render as color emoji; unknown shortcodes stay as text. Both
`~one~` and `~~two~~` tildes strike text through, as on GitHub. YAML front
matter is parsed and not printed. A fixed print stylesheet handles typography,
code blocks, tables, block quotes, task lists, footnotes, and page-break hints.
The document title comes from the first level-one heading.

Untrusted content is constrained before Chromium sees it:

- Raw HTML blocks and inline HTML are escaped and printed as literal text.
- Links keep only `http`, `https`, `mailto`, `www.` autolinks, and in-document
  `#fragment` targets, and stay clickable in the PDF. Heading IDs follow
  GitHub's rules, so table-of-contents and footnote links jump within the
  document. Relative links such as `docs/setup.md` cannot resolve inside a
  standalone PDF, so their text is followed by the target in parentheses.
  Other schemes, including `javascript:` and `file:`, print as plain text.
- Images are embedded only from bundle-relative paths or existing
  `data:image/...;base64` URIs, and only when the bytes are PNG, JPEG, GIF, or
  WebP. SVG, remote, missing, out-of-bundle, and unsupported images are printed
  as their alt text. Nothing is fetched.
- The generated page has no scripts and carries its own Content Security Policy
  (`default-src 'none'; img-src data:; style-src 'unsafe-inline'`), in addition
  to the sidecar's injected policy and request blocking.

## Isolation and limits

Parsing, ZIP reading, image embedding, and HTML generation run in a killable
Java subprocess with a 256 MiB heap and one-minute wall timeout. The ZIP is read
through its central directory. Only the selected Markdown entry and the images
it references are decompressed, each through a bounded stream. Deeply nested
input fails with `MARKDOWN_TOO_COMPLEX`.

Defaults:

- 2 MiB Markdown text (`MARKDOWN_MAX_INPUT_BYTES`), for both `.md` uploads and
  the Markdown entry inside a bundle;
- 16 MiB ZIP upload (`MARKDOWN_MAX_BUNDLE_BYTES`) with at most 500 entries
  (`MARKDOWN_MAX_BUNDLE_ENTRIES`);
- 200 embedded images (`MARKDOWN_MAX_IMAGES`) and 6 MiB of embedded image bytes
  (`MARKDOWN_MAX_TOTAL_IMAGE_BYTES`);
- `MARKDOWN_WORKER_HEAP_BYTES` and `MARKDOWN_WORKER_TIMEOUT` for the subprocess.

The rendered HTML, including base64 images, must fit `HTML_MAX_INPUT_BYTES`
(10 MiB by default) or the job fails with `MARKDOWN_RENDERED_HTML_TOO_LARGE`.
Raise both image and HTML limits together for image-heavy bundles. Output page
count, output size, and print time follow the `HTML_*` limits, and the HTML
sidecar must be running.

## Fidelity

Output follows a fixed stylesheet, not a specific Markdown site theme. Fonts
come from the sidecar image, so emoji and uncommon scripts depend on installed
system fonts. There is no syntax highlighting, math, Mermaid, or other
diagram rendering, and raw HTML, including tags GitHub allows such as
`<details>`, `<kbd>`, and `<sub>`, never renders as markup.
