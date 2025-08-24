variable "ami_id" {}
variable "instance_type" {}
variable "subnet_id" {}
variable "name" {}
variable "vpc_id" {}
variable "key_name" {
  description = "Name of the AWS key pair to use for SSH access"
  type        = string
}
