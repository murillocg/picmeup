########################################################################
# Cognito — Google sign-in for admins and contract photographers.
#
# Purely additive: nothing here is wired into the application until the
# backend adds the JWT resource server. A Google sign-in that succeeds
# here still gets zero authority from the app until an invite exists.
########################################################################

resource "aws_cognito_user_pool" "main" {
  name = var.app_name

  # Email is the identity. It is also the join key the application uses to
  # match a token back to a `users` row, so it must be verified.
  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  # Blocks self-service signup through the native API. It does NOT block
  # Google federation — Cognito auto-creates a pool user for any Google
  # account that completes the flow. Invite-only is enforced in the
  # application: no `users` row, no authorities.
  admin_create_user_config {
    allow_admin_create_user_only = true
  }

  # Only reachable via the native API, which is closed above. Kept strict so
  # the pool stays sane if a password flow is ever added.
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

  provider_details = {
    client_id        = var.google_oauth_client_id
    client_secret    = var.google_oauth_client_secret
    authorize_scopes = "openid email profile"
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
}

resource "aws_cognito_user_pool_client" "web" {
  name         = "${var.app_name}-web"
  user_pool_id = aws_cognito_user_pool.main.id

  # Public client: a browser cannot keep a secret, so PKCE protects the code
  # exchange instead.
  generate_secret = false

  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]
  supported_identity_providers         = ["Google"]

  # Must be the www host. The apex serves a bare 404 on every path but "/",
  # and Cognito matches these as exact strings.
  callback_urls = [
    "https://www.${var.domain_name}/auth/callback",
    "http://localhost:5173/auth/callback",
  ]

  logout_urls = [
    "https://www.${var.domain_name}/",
    "http://localhost:5173/",
  ]

  # Hosted UI only — no direct username/password auth against this client.
  explicit_auth_flows = ["ALLOW_REFRESH_TOKEN_AUTH"]

  access_token_validity  = 1
  id_token_validity      = 1
  refresh_token_validity = 30

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
