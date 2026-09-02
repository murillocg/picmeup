# Cognito Authentication Migration

## Context

The platform authenticates a single hardcoded admin against an in-memory user, using a session login (`SecurityConfig` + `AuthController` + `AdminLoginPage`). That supports exactly one operator and gives photographers/contractors no way to log in and upload to their assigned events.

Migrating to Amazon Cognito gives us Google sign-in (no passwords to invent or reset), multi-user support, and role-based access for ADMIN and PHOTOGRAPHER.

## Decisions

- **Login UI**: Cognito Hosted UI (redirect-based). The current custom login form is removed.
- **Identity provider**: Google only. Apple is deliberately out of scope — it requires a paid Apple Developer account.
- **Account creation**: invite-only. Nobody self-registers.
- **Roles**: authoritative in **our database**, not in Cognito groups. The JWT proves *who* someone is; the `users` table decides *what they may do*.
- **Auth protocol**: OAuth2 Authorization Code with PKCE, JWT Bearer tokens.
- **Photographer access**: upload only to assigned events; no access to orders, stats, or admin features. Explicitly *not* in scope: managing their own photos, or any photographer-facing stats view.
- **Attribution is provenance, not money.** Photographers are not paid or credited through the platform, so per-photo accuracy carries no financial weight and no historical backfill is needed.
- **Email is out of scope.** SES production access stays denied and no email is sent by this work — invitations are delivered out of band (see Constraint 2).

## Constraints discovered before planning

Four facts that shape the design. Ignoring any of them produces a plan that fails in production:

1. **`allow_admin_create_user_only` does not apply to federated sign-ins.** It governs the native username/password signup API only. With Google attached to the Hosted UI, any Google account can complete federation and Cognito will auto-create a user in the pool. Invite-only is therefore enforced **in the application**: no `users` row, no access. An uninvited person can obtain a token and do precisely nothing with it. If pool hygiene later matters, add a Pre Sign-up Lambda trigger (it does fire for federated sign-ups) to reject at the door.

2. **We cannot email invitations.** SES is in the sandbox and the production-access request was **denied** (case 177980975400037), so any unverified recipient is rejected. Invitations are therefore delivered out of band — the admin tells the person "go to elitesportphotos.com and sign in with Google". If a password-based invite is added later, Cognito's own email service sends it (separate from our SES, ~50/day), which sidesteps the sandbox entirely.

3. **Callback URLs must use the `www` host.** `https://elitesportphotos.com/...` returns a bare 404 on every path but `/` — the apex is on GoDaddy domain forwarding, not CloudFront. Cognito matches callback URLs as exact strings, and a mismatch fails with an opaque error that is unpleasant to debug. Derive them from `SITE_URL` in `frontend/src/config.ts`, which already points at the `www` host.

4. **Email is the join key, not `cognito_sub`.** If someone signs in with Google and later gets a native password account at the same address, Cognito issues two users with two different `sub` values. Keying `users` on the verified email means both resolve to one application user. Google always supplies a verified email; Cognito verifies native signups by emailed code. `cognito_sub` is recorded for reference only.

## Phase 1: Infrastructure — Cognito via OpenTofu

Purely additive. No app changes.

**Prerequisite — done (2026-09-01).** The Google Cloud OAuth client exists, is published **In production** (its scopes are `openid`/`email`/`profile`, all non-sensitive, so no Google verification was needed) and has `https://elitesportphotos.auth.ap-southeast-2.amazoncognito.com/oauth2/idpresponse` registered as its authorised redirect URI. Both values are in the gitignored `terraform-ec2/terraform.tfvars` and declared in `variables.tf`. The `elitesportphotos` domain prefix was confirmed free in `ap-southeast-2`, and the account had no pre-existing user pools.

**New file: `terraform-ec2/cognito.tf`**
- `aws_cognito_user_pool` `elitesportphotos` — email sign-in, `allow_admin_create_user_only = true`, password policy
- `aws_cognito_identity_provider` `Google` — client ID/secret from a Google Cloud OAuth client (free), attribute mapping `email → email`, scopes `openid email profile`
- `aws_cognito_user_pool_domain` — prefix domain `elitesportphotos`
- `aws_cognito_user_pool_client` `elitesportphotos-web` — Authorization Code + PKCE, **no client secret**, `supported_identity_providers = ["Google"]`, callback/logout URLs for prod and `http://localhost:5173`
- Optional Hosted UI customisation with brand colours and logo

