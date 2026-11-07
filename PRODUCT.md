# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Home gardeners: hobbyists with houseplants or a small garden. They hold a plant with a sick-looking leaf, often on a phone, and want to know what is wrong and how to treat it. Phase 1 is also demoed to university evaluators (FPT final project, due 2026-11-29).

## Product Purpose

LeafCare lets a gardener upload a leaf photo, receive a disease identification with treatment advice, and keep a history of past scans. It also keeps a catalogue of plants and diseases (symptoms, treatment). Success: a gardener goes from photo to a clear next step for their plant without needing expert knowledge.

## Positioning

Photo to diagnosis to care in one flow, with a persistent scan history. A plain plant database or care-reminder app does not do the photo-to-diagnosis step.

## Operating Context

Server-rendered Thymeleaf pages (layout via `thymeleaf-layout-dialect`, base template `layout/base.html`), Tailwind v4 + DaisyUI built with `npm run build` into `static/css/output.css`. Uploaded images live on disk in `uploads/`, served at `/uploads/**`. Used on phones (photographing leaves) and desktop.

## Capabilities and Constraints

- Phase 1 (current): Plant and Disease CRUD with name search and pagination (10 per page), leaf photo upload with note stored as a scan with status "Chờ phân tích", scan list/detail/delete, dashboard with totals and recent scans.
- Not yet: login/roles (phase 2), disease recognition, chatbot, RAG (phase 3). Phase 1 must not show fake diagnosis results.
- UI copy is Vietnamese (`lang="vi"`).
- Upload limits: jpg/png/webp, max 5 MB.
- No SPA or front-end framework; Thymeleaf only.
- Spec: `docs/superpowers/specs/2026-11-07-phase1-assignment-design.md`; plan: `docs/superpowers/plans/2026-11-07-phase1-assignment.md`.

## Brand Commitments

Name "LeafCare". Existing landing copy: "Chăm sóc cây trồng của bạn" / "LeafCare giúp bạn theo dõi và chăm sóc cây mỗi ngày." (placeholder, not confirmed as final). No logo or brand voice defined yet.

## Evidence on Hand

No real plant, disease, or scan data, no testimonials, no photography. Do not fabricate any; sample data is deferred to week 9.

## Product Principles

- Never present a diagnosis the system did not make; pending scans say they are pending.
- Photo upload is the primary action and must be easy one-handed on a phone.
- Treatment advice should be plain-language, written for non-experts.
- Keep the structure easy to split among team members (one controller/service/repository/template set per entity).

## Accessibility & Inclusion

No product-specific standard established. Mobile-friendly layout is required.
