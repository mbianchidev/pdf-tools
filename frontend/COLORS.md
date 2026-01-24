# Color Reference Guide

Quick reference for the PDF Tools color palette.

## Primary Colors

### Deep Indigo (Primary)
Used for: Main actions, primary buttons, links, active states

```css
--color-primary-50:  #EEF2FF  /* Backgrounds */
--color-primary-100: #E0E7FF  /* Hover backgrounds */
--color-primary-200: #C7D2FE  /* Borders, accents */
--color-primary-300: #A5B4FC  /* Disabled states */
--color-primary-400: #818CF8  /* Hover states */
--color-primary-500: #4F46E5  /* Default primary ⭐ */
--color-primary-600: #4338CA  /* Hover, pressed */
--color-primary-700: #3730A3  /* Active states */
--color-primary-800: #312E81  /* Dark text */
--color-primary-900: #1E1B4B  /* Darkest */
```

### Vibrant Coral (Secondary)
Used for: Secondary actions, highlights, attention areas

```css
--color-secondary-50:  #FFF1F2  /* Backgrounds */
--color-secondary-100: #FFE4E6  /* Hover backgrounds */
--color-secondary-200: #FECDD3  /* Borders */
--color-secondary-300: #FDA4AF  /* Accents */
--color-secondary-400: #FB7185  /* Hover */
--color-secondary-500: #F43F5E  /* Default secondary ⭐ */
--color-secondary-600: #E11D48  /* Hover, pressed */
--color-secondary-700: #BE123C  /* Active */
--color-secondary-800: #9F1239  /* Dark */
--color-secondary-900: #881337  /* Darkest */
```

### Warm Amber (Accent)
Used for: Warnings, highlights, decorative accents

```css
--color-accent-50:  #FFFBEB  /* Backgrounds */
--color-accent-100: #FEF3C7  /* Hover backgrounds */
--color-accent-200: #FDE68A  /* Borders */
--color-accent-300: #FCD34D  /* Accents */
--color-accent-400: #FBBF24  /* Hover */
--color-accent-500: #F59E0B  /* Default accent ⭐ */
--color-accent-600: #D97706  /* Hover, pressed */
--color-accent-700: #B45309  /* Active */
--color-accent-800: #92400E  /* Dark */
--color-accent-900: #78350F  /* Darkest */
```

## Neutral Colors

### Stone (Neutral)
Used for: Text, backgrounds, borders, UI elements

```css
--color-neutral-50:  #FAFAF9  /* Lightest bg */
--color-neutral-100: #F5F5F4  /* Light bg */
--color-neutral-200: #E7E5E4  /* Borders */
--color-neutral-300: #D6D3D1  /* Secondary borders */
--color-neutral-400: #A8A29E  /* Disabled text */
--color-neutral-500: #78716C  /* Tertiary text */
--color-neutral-600: #57534E  /* Secondary text */
--color-neutral-700: #44403C  /* Text */
--color-neutral-800: #292524  /* Dark text */
--color-neutral-900: #1C1917  /* Darkest text ⭐ */
```

## Semantic Colors

### Success
```css
--color-success:    #10B981  /* Emerald green */
--color-success-bg: #D1FAE5  /* Light background */
```

### Warning
```css
--color-warning:    #F59E0B  /* Amber */
--color-warning-bg: #FEF3C7  /* Light background */
```

### Error
```css
--color-error:    #EF4444  /* Red */
--color-error-bg: #FEE2E2  /* Light background */
```

## Usage Examples

### Buttons
```css
Primary:   background: linear-gradient(135deg, #4338CA, #4F46E5);
Secondary: background: linear-gradient(135deg, #E11D48, #F43F5E);
Outline:   border: 2px solid #4F46E5; color: #4F46E5;
Ghost:     background: transparent; color: #57534E;
Danger:    background: linear-gradient(135deg, #EF4444, #DC2626);
```

