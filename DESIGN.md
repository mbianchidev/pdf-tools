---
name: PDF Tools
description: Self-hosted PDF workbench with paper surfaces, ink panels, and indigo proof marks.
colors:
  workbench-ink: "#19172d"
  workbench-ink-soft: "#312e4b"
  cool-paper: "#f7f6fb"
  sheet-white: "#ffffff"
  panel-muted: "#f0eef7"
  rule: "#dedbea"
  rule-strong: "#c8c3da"
  muted-copy: "#6f6b83"
  proof-indigo: "#4f46e5"
  proof-indigo-deep: "#3730a3"
  proof-indigo-soft: "#e8e7ff"
  signal-coral: "#ff706b"
  signal-aqua: "#35c2b8"
  focus-sun: "#f2ce57"
  danger: "#c43c52"
  success: "#197a64"
typography:
  brand:
    fontFamily: '"Bricolage Grotesque Variable", "Arial Narrow", sans-serif'
    fontSize: "25px"
    fontWeight: 560
    lineHeight: 1
    letterSpacing: "-0.04em"
  display:
    fontFamily: '"Bricolage Grotesque Variable", "Arial Narrow", sans-serif'
    fontSize: "clamp(58px, 7vw, 96px)"
    fontWeight: 720
    lineHeight: 0.86
    letterSpacing: "-0.04em"
    fontVariation: '"wdth" 84'
  headline:
    fontFamily: '"Bricolage Grotesque Variable", "Arial Narrow", sans-serif'
    fontSize: "clamp(38px, 4vw, 58px)"
    fontWeight: 720
    lineHeight: 0.95
    letterSpacing: "-0.04em"
    fontVariation: '"wdth" 92'
  title:
    fontFamily: '"Bricolage Grotesque Variable", "Arial Narrow", sans-serif'
    fontSize: "22px"
    fontWeight: 720
    lineHeight: 1
    letterSpacing: "-0.035em"
  body:
    fontFamily: '"DM Sans Variable", "DM Sans", system-ui, sans-serif'
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.55
    letterSpacing: "normal"
  control:
    fontFamily: '"DM Sans Variable", "DM Sans", system-ui, sans-serif'
    fontSize: "15px"
    fontWeight: 750
    lineHeight: 1
    letterSpacing: "normal"
  label:
    fontFamily: '"DM Sans Variable", "DM Sans", system-ui, sans-serif'
    fontSize: "11px"
    fontWeight: 850
    lineHeight: 1.2
    letterSpacing: "0.09em"
  mono:
    fontFamily: "ui-monospace, monospace"
    fontSize: "10px"
    fontWeight: 700
    lineHeight: 1.4
    letterSpacing: "0.1em"
rounded:
  sm: "0.25rem"
  md: "0.45rem"
  lg: "0.65rem"
  xl: "0.8rem"
  2xl: "1rem"
  3xl: "1.25rem"
  full: "9999px"
spacing:
  space-1: "0.25rem"
  space-2: "0.5rem"
  space-3: "0.75rem"
  space-4: "1rem"
  space-5: "1.25rem"
  space-6: "1.5rem"
  space-8: "2rem"
  space-10: "2.5rem"
  space-12: "3rem"
  space-16: "4rem"
  space-20: "5rem"
  space-24: "6rem"
