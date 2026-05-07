terraform {
  backend "s3" {
    bucket         = "ghatana-terraform-state"
    key            = "data-cloud/staging/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "ghatana-terraform-locks"
    encrypt        = true
  }
}