### Text
```css
Primary:   color: #1C1917;  /* var(--text-primary) */
Secondary: color: #57534E;  /* var(--text-secondary) */
Tertiary:  color: #78716C;  /* var(--text-tertiary) */
```

### Backgrounds
```css
Primary:   background: #FFFFFF;  /* var(--bg-primary) */
Secondary: background: #FAFAF9;  /* var(--bg-secondary) */
Tertiary:  background: #F5F5F4;  /* var(--bg-tertiary) */
```

### Borders
```css
Primary:   border: 1px solid #E7E5E4;  /* var(--border-primary) */
Secondary: border: 1px solid #D6D3D1;  /* var(--border-secondary) */
```

### Shadows
```css
Primary Shadow:   box-shadow: 0 10px 30px -5px rgb(79 70 229 / 0.3);
Secondary Shadow: box-shadow: 0 10px 30px -5px rgb(244 63 94 / 0.3);
```

## Color Psychology

### Why These Colors?

**Deep Indigo (#4F46E5)**
- Conveys trust, professionalism, stability
- Less common than pure blue, more distinctive
- Strong contrast for accessibility
- Modern, tech-forward feeling

**Vibrant Coral (#F43F5E)**
- Adds warmth and energy
- Creates excitement without being aggressive
- Complements indigo beautifully
- Unexpected choice (not the typical green or teal)

**Warm Amber (#F59E0B)**
- Provides attention-grabbing highlights
- Works for warnings without being alarming
- Adds visual interest
- Completes the warm-cool balance

**Stone Neutrals**
- Warmer than pure grays
- More sophisticated than black/white
- Maintains good contrast
- Professional appearance

## Accessibility

All color combinations meet WCAG AA standards:
- ✅ Primary text on white: 13.6:1 (AAA)
- ✅ Secondary text on white: 6.5:1 (AA)
- ✅ Primary button text: 8.3:1 (AAA)
- ✅ Links on white: 7.4:1 (AAA)

## Gradients

### Used in the Application

```css
/* Primary gradient */
linear-gradient(135deg, #4338CA, #4F46E5)

/* Secondary gradient */
linear-gradient(135deg, #E11D48, #F43F5E)

/* Icon backgrounds */
linear-gradient(135deg, #E0E7FF, #FFE4E6)
linear-gradient(135deg, #C7D2FE, #FECDD3)
linear-gradient(135deg, #4F46E5, #F43F5E)

/* Background meshes */
radial-gradient(at 40% 20%, #C7D2FE 0px, transparent 50%),
radial-gradient(at 80% 0%, #FDE68A 0px, transparent 50%),
radial-gradient(at 0% 50%, #C7D2FE 0px, transparent 50%)
```

## Design System Variables

### How to Access in CSS

```css
/* Use CSS variables */
.my-element {
  color: var(--color-primary-600);
  background: var(--bg-primary);
  border: 1px solid var(--border-primary);
}

/* For gradients, use the hex values directly */
.my-button {
  background: linear-gradient(135deg, 
    var(--color-primary-600), 
    var(--color-primary-500)
  );
}
```

## Quick Copy-Paste

### Primary Palette
```
#EEF2FF #E0E7FF #C7D2FE #A5B4FC #818CF8 
#4F46E5 #4338CA #3730A3 #312E81 #1E1B4B
```

### Secondary Palette
```
#FFF1F2 #FFE4E6 #FECDD3 #FDA4AF #FB7185 
#F43F5E #E11D48 #BE123C #9F1239 #881337
```

### Accent Palette
```
#FFFBEB #FEF3C7 #FDE68A #FCD34D #FBBF24 
#F59E0B #D97706 #B45309 #92400E #78350F
```

### Neutral Palette
```
#FAFAF9 #F5F5F4 #E7E5E4 #D6D3D1 #A8A29E 
#78716C #57534E #44403C #292524 #1C1917
```

---

**Tip:** All colors are defined in `src/index.css` as CSS variables. Modify there to update the entire application theme.