**Modify: `terraform-ec2/variables.tf`** — add `google_oauth_client_id`, `google_oauth_client_secret` (sensitive)
**Modify: `terraform-ec2/outputs.tf`** — pool ID, client ID, Hosted UI domain, issuer URI

No IAM changes: the app never calls the Cognito Admin API in this design.

**Verify**: `tofu plan && tofu apply`; open the Hosted UI URL and confirm the Google button appears and a sign-in completes (it will succeed at Cognito and be rejected by our app later — that is correct).

## Phase 2: Database — Users and Assignments

**`V16__create_users_table.sql`**
```sql
CREATE TABLE users (
    id           UUID PRIMARY KEY,
    email        VARCHAR(255) NOT NULL UNIQUE,
    cognito_sub  VARCHAR(255) UNIQUE,
    name         VARCHAR(255),
    role         VARCHAR(32)  NOT NULL,
    status       VARCHAR(32)  NOT NULL DEFAULT 'INVITED',
    invited_by   UUID REFERENCES users (id),
    created_at   TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    last_login_at TIMESTAMP
);

CREATE INDEX idx_users_email ON users (email);
```
`role` is `ADMIN` or `PHOTOGRAPHER` — chosen by the admin at invite time, so there is no pending/unapproved state. `status` is `INVITED`, `ACTIVE`, or `DISABLED`.

**`V17__create_event_photographers_table.sql`**
```sql
CREATE TABLE event_photographers (
    event_id    UUID NOT NULL REFERENCES events (id),
    user_id     UUID NOT NULL REFERENCES users (id),
    assigned_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    PRIMARY KEY (event_id, user_id)
);
```

The existing `photographers` table stays as-is; it records photo authorship and is populated by `PhotoService` (`confirmUpload` currently falls back to a placeholder "Admin" photographer). Phase 4 links uploads to the authenticated user instead.

**Verify**: app starts, Flyway applies both migrations, existing tests pass.

## Phase 3: Backend — JWT alongside the session login

Both auth mechanisms run at once so the current admin login keeps working while Cognito is tested. This is the rollback point.

**`pom.xml`** — add `spring-boot-starter-oauth2-resource-server`

**`application.yml`** — `spring.security.oauth2.resourceserver.jwt.issuer-uri` under the prod profile, plus `app.admin.bootstrap-emails` (comma-separated). Leave the issuer blank in the `test` profile so JWT config stays inert in tests.

**New: `AppUser.java` + `AppUserRepository.java`** (`com.picmeup.common.user`) — entity for `users`, with `findByEmail`.

**New: `DatabaseRoleJwtAuthenticationConverter.java`** — given a validated JWT:
1. read the verified `email` claim
2. look up the `users` row; if absent or `status != 'ACTIVE'`/`'INVITED'`, grant **no** authorities
3. on first login for an `INVITED` row: set `ACTIVE`, store `cognito_sub`, stamp `last_login_at`
4. bootstrap: if the email is in `app.admin.bootstrap-emails` and has no row, create an `ACTIVE` `ADMIN` row — otherwise nobody can issue the first invite
5. grant `ROLE_ADMIN` / `ROLE_PHOTOGRAPHER` from `role`

**`SecurityConfig.java`** — add `.oauth2ResourceServer(jwt -> jwt.jwtAuthenticationConverter(...))` alongside the existing session config, and tighten the rules by role:
- `ROLE_ADMIN` — everything currently marked `authenticated()`
- `ROLE_PHOTOGRAPHER` — `POST /api/events/*/photos/**` and `GET /api/photographer/**`

**`AuthController.java`** — `/check` returns `{ authenticated, email, role }` for both a session Principal (always ADMIN) and a JWT principal.

**Verify**: the existing admin login still works; a Google sign-in by a non-invited address authenticates at Cognito and receives 403 from every protected endpoint.

## Phase 4: Backend — Invites, Assignments, Photographer Uploads

**New package: `com.picmeup.admin`**

**`UserManagementService.java`** — pure database work, no AWS calls:
- `invite(email, name, role)` — creates an `INVITED` row
- `listUsers()`, `setRole(userId, role)`, `setStatus(userId, status)`
- `assignToEvent(userId, eventId)` / `unassignFromEvent(userId, eventId)`

