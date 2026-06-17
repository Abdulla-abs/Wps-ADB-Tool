---
name: Carbon Terminal
colors:
  surface: '#111316'
  surface-dim: '#111316'
  surface-bright: '#37393d'
  surface-container-lowest: '#0c0e11'
  surface-container-low: '#1a1c1f'
  surface-container: '#1e2023'
  surface-container-high: '#282a2d'
  surface-container-highest: '#333538'
  on-surface: '#e2e2e6'
  on-surface-variant: '#bbcbbc'
  inverse-surface: '#e2e2e6'
  inverse-on-surface: '#2f3034'
  outline: '#869587'
  outline-variant: '#3c4a3f'
  surface-tint: '#43e188'
  primary: '#60f99e'
  on-primary: '#00391c'
  primary-container: '#3ddc84'
  on-primary-container: '#005c31'
  inverse-primary: '#006d3b'
  secondary: '#adc6ff'
  on-secondary: '#002e69'
  secondary-container: '#4b8eff'
  on-secondary-container: '#00285c'
  tertiary: '#ffd4d1'
  on-tertiary: '#68000c'
  tertiary-container: '#ffada8'
  on-tertiary-container: '#a3041a'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#66fea2'
  primary-fixed-dim: '#43e188'
  on-primary-fixed: '#00210e'
  on-primary-fixed-variant: '#00522b'
  secondary-fixed: '#d8e2ff'
  secondary-fixed-dim: '#adc6ff'
  on-secondary-fixed: '#001a41'
  on-secondary-fixed-variant: '#004493'
  tertiary-fixed: '#ffdad7'
  tertiary-fixed-dim: '#ffb3ae'
  on-tertiary-fixed: '#410004'
  on-tertiary-fixed-variant: '#930015'
  background: '#111316'
  on-background: '#e2e2e6'
  surface-variant: '#333538'
typography:
  headline-lg:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: -0.01em
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-mono:
    fontFamily: JetBrains Mono
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 16px
  code-sm:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 18px
  caption:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.05em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  unit: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  gutter: 12px
  sidebar_width: 240px
---

## Brand & Style

This design system is engineered for professional developers and QA engineers managing Android device clusters. The brand personality is technical, high-performance, and precise. It draws inspiration from modern IDEs and low-level system utilities, blending a **Corporate Modern** structure with **Glassmorphism** accents to signify sophisticated data processing.

The aesthetic prioritizes information density and operational status. The atmosphere is quiet and focused, utilizing a "Deep Sea" backdrop to reduce eye strain during long debugging sessions, while high-vibrancy accents draw immediate attention to hardware states and critical actions.

## Colors

The palette is rooted in a "Carbon" foundation.
- **Primary (Android Green):** Reserved exclusively for "Online" statuses, success confirmations, and the "Connect" primary action.
- **Secondary (Electric Blue):** Used for active interactive states, primary action buttons (Execute, Push, Pull), and focus rings.
- **Tertiary (Alert Red):** Used for "Offline" or "Unauthorized" device states, and destructive actions like "Wipe Data."
- **Neutrals:** The background uses a near-black "Deep Sea" (#0B0C0E), while surface containers use a slightly lighter "Carbon" (#1E2024) to create subtle depth.

## Typography

This design system utilizes a dual-font approach to distinguish between UI navigation and technical hardware data.
- **Inter** is the workhorse for the interface, providing high legibility for labels, headers, and descriptions.
- **JetBrains Mono** is used for all technical strings, including Serial Numbers, IP Addresses, ADB Shell commands, and logs.

Hierarchy is maintained through weight rather than size to keep the UI compact. Large headers are kept modest (24px) to maximize the available space for device grids.

## Layout & Spacing

The layout follows a **Fixed Sidebar / Fluid Content** model. The sidebar remains at 240px for navigation and global filters, while the main device management area uses a fluid grid to accommodate varying screen widths.

Spacing is based on a 4px baseline grid. To achieve a "compact yet breathable" feel, we use 12px gutters between cards and 16px internal padding for containers. This allows more devices to be visible on a single screen without the information feeling cramped.

## Elevation & Depth

Visual hierarchy is established through **Tonal Layers** supplemented by **Glassmorphism** for temporary overlays.

1.  **Level 0 (Deep Sea):** The main application canvas.
2.  **Level 1 (Carbon Surface):** Device cards and sidebar. These use a 1px subtle border (`border_subtle`) rather than shadows to maintain a clean, technical look.
3.  **Level 2 (Active States):** Hovered cards or active inputs use a slight blue-tinted inner glow.
4.  **Level 3 (Overlays):** Modals, dropdowns, and floating command palettes use a backdrop-filter blur (20px) with a 60% opaque surface color to create a glass effect. This ensures the user maintains visual context of the device list while performing a specific task.

## Shapes

The design system uses a **Soft** geometry (0.25rem / 4px). This creates a professional, tool-like appearance that feels modern but avoids the "toy-like" roundness of consumer social apps.

- **Standard Elements:** 4px (Buttons, Input fields, Checkboxes).
- **Cards & Modals:** 8px (Large containers).
- **Status Indicators:** Fully circular (Pill) for status dots to differentiate them from interactive elements.

## Components

### Device Cards
Each device is represented by a Level 1 card. The header includes the device model (Inter, Bold) and the Serial Number (JetBrains Mono, Subtle). A prominent status indicator dot (Green/Red/Gray) sits in the top-right corner.

### Action Buttons
- **Primary Action:** Solid Electric Blue with white text.
- **Secondary/Ghost:** Subtle white border with transparent background, turning semi-transparent white on hover.
- **ADB Commands:** Styled as "Command Chips" using monospaced text on a dark background.

### Drag-and-Drop Zones
Target zones for APK installations or file pushes use a dashed border (Android Green) with a 5% opacity green fill. Upon dragging over the zone, the border becomes solid and the fill increases to 10% opacity.

### Status Indicators
- **Online:** Android Green dot with a soft 4px outer glow.
- **Offline:** Alert Red dot, no glow.
- **Busy/Sideloading:** Animated pulsing blue border around the device card.

### Input Fields
Inputs are dark-themed with a 1px border. When focused, they transition to an Electric Blue border with a 0 0 0 2px glow.