components:
  brand-navigation:
    backgroundColor: "transparent"
    textColor: "{colors.workbench-ink}"
    typography: "{typography.brand}"
    rounded: "0"
    padding: "0"
    height: "42px"
  button-primary:
    backgroundColor: "{colors.proof-indigo}"
    textColor: "{colors.sheet-white}"
    typography: "{typography.control}"
    rounded: "{rounded.lg}"
    padding: "0 18px"
    height: "44px"
  button-primary-hover:
    backgroundColor: "{colors.proof-indigo-deep}"
    textColor: "{colors.sheet-white}"
    rounded: "{rounded.lg}"
  button-secondary:
    backgroundColor: "{colors.proof-indigo-soft}"
    textColor: "{colors.proof-indigo-deep}"
    typography: "{typography.control}"
    rounded: "{rounded.lg}"
    padding: "0 18px"
    height: "44px"
  button-outline:
    backgroundColor: "{colors.sheet-white}"
    textColor: "{colors.workbench-ink}"
    typography: "{typography.control}"
    rounded: "{rounded.lg}"
    padding: "0 18px"
    height: "44px"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.muted-copy}"
    typography: "{typography.control}"
    rounded: "{rounded.lg}"
    padding: "0 18px"
    height: "44px"
  button-danger:
    backgroundColor: "{colors.danger}"
    textColor: "{colors.sheet-white}"
    typography: "{typography.control}"
    rounded: "{rounded.lg}"
    padding: "0 18px"
    height: "44px"
  text-field:
    backgroundColor: "{colors.sheet-white}"
    textColor: "{colors.workbench-ink}"
    typography: "{typography.body}"
    rounded: "{rounded.lg}"
    padding: "0.75rem 1rem"
  file-dropzone:
    backgroundColor: "{colors.sheet-white}"
    textColor: "{colors.workbench-ink}"
    typography: "{typography.body}"
    rounded: "0"
    padding: "26px"
  proof-terminal:
    backgroundColor: "{colors.workbench-ink}"
    textColor: "{colors.sheet-white}"
    typography: "{typography.mono}"
    rounded: "0"
    padding: "28px"
  workbench-preview:
    backgroundColor: "{colors.workbench-ink}"
    textColor: "{colors.sheet-white}"
    rounded: "0"
    padding: "0"
---

# Design System: PDF Tools

## Overview

**Creative North Star: "The Owned Document Workbench"**

PDF Tools makes self-hosting visible as product proof. The landing surface is a docs-first Persuade experience with a direct bridge into the deployed workspace: it leads with a clear ownership proposition, a concrete PDF workbench artifact, a primary `/new` action, actual clone and Docker Compose commands, then a linked inventory of available workflows. The operation routes switch to Operate mode without changing worlds: a persistent paper topbar switches workflows, controls live on light paper panels, and documents are handled on a dark utility workbench.

The visual language is pinned to the `img-tools` family through the `pinned-reference` seed key while retaining PDF Tools' indigo identity. Variable Bricolage Grotesque supplies compressed, assertive display shapes; DM Sans handles instructional copy and controls; paper-white surfaces sit against a cool page; ink panels and monospace status strips make processing feel observable; indigo appears as proof, focus, and offset geometry. Motion is short and physical—load-in fades, small translations, restrained rotations, and direct hover lifts—and always supports hierarchy or state.

**Key Characteristics:**

- Self-hosting commands are evidence, not tertiary developer copy.
- The first viewport pairs the ownership headline on the left with the PDF artifact on the right.
- The landing reads like installation documentation with a persuasive thesis while giving deployed users a clear path into `/new`.
- Capability rows link to their workflows without replacing the deployment proof hierarchy.
- Operation pages use a persistent workflow topbar above a paper control rail and ink preview canvas.
- Square panels and indigo offset shadows form the family signature; rounded shapes are concentrated on controls.
- The pinned `img-tools` family language is the visual authority, not a generic PDF SaaS template.

**The Proof-Before-Capability Rule.** Keep the real self-host path before the installed feature inventory; workflow links may activate the deployed product without displacing its ownership proof.

**The Dual-Mode Rule.** The landing persuades through documentation and ownership evidence; operation routes switch to compact, task-first workbench behavior without changing the visual world.

## Colors

The palette sets cool paper and dark ink in deliberate contrast, with indigo as the single brand authority and small signal colors reserved for utility feedback.

### Primary

- **Proof Indigo:** Marks the product name, primary actions, active selections, PDF identity, links, and hard offset shadows.
- **Deep Proof Indigo:** Supplies hover emphasis and the stronger edge of indigo interactions.
- **Soft Proof Indigo:** Creates selected rows, upload hover fields, and quiet indigo-backed controls without turning the page into a color wash.