**`AdminUserController.java`** — `/api/admin/users`: `POST /` invite, `GET /` list, `PATCH /{id}` role/status, `POST /{id}/events/{eventId}` assign, `DELETE /{id}/events/{eventId}` unassign. ADMIN only.

**New: `EventPhotographer.java` + repository** — JPA entity for the join table.

**New: `PhotographerController.java`** — `GET /api/photographer/events` lists the caller's assigned events.

**`PhotoController.java` / `PhotoService.java`** — resolve the uploader from the authenticated principal. For `ROLE_PHOTOGRAPHER`, reject uploads to unassigned events with 403. Replace the placeholder-"Admin" fallback in `confirmUpload` with the resolved user.

That fallback is a latent bug worth understanding before touching it:

```java
// PhotoService.confirmUpload
var photographer = photographerRepository.findAll().stream().findFirst()
        .orElseGet(() -> photographerRepository.save(new Photographer("Admin", "admin@...")));
```

It attributes every photo to whichever row is returned first, unordered. It returns the right answer today only by accident — the frontend uses presign/confirm exclusively, so the one path that records a real photographer (`uploadPhotos(slug, photographerEmail, photographerName, files)`) is never called, leaving a single `Admin` row for `findFirst` to pick. It becomes a genuine bug the moment a second photographer exists, which is exactly when this phase ships. Fixing it earlier would only attribute everything to the admin, which is what already happens.

**Verify**: admin invites an address, that person signs in with Google and lands as an active photographer, can upload to an assigned event, and gets 403 on an unassigned one.

## User management: where it lives and how an invite works

**`/admin/users`** — a new page in the existing admin nav beside Orders / Stats / Usage, behind `ProtectedRoute` (already built) plus an admin-role check. It owns the person's lifecycle: invite, change role, disable, re-enable. One table — email, name, role, status, last login, assigned event count — plus an invite form.

**Assignment lives on the event page, not here.** The mental model is staffing an event, and it is the high-frequency action: every new event needs photographers, while a person is invited once. `EventDetailPage`'s admin area gets an "Assigned photographers" control; `/admin/users` shows each person's assignments read-only. Building it the other way round means opening a person's record every time an event is created.

**Photographers see almost nothing** — `PhotographerEventsPage` lists their assigned events with upload links, and that is the entire application to them.

### The invite is an email address, not an email

Nothing is sent. SES production access is denied (Constraint 2), so the address itself is the invitation:

1. An admin enters email + name + role, creating an `INVITED` row.
2. The admin tells the person out of band — WhatsApp, phone — to sign in with Google.
3. On sign-in, `DatabaseRoleJwtAuthenticationConverter` matches the **verified** email claim to the row, flips it to `ACTIVE`, and stores `cognito_sub` and `last_login_at`.

No token, no link, no expiry, nothing that can leak, be forwarded, or need re-sending. The security property that makes it safe is Constraint 1: **no `users` row means no authorities**, so an uninvited person can hold a perfectly valid Cognito token and do nothing with it.

### The failure this design must handle

Someone signs in with a *different* Google account than the one invited — a personal address instead of a work one. They authenticate successfully and receive zero authority, landing on an otherwise blank "no access" screen.

**That screen must display the email they actually signed in with.** Support then becomes one message: "you used your personal address, the invite is on your work one." Without it the failure is unresolvable for both sides, and it will happen.

### Revoking access

Set `status = 'DISABLED'`; never delete the row. It takes effect on the next request with no Cognito call, and because attribution is provenance, deleting someone who has uploaded photos would orphan that history. The disabled person's token stays cryptographically valid and completely powerless.

## Phase 5: Frontend — Hosted UI + Role-Based UI

**New: `frontend/src/services/auth.ts`** — PKCE helpers: `generatePKCE()`, `buildAuthorizeUrl()`, `exchangeCodeForTokens()`, `refreshTokens()`, `parseJwt()`, `buildLogoutUrl()`

**`AuthContext.tsx`** — rewrite: tokens in memory, refresh on expiry, `login()` redirects to the Hosted UI, `logout()` hits the Cognito logout endpoint. Exposes `{ authenticated, user: { email, role }, loading, login, logout }`. The role comes from `/api/auth/check`, not from a token claim.

**New: `AuthCallbackPage.tsx`** — handles the redirect back, exchanges the code for tokens.

