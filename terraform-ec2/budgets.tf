########################################################################
# Billing alerts.
#
# Promotional credits have been covering the whole bill, so the console has
# read $0.00 for months. That makes the first real charge easy to miss —
# these exist so it is not.
#
# AWS provides the first two budgets at no cost.
########################################################################

locals {
  # The budget API wants a list; the variable is a comma-separated string shared
  # with the application's email configuration.
  billing_alert_emails = split(",", var.email_admin_recipients)
}

# Fires when money is actually leaving the account. Credits are counted here, so
# this stays silent until they are exhausted — which is precisely the event worth
# knowing about. The threshold is deliberately near zero rather than a round
# number: the signal is "we have started paying", not "we have spent a lot".
resource "aws_budgets_budget" "real_spend" {
  name         = "${var.app_name}-real-spend"
  budget_type  = "COST"
  limit_amount = "5"
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  cost_types {
    include_credit = true
    include_refund = false
    include_tax    = true
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = local.billing_alert_emails
  }

  # Catches a spike early rather than after the fact.
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = local.billing_alert_emails
  }
}

# Tracks consumption regardless of who is paying for it. Credits are excluded, so
# this reflects real usage and gives warning while there is still credit left to
# absorb it. Set above the observed ~$24/month baseline so ordinary months are
# quiet and a genuine increase is not.
resource "aws_budgets_budget" "usage_before_credits" {
  name         = "${var.app_name}-usage-before-credits"
  budget_type  = "COST"
  limit_amount = "45"
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  cost_types {
    include_credit = false
    include_refund = false
    include_tax    = true
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = local.billing_alert_emails
  }
}