### Tertiary

- **Signal Coral:** Appears in terminal-style status dots and destructive-adjacent visual signals.
- **Signal Aqua:** Marks ready states and terminal/workbench status.
- **Focus Sun:** Owns the high-contrast global focus outline and one of the three machine-status dots.
- **Danger:** Handles destructive actions and validation errors.
- **Success:** Handles trust copy, successful states, and positive job feedback.

### Neutral

- **Workbench Ink:** Grounds terminals, preview canvases, brand marks, primary-button plinths, and the strongest text.
- **Soft Workbench Ink:** Supports secondary dark text and patterned workbench geometry.
- **Cool Paper:** Fills the page and route-loading surface.
- **Sheet White:** Holds instructional panels, sidebars, inputs, cards, and simulated documents.
- **Muted Panel:** Separates secondary controls and uploaded-file collections from white paper.
- **Rule / Strong Rule:** Structure adjacent surfaces, controls, and dashed upload boundaries.
- **Muted Copy:** Carries supporting instructions, descriptions, metadata, and quiet navigation.

**The Indigo Proof Rule.** Indigo marks ownership, primary actions, active state, PDF identity, and offset geometry; it does not become a full-page wash.

**The Paper-on-Ink Rule.** Instructions and controls sit on paper; terminals and PDF canvases sit on ink.

**The Signal-Dot Rule.** Coral, aqua, and sun are small signals or focus cues, never competing brand accents.

## Typography

**Display Font:** Bricolage Grotesque Variable, with Arial Narrow and sans-serif fallbacks
**Body Font:** DM Sans Variable, with DM Sans, system UI, and sans-serif fallbacks
**Label/Mono Font:** The body family for labels; the platform UI monospace stack for commands and telemetry

**Character:** Bricolage is compact, slightly industrial, and expressive enough to make ownership feel like a product claim rather than a slogan. DM Sans stays neutral and highly legible for installation instructions, tool descriptions, dense controls, and status copy.

### Hierarchy

- **Display:** Reserved for the first-view ownership proposition and the oversized PDF mark inside the hero artifact.
- **Headline:** Used for major landing sections and their docs-like milestones.
- **Title:** Used for operation names, upload prompts, capability groups, and other compact landmarks.
- **Body:** Used for installation guidance, descriptions, control labels, and operational instructions.
- **Label:** Used for uppercase sidebar sections, file-list headings, and compact metadata.
- **Mono:** Used for shell commands, terminal chrome, runtime labels, and machine-readable status.

**The Proposition/Instruction Rule.** Bricolage makes propositions and section landmarks; DM Sans explains, labels, and controls.

**The System-Evidence Rule.** Monospace appears only where the interface is showing commands, runtime labels, or machine status.

## Layout

The landing uses a centered shell capped at 1180px with 48px total viewport inset. Its first viewport is a two-column split—slightly wider copy, slightly narrower artifact—with a minimum height of 670px, large fluid gap, and generous vertical breathing room. At 860px and below, the columns become a single stack while preserving copy before artifact. The three deployment steps and three capability columns also collapse to one column at that breakpoint; capability columns are staggered by 30px and 60px only on wide screens. At 768px and 560px, shell insets, type, artifact shadow, and header controls tighten without changing the story order.

The workspace fills the dynamic viewport (`100dvh`). A persistent 64px paper topbar provides the compact brand, `/new`, current workflow, and a grouped tool switcher. Each operation retains its compact header; below it, a 360px paper sidebar holds files, settings, and actions while the flexible remainder becomes the dark PDF preview. The sidebar scrolls independently and keeps primary actions at its end. At 1024px and below, controls stack above the preview, the rail becomes full width, and its height is capped at half the viewport. At 560px, the topbar compacts to icon-first controls, the back control becomes icon-only, and header copy compacts.

Spacing follows the shipped 4px-based scale, but the page rhythm is intentionally broad on Persuade surfaces and compact on Operate surfaces. Landing sections use 68–104px vertical intervals; workbench chrome and control groups commonly use 10–24px intervals.