**`api.ts`** — Bearer token interceptor; drop `withCredentials`.

**`Layout.tsx`** — admin sees the full menu; photographer sees "My Events" and Logout.

**New: `AdminUsersPage.tsx`** — invite by email with a role, list users, change role/status, assign events.
**New: `PhotographerEventsPage.tsx`** — assigned events with upload links.

**Existing pages** — replace `authenticated` checks with `isAdmin` / `isPhotographer`.

**Delete: `AdminLoginPage.tsx`** — the Hosted UI replaces it.

**`vite.config.ts`** — add `__COGNITO_DOMAIN__`, `__COGNITO_CLIENT_ID__`, `__COGNITO_REDIRECT_URI__` defines; pass them as build args from `Dockerfile` and the deploy workflow.

**Verify**: full E2E — admin signs in with Google, invites a photographer, that person signs in and uploads to an assigned event, role-based nav is correct, logout returns to a signed-out state.

## Phase 6: Cleanup

Once Cognito is proven in production:

- `SecurityConfig.java` — remove the session login, `InMemoryUserDetailsManager`, `AuthenticationManager`, `SecurityContextRepository`, and the logout handler
- Remove **CSRF configuration and the session cookie settings** — they exist to protect the cookie session, and Bearer tokens are not attached automatically by the browser
- `AuthController.java` — delete the `/login` endpoint and `LoginRequest`
- `application.yml` — drop `app.admin.username` / `app.admin.password` and the `server.servlet.session` block
- Tests — replace the `user()` + `csrf()` post-processors with `jwt()`

## Key Files Summary

| New Files | Purpose |
|---|---|
| `terraform-ec2/cognito.tf` | User pool, Google IdP, Hosted UI client |
| `V16` / `V17` migrations | `users`, `event_photographers` |
| `common/user/AppUser.java` + repository | User entity keyed on verified email |
| `DatabaseRoleJwtAuthenticationConverter.java` | JWT → DB lookup → Spring authorities |
| `admin/UserManagementService.java` | Invite, role, status, assignment (no AWS calls) |
| `admin/AdminUserController.java` | `/api/admin/users` endpoints |
| `EventPhotographer.java` + repository | Assignment entity |
| `PhotographerController.java` | Photographer-scoped event listing |
| `frontend/src/services/auth.ts` | PKCE/token utilities |
| `AuthCallbackPage.tsx` | OAuth2 callback handler |
| `AdminUsersPage.tsx` | Invite and manage users |
| `PhotographerEventsPage.tsx` | Photographer's event list |

| Modified Files | Change |
|---|---|
| `SecurityConfig.java` | Session + JWT, then JWT-only |
| `AuthController.java` | Role-aware `/check`; `/login` removed in Phase 6 |
| `AuthContext.tsx` | Rewritten for OAuth2 PKCE |
| `api.ts` | Bearer interceptor, no cookies |
| `Layout.tsx` | Role-based navigation |
| `PhotoController.java` + `PhotoService.java` | Uploader identity from the JWT |
| `application.yml` | Issuer URI, bootstrap admin emails |
| `pom.xml` | OAuth2 resource server |
| Upload/admin pages | Role-based access checks |

| Deleted Files | Reason |
|---|---|
| `AdminLoginPage.tsx` | Replaced by the Hosted UI |

## Deferred

- **Customer-facing email.** Buyers receive no receipt or download link, and cannot until SES production access is granted (currently `DENIED`, case `177980975400037`, though `EnforcementStatus` is `HEALTHY` so this was a review decision rather than an abuse penalty). Before any resubmission, the account needs a configuration set with a bounce/complaint event destination — there is none today — and `EmailService` needs to stop swallowing send failures. A dedicated transactional provider is a reasonable alternative at this volume. None of this blocks the work in this plan.
- **Password-based invites.** If someone wants a password instead of Google: an admin action calling `AdminCreateUser`, with Cognito's own email service sending the temporary password. Additive — no schema or role changes — but it reintroduces the Cognito Admin SDK and its IAM permissions. Ship Google-only first.
- **Pre Sign-up Lambda** to reject uninvited federated users at Cognito rather than in the app. Only worth it if unauthorised records accumulating in the pool becomes a nuisance.

## Rollback

Through Phase 5 the session login still works: revert the frontend and the app authenticates admins exactly as it does today. After Phase 6 removes it, rollback means reverting that commit.
