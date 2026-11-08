# LeafCare Phase 2 (Đăng nhập + Thanh toán) Implementation Plan

> Steps use checkbox (`- [ ]`) syntax for tracking. Same rules as Phase 1: one branch + PR per task (`feat/task-<N>-<short-name>`, N continues from Phase 1 at 6), `docs/PROGRESS.md` updated in the task's commit, TDD, UI tasks use the project UI skills (CLAUDE.md "UI work").

**Goal:** Google login with USER / ADMIN roles; Free / Premium plans; buy Premium through MoMo sandbox (create order, redirect, IPN, signature check); result page and transaction history; Free accounts limited to 5 scans per day.

**Architecture:** Same layers under `com.son.leafcare`. Spring Security (`spring-boot-starter-security` + `spring-boot-starter-oauth2-client`), one `SecurityFilterChain` in `config/SecurityConfig`. MoMo is called from a small `MomoClient` with Spring's `RestClient` (no SDK). Payment state lives only in the DB; the browser redirect never changes it by itself.

**Tech Stack:** Phase 1 stack + Spring Security 7, OAuth2 Client, `spring-security-test` (tests), `thymeleaf-extras-springsecurity6` (`sec:` in templates).

## Global Constraints

- **Secrets only from env vars**, never in git: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY`, `LEAFCARE_ADMIN_EMAILS` (comma list). `application.properties` reads them with `${...}`; test properties use dummy values.
- Schema changes = new Flyway files (`V2__users.sql`, `V3__subscription_payment.sql`, `V5__scan_owner.sql`). Never edit `V1__init.sql`.
- Roles: `USER`, `ADMIN`. A user is ADMIN when their Google email is in `leafcare.admin-emails` (checked at every login, so editing the env var + re-login changes it).
- Access rules:
  - Public: `/login`, `/css/**`, `/error/**`, `POST /payments/momo/ipn` (CSRF ignored for this path only; MoMo calls it server-to-server). `/` and `/uploads/**` need login (the dashboard shows scan photos).
  - Logged in: everything else. Plants/diseases list = any user.
  - ADMIN only: `/plants/new`, `/plants/*/edit`, `POST /plants/**`, same for `/diseases`.
- Plans: `FREE` (5 scans / day, default), `PREMIUM` (unlimited). Price and length from `leafcare.premium.price=49000` (VND) and `leafcare.premium.days=30`. Buying again while Premium is active extends from the current end date.
- MoMo sandbox, API v2 `captureWallet`: endpoint `https://test-payment.momo.vn/v2/gateway/api/create` and `/query`, signature HMAC-SHA256 (hex, lowercase) of the raw string with keys in alphabetical order, as in MoMo docs.
- Payment status enum `PaymentStatus { PENDING, SUCCESS, FAILED }`. Only IPN or the server-side `/query` call change it; the result page never trusts URL params.
- Local dev: MoMo cannot reach `localhost` for IPN. The result page calls `/query` when the payment is still `PENDING`, so the flow works without ngrok. IPN is still implemented and tested (works when deployed or via ngrok).
- Day boundary for the Free limit: `Asia/Ho_Chi_Minh` (00:00 – 24:00 VN time).
- Money: `long` VND (no decimals), shown as `49.000 ₫`.

## Review Focus

- Not logged in → any page except `/login`, CSS and error pages redirects to `/login` (Task 6).
- USER calling `POST /plants` or opening `/plants/new` → 403 page, not a 500 (Task 6).
- First login creates the user; second login updates name/avatar, does not duplicate (Task 6).
- IPN with wrong signature → 400, payment unchanged (Task 8).
- IPN for an unknown `orderId`, or `amount` different from the stored amount → payment unchanged (Task 8).
- Same IPN delivered twice → Premium extended only once (idempotent: only `PENDING` changes) (Task 8).
- `resultCode != 0` → `FAILED`, no subscription change (Task 8).
- Result page with forged `?resultCode=0` while DB says `PENDING` and `/query` says failed → shows failed/pending, never success (Task 8).
- User A opening user B's payment result or history → 404 (Tasks 8, 9).
- 6th scan of the day for Free → friendly error, no file written; Premium / ADMIN → no limit; scans from yesterday don't count (Task 10).
- A user only sees and deletes their own scans; ADMIN sees all (Task 10).

---

### Task 6: Spring Security + Google login, roles

**Branch:** `feat/task-6-google-login`

**Files:**
- Modify: `pom.xml` (`spring-boot-starter-security`, `spring-boot-starter-oauth2-client`, `org.thymeleaf.extras:thymeleaf-extras-springsecurity6`, test `spring-security-test`)
- Modify: both `application.properties` (Google registration from env; `leafcare.admin-emails`)
- Create: `db/migration/V2__users.sql` (`app_user`: id, email unique, name, avatar_url, role, created_at)
- Create: `entity/AppUser.java`, `entity/Role.java`, `repository/AppUserRepository.java`
- Create: `service/AppUserService.java` (`upsertFromGoogle(email, name, picture)` → sets role from admin list; `current(Authentication)`)
- Create: `config/SecurityConfig.java` (`SecurityFilterChain`, custom `OidcUserService` that calls `upsertFromGoogle` and adds `ROLE_ADMIN`/`ROLE_USER`), `templates/login.html` ("Đăng nhập bằng Google"), `templates/error/403.html`
- Modify: `layout/base.html` (avatar + name + "Đăng xuất" (POST form with CSRF) when logged in; "Đăng nhập" otherwise; hide "Thêm" / "Sửa" / "Xoá" buttons for non-admin with `sec:authorize`)
- Modify: every existing `@WebMvcTest` (add `@Import(SecurityConfig.class)` if needed and `.with(oidcLogin())` / `.with(csrf())`)
- Test: `AppUserServiceTest` (`@DataJpaTest`), `SecurityConfigTest` (anonymous redirect, USER 403 on admin routes, ADMIN allowed, public routes 200)

- [ ] Step 1: failing tests above.
- [ ] Step 2: implement; update existing controller tests so the suite is green again.
- [ ] Step 3: UI for login page + navbar user menu (impeccable, emil-design-eng, break-ui with a very long name and no avatar).
- [ ] Step 4: `./mvnw verify` green; manual check needs Shin's Google OAuth client (guide in `README.md` "Cấu hình Google OAuth").
- [ ] Step 5: PROGRESS.md, commit, push, PR.

### Task 7: Subscription, Payment entities + trang chọn gói

**Branch:** `feat/task-7-plans`

**Files:**
- Create: `db/migration/V3__subscription_payment.sql`
  - `subscription`: id, user_id FK unique, plan (`FREE`/`PREMIUM`), premium_until TIMESTAMP(6) null
  - `payment`: id, user_id FK, order_id VARCHAR(64) unique, request_id, plan, amount BIGINT, status, momo_trans_id BIGINT null, result_code INT null, message VARCHAR(255), created_at, paid_at null
- Create: `entity/Plan.java`, `entity/Subscription.java`, `entity/Payment.java`, `entity/PaymentStatus.java`, repositories
- Create: `service/SubscriptionService.java`: `isPremium(user)` (premium_until > now), `activatePremium(user, days)` (extend from max(now, premium_until))
- Create: `controller/PlanController.java` `GET /plans` → `plans/index.html`: Free vs Premium cards (benefits, price, "Gói đang dùng", Premium end date, button "Nâng cấp Premium" → `/payments/checkout`)
- Modify: navbar link "Gói"
- Test: `SubscriptionServiceTest` (`@DataJpaTest`: new user is Free, activate, extend while active, expired → Free), `PlanControllerTest`

### Task 8: MoMo sandbox: tạo đơn, redirect, IPN, kiểm chữ ký, trang kết quả

**Branch:** `feat/task-8-momo`

**Files:**
- Modify: both `application.properties` (`momo.endpoint`, `momo.partner-code`, `momo.access-key`, `momo.secret-key` from env, `momo.redirect-url`, `momo.ipn-url`, default `http://localhost:8080/payments/momo/...`)
- Create: `config/MomoProperties.java` (`@ConfigurationProperties("momo")` record)
- Create: `service/momo/MomoSigner.java` (`sign(raw)` HMAC-SHA256 hex; `createRaw(...)`, `ipnRaw(...)`, `queryRaw(...)` building the exact strings from MoMo docs)
- Create: `service/momo/MomoClient.java` (`create(orderId, requestId, amount, orderInfo)` → `payUrl`; `query(orderId, requestId)` → resultCode; verifies response signature)
- Create: `service/PaymentService.java`:
  - `startPremium(user)` → new `Payment` PENDING (orderId `LC` + time + random), calls MoMo create, returns `payUrl`
  - `handleIpn(Map params)` → verify signature, find by orderId, check amount, if PENDING: resultCode 0 → SUCCESS + `activatePremium`, else FAILED; idempotent
  - `refresh(orderId, user)` → if PENDING, ask `/query` and apply the same update
  - `getForUser(orderId, user)` → 404 for other users
- Create: `controller/PaymentController.java`
  - `GET /payments/checkout` → `payments/checkout.html` (gói, số tiền, nút "Thanh toán bằng MoMo")
  - `POST /payments/momo` → redirect to `payUrl` (error flash if MoMo fails)
  - `GET /payments/momo/return?orderId=...` → `refresh`, then redirect `/payments/{orderId}`
  - `POST /payments/momo/ipn` (JSON body) → `handleIpn`, 204 / 400
  - `GET /payments/{orderId}` → `payments/result.html` (thành công / thất bại / đang xử lý + nút "Kiểm tra lại")
- Test: `MomoSignerTest` (exact raw string order + known HMAC), `PaymentServiceTest` (DB + mocked `MomoClient`: every IPN case in Review Focus), `PaymentControllerTest`, `MomoClientTest` (`MockRestServiceServer`)

### Task 9: Lịch sử giao dịch

**Branch:** `feat/task-9-payment-history`

**Files:**
- Modify: `PaymentRepository` (`findByUserOrderByCreatedAtDesc(user, Pageable)`), `PaymentService.history(user, page)` (clamp page like Phase 1)
- Create: `GET /payments` → `payments/history.html`: mã đơn, gói, số tiền, trạng thái (badge), thời gian, link to result page; empty state; Phase 1 pager
- Modify: navbar / plans page link "Lịch sử giao dịch"
- Test: repository test (only own rows, newest first), controller test (empty, paged)

### Task 10: Quét lá theo người dùng + giới hạn Free 5 lượt/ngày

**Branch:** `feat/task-10-free-limit`

**Files:**
- Create: `db/migration/V5__scan_owner.sql` (`scan_history.user_id` BIGINT null FK → `app_user`; old rows stay null = only ADMIN sees them)
- Modify: `ScanHistory` (`@ManyToOne owner`), `ScanHistoryRepository` (`countByOwnerAndCreatedAtGreaterThanEqual`, `findByOwner(..., Pageable)`)
- Modify: `ScanHistoryService` (`create(file, note, user)` checks limit **before** storing the file → `ScanLimitException`; `list/get/delete` scoped to owner unless ADMIN)
- Create: `exception/ScanLimitException.java`
- Modify: `ScanController` (message "Tài khoản Free được quét 5 lượt/ngày. Nâng cấp Premium để quét không giới hạn." + link `/plans`), `scans/form.html` shows "Còn X/5 lượt hôm nay" for Free
- Modify: `DashboardService` (stats for the current user; ADMIN = all)
- Test: service DB tests (5 ok, 6th blocked, yesterday not counted, Premium/ADMIN unlimited, other user's scan → 404), controller test

## Manual setup Shin does (guided in the thread)

1. Google Cloud Console → OAuth client (Web), redirect URI `http://localhost:8080/login/oauth2/code/google` → put ID/secret in IntelliJ Run Configuration env vars (needed from Task 6 manual check).
2. MoMo sandbox: test credentials from MoMo developer docs (or a business sandbox account) → env vars (needed from Task 8 manual check). Pay with the MoMo test app / test card on the sandbox page.
