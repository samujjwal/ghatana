package dcmaar

# Example Rego policy for action authorization.
# Input shape (example):
# {
#   "subject": "alice",
#   "roles": ["admin"],
#   "command": "restart",
#   "resources": ["ops"]
# }

default allow = false

# Allow echo anywhere
allow {
  input.command == "echo"
}

# Deny all on resource "secret"
deny_secret {
  some r
  input.resources[r] == "secret"
}

allow {
  not deny_secret
  some r
  input.resources[r] == "ops"
  input.command == "restart"
  some i
  input.roles[i] == "admin"
}

