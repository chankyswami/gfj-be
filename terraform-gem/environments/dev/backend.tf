terraform {
  backend "s3" {
    bucket         = "gems-of-jaipur-bucket"
    key            = "dev/terraform.tfstate"
    region         = "ap-south-1"
    encrypt        = true
  }
}