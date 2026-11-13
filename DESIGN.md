<!-- SEED: established with the user before implementation; re-run /impeccable document once there's code to capture the actual tokens and components. -->
---
name: LeafCare
description: A small plant clinic on your phone: enamel-white surfaces, a committed clinic-green shell, every control labeled.
colors:
  clinic-green: "#0B7A4B"
  clinic-green-deep: "#075C38"
  enamel-white: "#F4F8F5"
  surface: "#FFFFFF"
  ink: "#14261C"
  ink-muted: "#4A5E52"
  hairline: "#D3E2D9"
  alert-red: "#C8323A"
typography:
  body:
    fontFamily: "Be Vietnam Pro, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.5
  title:
    fontFamily: "Be Vietnam Pro, system-ui, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 700
    lineHeight: 1.25
  label:
    fontFamily: "Be Vietnam Pro, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 600
    lineHeight: 1.25
rounded:
  sm: "8px"
  md: "12px"
  pill: "9999px"
spacing:
  sm: "8px"
  md: "16px"
  lg: "24px"
components:
  button-primary:
    backgroundColor: "{colors.clinic-green}"
    textColor: "{colors.surface}"
    rounded: "{rounded.pill}"
    height: "48px"
    padding: "0 24px"
  button-scan:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.clinic-green-deep}"
    rounded: "{rounded.pill}"
    height: "48px"
    padding: "0 20px"
---

# Design System: LeafCare

## Overview

**Creative North Star: "The Plant Clinic"**

LeafCare behaves like a small, careful clinic for plants: clean enamel-white surfaces, one committed green shell, and every control carrying a plain Vietnamese label. It is an Operate surface used by home gardeners, often one-handed on a phone in daylight, so it is light-themed, high-contrast, and quiet. The world lends only type, palette, density, and one signature move; navigation and controls stay standard web patterns.

The signature move is the **leaf mark**: a white cross on a green rounded plate whose top arm is a leaf. It is LeafCare's own symbol, not a pharmacy cross. It appears as the logo, the active-tab marker, and the empty-state emblem, nowhere else.

**Key Characteristics:**
- Committed clinic-green shell (top bar, bottom bar), enamel-white working area.
- Flat surfaces, hairline green-tinted borders; soft shadows only on floating bars.
- Primary action "Quét lá" is always reachable: top bar on desktop, raised center button in the thumb zone on phones.
- State is never color alone: every status has a text label.

## Colors

A green shell on a green-tinted white ground; red is rationed to errors and destructive actions.

### Primary
- **Clinic Green** (#0B7A4B): top bar, bottom bar, primary buttons, focus ring, active markers.
- **Deep Clinic Green** (#075C38): hover/pressed on green, text on white-on-green contrast needs.

### Neutral
- **Enamel White** (#F4F8F5): page ground.
- **Surface White** (#FFFFFF): tables, forms, the "Quét lá" button on the green bar.
- **Leaf Ink** (#14261C): body text. **Muted Ink** (#4A5E52): secondary text (≥4.5:1 on Enamel White).
- **Hairline** (#D3E2D9): borders and dividers.

### Semantic
- **Alert Red** (#C8323A): errors, delete confirmations only.

### Named Rules
**The Labeled-State Rule.** A status or error is never shown by color alone; it always has words ("Chờ phân tích", "Tên đã tồn tại").
**The Rationed Red Rule.** Red appears only for errors and destructive actions.

## Typography

**Body/Display Font:** Be Vietnam Pro (fallback system-ui). Designed for Vietnamese diacritics; one family, weight does the ranking.

**Character:** Plain, friendly, legible at arm's length.

### Hierarchy
- **Title** (700, 1.5rem, 1.25): page headings.
- **Label** (600, 0.875rem): buttons, tabs, table headers, badges.
- **Body** (400, 1rem, 1.5): text and table cells, 65–75ch measure for prose.

### Named Rules
**The One Family Rule.** No second typeface; hierarchy comes from weight and size.

## Layout

Single-column working area, max width 72rem, 16px gutters on phones, 24px from `md`. Spacing rhythm 8/16/24; more space above a heading than below. Desktop (≥1024px, `lg`): sticky top bar with one-line nav links (Trang chủ, Hỏi đáp, Lịch sử, Cây, Bệnh, Gói), the scan button and the account menu. Phone and tablet (<1024px): slim top bar with logo and account menu; fixed bottom bar with Trang chủ, Hỏi đáp, Lịch sử, Bệnh tabs and a raised "Quét lá" button in the center; the account menu (avatar, `<details>` dropdown) holds Cây, Gói, Lịch sử giao dịch, Trang quản trị, Đăng xuất; page content gets bottom padding so nothing hides behind it. Tap targets ≥48px.

## Elevation & Depth

Flat at rest. Depth is tonal (white surface on enamel ground, hairline borders). Only floating bars carry a soft shadow (`0 -2px 12px rgba(20,38,28,0.12)` for the bottom bar).

## Shapes

12px radius on surfaces and inputs, pill radius on buttons, 12px radius on the logo plate. No nested cards; no colored side borders.

## Components

### Buttons
- **Primary:** Clinic Green fill, white text, pill, 48px high. Hover Deep Clinic Green. Focus: 2px white-on-green or green-on-white ring, offset 2px.
- **Scan ("Quét lá"):** white pill with Deep Green text on the green top bar; on phones, a larger raised green pill with white ring in the bottom bar center. Leaf icon + label.

### Navigation
Top bar on desktop; bottom tab bar on phones. Active item: bold label plus a small leaf-mark notch; never color alone.

### Pagination
DaisyUI `join` of 48px-tap buttons: Trước, page numbers around current, Sau. Current page filled Clinic Green. Preserves `q`.

### Alerts (flash)
Surface-white strip with 1px Hairline border, a leading icon, and text; success uses green icon, error uses Alert Red icon and text.

## Do's and Don'ts

### Do:
- **Do** keep "Quét lá" one tap away on every page.
- **Do** write all copy in Vietnamese with plain verbs ("Thêm cây", "Xoá").
- **Do** respect `prefers-reduced-motion`; motion is short transitions only.

### Don't:
- **Don't** reuse the pharmacy cross as is; the top arm is always the leaf.
- **Don't** show red for anything but errors and deletes.
- **Don't** use a second typeface, gradients, or glass effects.
- **Don't** show a diagnosis the system did not make; pending scans say they are pending.