**The Ownership Split Rule.** On wide screens, the first viewport keeps the ownership claim on the left and the PDF artifact on the right; collapse preserves that order.

**The Workbench Rail Rule.** Operation controls remain in a 360px paper rail beside the flexible ink preview until the 1024px stack breakpoint.

## Elevation & Depth

Depth is hybrid and semantic. Thin cool rules and tonal changes separate most stationary surfaces. Hard, unblurred offsets make owned artifacts and decisive actions feel physical, while ambient shadows appear only beneath the hero document stack or lightly under resting white surfaces. The dark operation preview stays flat so document content, not chrome, receives attention.

### Shadow Vocabulary

- **Brand Offset** (`5px 5px 0 #4f46e5`): Gives the tilted brand mark its compact ownership stamp.
- **Hero Artifact Offset** (`22px 22px 0 #4f46e5`): Anchors the main PDF workbench; it reduces to 12px on small screens.
- **Terminal Proof Offset** (`14px 14px 0 #4f46e5`): Makes the real installation command feel like a primary product artifact.
- **Action Plinth** (`0 7px 0 #19172d`): Supports primary and danger buttons; hover increases it to 9px.
- **Quiet Surface** (`0 1px 2px rgb(25 23 45 / 6%)`): Gives resting light containers minimal separation.
- **Artifact Ambient** (`0 30px 80px rgb(35 28 78 / 18%)`): Adds atmosphere behind the hero workbench without softening its hard indigo offset.
- **Upload Lift** (`8px 8px 0 #e8e7ff`): Appears with a -3px translation when the upload target is hovered or active.

**The Offset-Is-Structural Rule.** Hard indigo or ink offsets identify owned artifacts and decisive actions; they are not generic decoration for every container.

**The Flat-Workbench Rule.** Control rails, capability lists, and preview chrome use borders and tonal contrast at rest; ambient lift is reserved for the hero artifact or interaction.

## Shapes

Large surfaces are square or nearly square: the deployment terminal, capability columns, upload target, workbench panels, preview chrome, and paper sheets rely on straight edges and borders. Interactive controls use restrained 7–10px rounding, while full pills are reserved for page badges, compact indicators, and scrollbars. The brand mark is a rotated square, the hero papers overlap at distinct angles, and the proof badge tilts against the otherwise disciplined grid. Circles are limited to terminal dots, step numbers, and similarly small status devices.

**The Square-Surface Rule.** Large surfaces, upload zones, terminals, and workbench panels stay square or nearly square; rounded corners belong primarily to controls and small states.

**The Controlled-Skew Rule.** Rotation is limited to the brand mark, stacked paper artifact, and proof badge; functional controls remain aligned.

## Components

### Brand and Navigation

- **Brand lockup:** A 42px ink square with a white stacked-file glyph, 5px indigo offset, and -3-degree rotation precedes the lowercase Bricolage wordmark; “tools” is indigo.
- **Compact lockup:** The footer uses a 34px mark, 3px offset, and reduced wordmark.
- **Landing navigation:** A plain self-host anchor receives an indigo underline on hover; the GitHub action is a quiet bordered button. The hero adds an indigo `/new` workspace action. At 860px the text anchor hides, and at 560px the GitHub action becomes icon-only.
- **Focus:** Every interactive element receives the global 3px sun outline with a 3px offset.

### Buttons

- **Shape:** Controls use a 10px corner treatment and 38px, 44px, or 52px heights.
- **Primary:** White type on proof indigo with an ink action plinth; hover darkens the indigo, lifts 2px, and deepens the plinth.
- **Secondary:** Deep indigo type on soft indigo with a quiet indigo border.
- **Outline / Ghost:** White or transparent backgrounds retain visible cool rules; hover shifts both toward soft indigo.
- **Danger:** White type on danger red with the same ink plinth as the primary action.
- **State:** Active returns the control to its baseline; disabled controls reduce opacity to 45%; loading replaces content with a compact spinner.

