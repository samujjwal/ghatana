terraform {
  backend "s3" {
    bucket         = "ghatana-terraform-state"
    key            = "data-cloud/dr/terraform.tfstate"
    region         = "us-east-1"     # State bucket stays in primary region
    dynamodb_table = "ghatana-terraform-locks"
    encrypt        = true
  }
}
