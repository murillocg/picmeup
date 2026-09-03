########################################################################
# Cognito — Google sign-in for admins and contract photographers.
#
# The backend drives the OAuth flow and holds the tokens; the browser only
# ever receives a session cookie. A sign-in that succeeds here still gets
# zero authority from the app until an invite exists.
########################################################################

resource "aws_cognito_user_pool" "main" {
  name = var.app_name

  # Email is the identity. It is also the join key the application uses to
  # match a token back to a `users` row, so it must be verified.
  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  # Deliberately open, because closing it would buy nothing. Google federation
  # already auto-creates a pool user for anyone who completes sign-in, so the
  # pool was never a gate. Email-OTP sign-in needs a native pool user to exist,
  # and letting Cognito create one on demand avoids the app having to call
  # AdminCreateUser — a new AWS dependency and IAM permission for no gain.
  #
  # Invite-only is enforced entirely in the application: no `users` row means no
  # authorities, and sign-in is refused. Anyone can authenticate against this
  # pool; nobody can do anything in the app without an invite.
  admin_create_user_config {
    allow_admin_create_user_only = false
  }

  # Sign in with a one-time code emailed by Cognito, so nobody needs a password
  # or a Google account.
  #
  # PASSWORD cannot be removed — Cognito rejects the update with "Password should
  # be configured as one of the allowed first auth factors". The app client
  # withholds ALLOW_USER_PASSWORD_AUTH instead, so no password flow is usable
  # against it even though the factor is listed here.
  #
  # Requires the Essentials tier, which is the default for new pools — this one
  # is already on it, so there is no tier change and no new cost.
  sign_in_policy {
    allowed_first_auth_factors = ["PASSWORD", "EMAIL_OTP"]
  }

  # Codes are sent by Cognito's own mailer (~50/day), which never touches our
  # SES — so the SES sandbox does not block sign-in. Worth revisiting once SES
  # production access is granted: a branded sender is far less likely to be
  # filtered as junk than no-reply@verificationemail.com.
  email_configuration {
    email_sending_account = "COGNITO_DEFAULT"
  }

  # Reachable now that native sign-in is open, though passwordless users never
  # set one. Kept strict for anyone who does.
  password_policy {
    minimum_length                   = 12
    require_lowercase                = true
    require_uppercase                = true
    require_numbers                  = true
    require_symbols                  = true
    temporary_password_validity_days = 7
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  tags = { Name = "${var.app_name}-users" }
}

resource "aws_cognito_identity_provider" "google" {
  user_pool_id  = aws_cognito_user_pool.main.id
  provider_name = "Google"
  provider_type = "Google"

  # The endpoint values below are filled in by Cognito when the provider is
  # created. They are declared here so the config matches what AWS reports —
  # otherwise every plan proposes deleting them, and applying that would strip
  # the endpoints Google federation runs on.
  provider_details = {
    client_id                     = var.google_oauth_client_id
    client_secret                 = var.google_oauth_client_secret
    authorize_scopes              = "openid email profile"
    attributes_url                = "https://people.googleapis.com/v1/people/me?personFields="
    attributes_url_add_attributes = "true"
    authorize_url                 = "https://accounts.google.com/o/oauth2/v2/auth"
    oidc_issuer                   = "https://accounts.google.com"
    token_request_method          = "POST"
    token_url                     = "https://www.googleapis.com/oauth2/v4/token"
  }

  # email_verified is carried across deliberately: the application trusts the
  # email claim to resolve a user, so it must know Google actually verified it.
  attribute_mapping = {
    username       = "sub"
    email          = "email"
    email_verified = "email_verified"
    name           = "name"
  }
}

resource "aws_cognito_user_pool_domain" "main" {
  domain       = var.app_name
  user_pool_id = aws_cognito_user_pool.main.id

  # 1 = classic hosted UI. Managed login (2) is what renders passwordless email-OTP
  # sign-in and drops the empty grey logo bar, but switching to it broke sign-in
  # entirely with "Login pages unavailable" — it needs a managed login branding
  # style, and creating the default style for the client was not enough on its own.
  # Reverted until that is worked out; see aws_cognito_managed_login_branding,
  # which this provider version (5.100.0) does not yet expose.
  managed_login_version = 2
}

resource "aws_cognito_user_pool_client" "web" {
  name         = "${var.app_name}-web"
  user_pool_id = aws_cognito_user_pool.main.id

  # Confidential client. The backend performs the code exchange, so the secret never
  # reaches a browser — and neither do the tokens. The session cookie is all the
  # frontend ever holds, which puts tokens out of reach of any script on the page.
  generate_secret = true

  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]
  # "COGNITO" is the pool's own directory — without it the login page offers only
  # the Google button and no way to sign in with an emailed code.
  supported_identity_providers = ["COGNITO", "Google"]

  # Cognito redirects to the backend, which completes the exchange. Kept under /api
  # so the Vite dev proxy — which forwards only that prefix — reaches it unchanged.
  # Both localhost ports are registered so it works whether a developer browses the
  # dev server or the backend directly.
  #
  # Must be the www host: the apex serves a bare 404 on every path but "/", and
  # Cognito matches these as exact strings.
  callback_urls = [
    "https://www.${var.domain_name}/api/auth/callback/cognito",
    "http://localhost:5173/api/auth/callback/cognito",
    "http://localhost:8080/api/auth/callback/cognito",
  ]

  logout_urls = [
    "https://www.${var.domain_name}/",
    "http://localhost:5173/",
    "http://localhost:8080/",
  ]

  # ALLOW_USER_AUTH is what enables choice-based sign-in, the flow email-OTP runs
  # through. Deliberately no ALLOW_USER_PASSWORD_AUTH: passwords are never sent
  # directly to this client.
  explicit_auth_flows = ["ALLOW_USER_AUTH", "ALLOW_REFRESH_TOKEN_AUTH"]

  # The backend discards the tokens once it has the verified email — authority comes
  # from the users table on every request, not from a token — so there is nothing to
  # keep alive for 30 days.
  refresh_token_validity = 1

  access_token_validity = 1
  id_token_validity     = 1

  token_validity_units {
    access_token  = "hours"
    id_token      = "hours"
    refresh_token = "days"
  }

  prevent_user_existence_errors = "ENABLED"

  # supported_identity_providers references "Google" by name, which Terraform
  # cannot see as a dependency on its own.
  depends_on = [aws_cognito_identity_provider.google]
}