### Inputs / Fields

- **Style:** White fields use a 2px cool rule, restrained rounded corners, and 12px by 16px internal padding.
- **Focus:** The border becomes proof indigo and gains a 3px soft-indigo halo.
- **Error / Disabled:** Errors use danger border, copy, and halo; disabled fields move to the muted panel with tertiary text.
- **Labels:** Labels use DM Sans at the compact body scale; required markers use danger.

### File Upload

- **Resting target:** A square, minimum-190px white panel combines a faint diagonal indigo pattern with a 2px dashed strong rule.
- **Icon tile:** A square soft-indigo tile holds the upload glyph above a Bricolage prompt and quiet helper copy.
- **Hover / drag:** The border becomes indigo, the panel moves -3px on both axes, and an 8px soft-indigo offset appears.
- **Uploaded files:** Files move into a muted paper collection with white bordered rows, square indigo icon tiles, and quiet metadata.

### Deployment Terminal

- **Purpose:** This is product proof, not decorative code. It contains the shipped clone, directory, and Docker Compose commands.
- **Structure:** Ink body, subtle dark border, three signal dots, uppercase mono chrome, copy action, spacious command area, and a status footer.
- **Depth:** A 14px indigo offset distinguishes it from ordinary instructional panels.

### PDF Workbench Artifact

- **Structure:** An ink board contains patterned preview space, three overlapping white paper sheets, compact system chrome, and a status strip.
- **Identity:** The front sheet carries the indigo PDF mark; a tilted indigo badge states that the app runs where it is deployed.
- **Motion:** The artifact enters with opacity, scale, and restrained rotation; the board itself rests at a slight positive angle.

### Capability Columns

- **Behavior:** The bordered groups inventory Organize, Mark up, Secure, and Convert capabilities; each full row is a workflow link.
- **Rhythm:** Each row uses an indigo icon, compact title, quiet description, directional arrow, and a dividing rule.
- **Composition:** Wide screens stagger the columns vertically; compact screens remove the stagger and stack them.

### Operation Workbench

- **Workspace topbar:** A persistent paper bar exposes `/new` and a grouped tool switcher, marks the current workflow, and retains the active PDF between compatible tools. Its dropdown becomes a scrollable single-column list on narrow screens.
- **Header:** A compact paper bar combines a bordered back control, Bricolage operation title, and right-aligned description.
- **Control rail:** A 360px white sidebar uses uppercase labels, compact groups, clear dividers, and a pinned action region.
- **Preview:** Ink canvas and darker preview chrome use white or lavender-gray status text; empty states remain subdued.
- **Responsive behavior:** At the workbench breakpoint, controls stack above a preview with at least half a viewport of height.

## Do's and Don'ts

### Do:

- **Do** preserve the pinned `img-tools` family language: Bricolage display type, DM Sans utility copy, paper surfaces, ink workbenches, and indigo offsets.
- **Do** lead the landing with the ownership proposition and PDF artifact, then show real self-host commands before installed capabilities.
- **Do** use ink for terminals and document previews, and paper for instructions, controls, and uploaded-file structure.
- **Do** keep workflow links informational, visibly grouped, and secondary to the landing's deployment proof.
- **Do** retain the 360px desktop operation rail and the 1024px control-above-preview stack behavior.
- **Do** keep motion quick, physical, and state-linked, with reduced-motion behavior respected globally.

### Don't:

- **Don't** replace the landing with a generic grid of undifferentiated PDF tool cards.
- **Don't** put the capability inventory before the real deployment proof.
- **Don't** swap the Bricolage/DM Sans pairing for a neutral system-only or conventional SaaS type stack.
- **Don't** round every panel, add diffuse shadows to every surface, or dilute the hard-offset signature.
- **Don't** use coral, aqua, or sun as competing CTA colors; indigo remains the sole brand authority.
- **Don't** fabricate screenshots, usage metrics, testimonials, or fidelity claims to make the landing feel more commercial.
